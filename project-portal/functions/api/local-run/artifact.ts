import {
  findLocalRunReleaseAsset,
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

export async function onRequestGet({ request, env }: PagesContext): Promise<Response> {
  const url = new URL(request.url);
  const moduleId = normalizeModuleId(url.searchParams.get('moduleId'));
  const sourceFingerprint = normalizeSourceFingerprint(url.searchParams.get('sourceFingerprint'));

  if (!moduleId || !sourceFingerprint) {
    return jsonResponse({ message: 'moduleId and sourceFingerprint are required.' }, 400);
  }

  try {
    const fileName = jarFileName(moduleId);
    const asset = await findLocalRunReleaseAsset(env, moduleId);

    if (!asset) {
      return jsonResponse({
        moduleId,
        available: false,
        stale: false,
        fileName,
      });
    }

    const fresh = asset.label === releaseAssetLabel(moduleId, sourceFingerprint);
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
