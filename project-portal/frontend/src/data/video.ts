import type { VideoIndex, VideoScriptDocument } from '../types/learning';

const videoIndexCache = new Map<string, Promise<VideoIndex>>();
const videoScriptCache = new Map<string, Promise<VideoScriptDocument>>();

export async function loadVideoIndex(path: string): Promise<VideoIndex> {
  const cached = videoIndexCache.get(path);

  if (cached) {
    return cached;
  }

  const request = fetch(path, { cache: 'no-cache' })
    .then((response) => {
      if (!response.ok) {
        throw new Error(`Unable to load video index: ${response.status} ${response.statusText}`);
      }

      return response.json() as Promise<VideoIndex>;
    })
    .catch((error: unknown) => {
      videoIndexCache.delete(path);
      throw error;
    });

  videoIndexCache.set(path, request);
  return request;
}

export async function loadVideoScript(path: string): Promise<VideoScriptDocument> {
  const cached = videoScriptCache.get(path);

  if (cached) {
    return cached;
  }

  const request = fetch(path, { cache: 'no-cache' })
    .then((response) => {
      if (!response.ok) {
        throw new Error(`Unable to load video script: ${response.status} ${response.statusText}`);
      }

      return response.json() as Promise<VideoScriptDocument>;
    })
    .catch((error: unknown) => {
      videoScriptCache.delete(path);
      throw error;
    });

  videoScriptCache.set(path, request);
  return request;
}
