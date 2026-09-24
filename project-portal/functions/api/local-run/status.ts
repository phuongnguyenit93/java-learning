import {
  GITHUB_OWNER,
  GITHUB_REPO,
  githubFetch,
  jsonResponse,
  normalizeModuleId,
  normalizeRunId,
  type LocalRunEnv,
} from '../../../functions-shared/github';

interface PagesContext {
  request: Request;
  env: LocalRunEnv;
}

interface WorkflowRunResponse {
  status?: string;
  conclusion?: string | null;
  html_url?: string;
}

function mapStatus(status: string | undefined, conclusion: string | null | undefined): 'QUEUED' | 'BUILDING' | 'SUCCESS' | 'FAILED' {
  if (status === 'completed') {
    return conclusion === 'success' ? 'SUCCESS' : 'FAILED';
  }

  if (status === 'in_progress') {
    return 'BUILDING';
  }

  return 'QUEUED';
}

export async function onRequestGet({ request, env }: PagesContext): Promise<Response> {
  const url = new URL(request.url);
  const runId = normalizeRunId(url.searchParams.get('runId'));
  const moduleId = normalizeModuleId(url.searchParams.get('moduleId'));

  if (!runId || !moduleId) {
    return jsonResponse({ message: 'runId and moduleId are required.' }, 400);
  }

  try {
    const response = await githubFetch(
      env,
      `/repos/${GITHUB_OWNER}/${GITHUB_REPO}/actions/runs/${runId}`,
    );
    const run = await response.json() as WorkflowRunResponse;
    const status = mapStatus(run.status, run.conclusion);

    return jsonResponse({
      runId,
      moduleId,
      status,
      runUrl: run.html_url,
      message: status === 'FAILED' ? `GitHub Actions finished with conclusion: ${run.conclusion ?? 'unknown'}.` : undefined,
    });
  } catch (error) {
    return jsonResponse({
      message: error instanceof Error ? error.message : 'Unable to read Local Run build status.',
    }, 502);
  }
}
