import {
  GITHUB_OWNER,
  GITHUB_REPO,
  githubFetch,
  githubHeaders,
  jarFileName,
  jsonResponse,
  normalizeModuleId,
  normalizeRunId,
  type LocalRunEnv,
} from '../../../functions-shared/github';
import { extractJarStream } from '../../../functions-shared/zip';

interface PagesContext {
  request: Request;
  env: LocalRunEnv;
}

interface GitHubArtifact {
  id: number;
  name: string;
  expired: boolean;
  size_in_bytes: number;
}

interface ArtifactListResponse {
  artifacts?: GitHubArtifact[];
}

const MAX_ARTIFACT_ARCHIVE_BYTES = 60 * 1024 * 1024;

async function findArtifact(env: LocalRunEnv, runId: string, moduleId: string): Promise<GitHubArtifact | null> {
  const response = await githubFetch(
    env,
    `/repos/${GITHUB_OWNER}/${GITHUB_REPO}/actions/runs/${runId}/artifacts?per_page=100`,
  );
  const payload = await response.json() as ArtifactListResponse;
  const expectedName = `local-run-${moduleId}`;

  return payload.artifacts?.find((artifact) => artifact.name === expectedName && !artifact.expired) ?? null;
}

async function downloadArtifactArchive(env: LocalRunEnv, artifactId: number): Promise<Uint8Array> {
  const response = await fetch(
    `https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}/actions/artifacts/${artifactId}/zip`,
    {
      headers: githubHeaders(env.GITHUB_ACTION_TOKEN),
      redirect: 'manual',
    },
  );

  if (response.status !== 302) {
    throw new Error(`GitHub artifact download returned ${response.status}.`);
  }

  const location = response.headers.get('location');
  if (!location) {
    throw new Error('GitHub artifact download did not return a redirect URL.');
  }

  const archiveResponse = await fetch(location, { redirect: 'follow' });
  if (!archiveResponse.ok) {
    throw new Error(`GitHub artifact archive returned ${archiveResponse.status}.`);
  }

  const contentLength = Number(archiveResponse.headers.get('content-length') ?? '0');
  if (contentLength > MAX_ARTIFACT_ARCHIVE_BYTES) {
    throw new Error('Local Run artifact is too large to proxy through the Pages Function.');
  }

  const archive = new Uint8Array(await archiveResponse.arrayBuffer());
  if (archive.byteLength > MAX_ARTIFACT_ARCHIVE_BYTES) {
    throw new Error('Local Run artifact is too large to proxy through the Pages Function.');
  }

  return archive;
}

export async function onRequestGet({ request, env }: PagesContext): Promise<Response> {
  const url = new URL(request.url);
  const runId = normalizeRunId(url.searchParams.get('runId'));
  const moduleId = normalizeModuleId(url.searchParams.get('moduleId'));

  if (!runId || !moduleId) {
    return jsonResponse({ message: 'runId and moduleId are required.' }, 400);
  }

  try {
    const artifact = await findArtifact(env, runId, moduleId);
    const fileName = jarFileName(moduleId);

    if (!artifact) {
      return jsonResponse({
        moduleId,
        available: false,
        fileName,
      });
    }

    if (url.searchParams.get('download') !== '1') {
      const downloadUrl = new URL(request.url);
      downloadUrl.searchParams.set('download', '1');

      return jsonResponse({
        moduleId,
        available: true,
        fileName,
        downloadUrl: `${downloadUrl.pathname}${downloadUrl.search}`,
      });
    }

    if (artifact.size_in_bytes > MAX_ARTIFACT_ARCHIVE_BYTES) {
      return jsonResponse({ message: 'Local Run artifact is too large to download through the Portal.' }, 413);
    }

    const archive = await downloadArtifactArchive(env, artifact.id);
    const jarStream = extractJarStream(archive, fileName);

    return new Response(jarStream, {
      headers: {
        'content-type': 'application/java-archive',
        'content-disposition': `attachment; filename="${fileName}"`,
        'cache-control': 'private, no-store',
        'x-content-type-options': 'nosniff',
      },
    });
  } catch (error) {
    return jsonResponse({
      message: error instanceof Error ? error.message : 'Unable to resolve Local Run artifact.',
    }, 502);
  }
}
