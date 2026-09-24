import {
  GITHUB_OWNER,
  GITHUB_REPO,
  LOCAL_RUN_RELEASE_TAG,
  githubFetch,
  githubHeaders,
  jarFileName,
  jsonResponse,
  normalizeModuleId,
  normalizeSourceFingerprint,
  releaseAssetLabel,
  type LocalRunEnv,
} from '../../../functions-shared/github';

interface PagesContext {
  request: Request;
  env: LocalRunEnv;
}

interface GitHubRelease {
  id: number;
}

interface GitHubReleaseAsset {
  id: number;
  name: string;
  label?: string | null;
  browser_download_url: string;
}

interface GitHubReleaseAssetsResponse extends Array<GitHubReleaseAsset> {}

async function findRelease(env: LocalRunEnv): Promise<GitHubRelease | null> {
  if (!env.GITHUB_ACTION_TOKEN) {
    throw new Error('GITHUB_ACTION_TOKEN is not configured for this Pages environment.');
  }

  const response = await fetch(
    `https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/tags/${LOCAL_RUN_RELEASE_TAG}`,
    {
      headers: githubHeaders(env.GITHUB_ACTION_TOKEN),
    },
  );

  if (response.status === 404) {
    return null;
  }

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GitHub API ${response.status}: ${body.slice(0, 500)}`);
  }

  return response.json() as Promise<GitHubRelease>;
}

async function listReleaseAssets(env: LocalRunEnv, releaseId: number): Promise<GitHubReleaseAsset[]> {
  const result: GitHubReleaseAsset[] = [];

  for (let page = 1; page <= 10; page += 1) {
    const response = await githubFetch(
      env,
      `/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/${releaseId}/assets?per_page=100&page=${page}`,
    );
    const assets = await response.json() as GitHubReleaseAssetsResponse;
    result.push(...assets);

    if (assets.length < 100) {
      break;
    }
  }

  return result;
}

export async function onRequestGet({ request, env }: PagesContext): Promise<Response> {
  const url = new URL(request.url);
  const moduleId = normalizeModuleId(url.searchParams.get('moduleId'));
  const sourceFingerprint = normalizeSourceFingerprint(url.searchParams.get('sourceFingerprint'));

  if (!moduleId || !sourceFingerprint) {
    return jsonResponse({ message: 'moduleId and sourceFingerprint are required.' }, 400);
  }

  try {
    const fileName = jarFileName(moduleId);
    const release = await findRelease(env);

    if (!release) {
      return jsonResponse({
        moduleId,
        available: false,
        stale: false,
        fileName,
      });
    }

    const assets = await listReleaseAssets(env, release.id);
    const asset = assets.find((candidate) => candidate.name === fileName);

    if (!asset) {
      return jsonResponse({
        moduleId,
        available: false,
        stale: false,
        fileName,
      });
    }

    const fresh = asset.label === releaseAssetLabel(sourceFingerprint);
    if (!fresh) {
      return jsonResponse({
        moduleId,
        available: false,
        stale: true,
        fileName,
      });
    }

    if (url.searchParams.get('download') === '1') {
      return Response.redirect(asset.browser_download_url, 302);
    }

    return jsonResponse({
      moduleId,
      available: true,
      stale: false,
      fileName,
      downloadUrl: asset.browser_download_url,
    });
  } catch (error) {
    return jsonResponse({
      message: error instanceof Error ? error.message : 'Unable to resolve Local Run release asset.',
    }, 502);
  }
}
