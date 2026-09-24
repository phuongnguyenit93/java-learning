interface ZipEntry {
  name: string;
  compressionMethod: number;
  generalPurposeFlags: number;
  compressedSize: number;
  localHeaderOffset: number;
}

const LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
const CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
const END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50;

function findEndOfCentralDirectory(bytes: Uint8Array): number {
  const minOffset = Math.max(0, bytes.length - 65_557);

  for (let offset = bytes.length - 22; offset >= minOffset; offset -= 1) {
    if (
      bytes[offset] === 0x50
      && bytes[offset + 1] === 0x4b
      && bytes[offset + 2] === 0x05
      && bytes[offset + 3] === 0x06
    ) {
      return offset;
    }
  }

  throw new Error('GitHub artifact is not a supported ZIP archive.');
}

function readEntries(bytes: Uint8Array): ZipEntry[] {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const eocdOffset = findEndOfCentralDirectory(bytes);

  if (view.getUint32(eocdOffset, true) !== END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
    throw new Error('Invalid ZIP end-of-central-directory record.');
  }

  const entryCount = view.getUint16(eocdOffset + 10, true);
  const centralDirectoryOffset = view.getUint32(eocdOffset + 16, true);
  const decoder = new TextDecoder();
  const entries: ZipEntry[] = [];
  let offset = centralDirectoryOffset;

  for (let index = 0; index < entryCount; index += 1) {
    if (view.getUint32(offset, true) !== CENTRAL_DIRECTORY_SIGNATURE) {
      throw new Error('Invalid ZIP central directory entry.');
    }

    const generalPurposeFlags = view.getUint16(offset + 8, true);
    const compressionMethod = view.getUint16(offset + 10, true);
    const compressedSize = view.getUint32(offset + 20, true);
    const fileNameLength = view.getUint16(offset + 28, true);
    const extraLength = view.getUint16(offset + 30, true);
    const commentLength = view.getUint16(offset + 32, true);
    const localHeaderOffset = view.getUint32(offset + 42, true);
    const fileNameStart = offset + 46;
    const name = decoder.decode(bytes.subarray(fileNameStart, fileNameStart + fileNameLength));

    entries.push({
      name,
      compressionMethod,
      generalPurposeFlags,
      compressedSize,
      localHeaderOffset,
    });

    offset = fileNameStart + fileNameLength + extraLength + commentLength;
  }

  return entries;
}

export function extractJarStream(zipBytes: Uint8Array, expectedFileName: string): ReadableStream<Uint8Array> {
  const entries = readEntries(zipBytes);
  const entry = entries.find((candidate) => candidate.name === expectedFileName)
    ?? entries.find((candidate) => candidate.name.endsWith(`/${expectedFileName}`));

  if (!entry) {
    throw new Error(`Artifact ZIP does not contain ${expectedFileName}.`);
  }

  if ((entry.generalPurposeFlags & 0x1) !== 0) {
    throw new Error('Encrypted ZIP entries are not supported.');
  }

  const view = new DataView(zipBytes.buffer, zipBytes.byteOffset, zipBytes.byteLength);
  if (view.getUint32(entry.localHeaderOffset, true) !== LOCAL_FILE_HEADER_SIGNATURE) {
    throw new Error('Invalid ZIP local file header.');
  }

  const fileNameLength = view.getUint16(entry.localHeaderOffset + 26, true);
  const extraLength = view.getUint16(entry.localHeaderOffset + 28, true);
  const dataStart = entry.localHeaderOffset + 30 + fileNameLength + extraLength;
  const dataEnd = dataStart + entry.compressedSize;
  const compressed = zipBytes.subarray(dataStart, dataEnd);
  const source = new Blob([compressed]).stream();

  if (entry.compressionMethod === 0) {
    return source;
  }

  if (entry.compressionMethod === 8) {
    return source.pipeThrough(new DecompressionStream('deflate-raw'));
  }

  throw new Error(`Unsupported ZIP compression method: ${entry.compressionMethod}.`);
}
