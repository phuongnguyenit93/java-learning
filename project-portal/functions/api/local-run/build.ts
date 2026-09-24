import {
  GITHUB_OWNER,
  GITHUB_REPO,
  LOCAL_RUN_WORKFLOW,
  findActiveLocalRunWorkflowRun,
  findLocalRunReleaseAsset,
  githubFetch,
  jsonResponse,
  normalizeModuleId,
  normalizeSourceFingerprint,
  releaseAssetLabel,
  sameOriginRequest,
  type LocalRunEnv,
} from '../../../functions-shared/github';

interface PagesContext {
  request: Request;
  env: LocalRunEnv;
}

interface DispatchResponse {
  workflow_run_id?: number;
  html_url?: string;
}

export async function onRequestPost({ request, env }: PagesContext): Promise<Response> {
  if (!sameOriginRequest(request)) {
    return jsonResponse({ message: 'Cross-origin build requests are not allowed.' }, 403);
  }

  let payload: unknown;
  try {
    payload = await request.json();
  } catch {
    return jsonResponse({ message: 'Request body must be valid JSON.' }, 400);
  }

  const moduleId = normalizeModuleId((payload as { moduleId?: unknown })?.moduleId);
  const sourceFingerprint = normalizeSourceFingerprint((payload as { sourceFingerprint?: unknown })?.sourceFingerprint);
  if (!moduleId || !sourceFingerprint) {
    return jsonResponse({ message: 'moduleId or sourceFingerprint is invalid.' }, 400);
  }

  try {
    const asset = await findLocalRunReleaseAsset(env, moduleId);
    if (asset?.label === releaseAssetLabel(moduleId, sourceFingerprint)) {
      return jsonResponse({
        moduleId,
        status: 'SUCCESS',
        result: 'AVAILABLE',
      });
    }

    const activeRun = await findActiveLocalRunWorkflowRun(env, moduleId, sourceFingerprint);
    if (activeRun) {
      return jsonResponse({
        runId: String(activeRun.id),
        moduleId,
        status: activeRun.status === 'in_progress' ? 'BUILDING' : 'QUEUED',
        result: 'REUSED',
        runUrl: activeRun.html_url,
      });
    }

    const response = await githubFetch(
      env,
      `/repos/${GITHUB_OWNER}/${GITHUB_REPO}/actions/workflows/${LOCAL_RUN_WORKFLOW}/dispatches`,
      {
        method: 'POST',
        body: JSON.stringify({
          ref: 'main',
          inputs: { moduleId, sourceFingerprint },
        }),
      },
    );
    const dispatched = await response.json() as DispatchResponse;

    if (!dispatched.workflow_run_id) {
      return jsonResponse({ message: 'GitHub accepted the build but did not return a workflow run id.' }, 502);
    }

    return jsonResponse({
      runId: String(dispatched.workflow_run_id),
      moduleId,
      status: 'QUEUED',
      result: 'DISPATCHED',
      runUrl: dispatched.html_url,
    });
  } catch (error) {
    return jsonResponse({
      message: error instanceof Error ? error.message : 'Unable to dispatch Local Run build.',
    }, 502);
  }
}
