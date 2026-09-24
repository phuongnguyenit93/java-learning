export interface LocalRunEnv {
  GITHUB_ACTION_TOKEN: string;
}

export interface LocalRunReleaseAsset {
  id: number;
  name: string;
  label?: string | null;
  browser_download_url: string;
}

export interface LocalRunWorkflowRun {
  id: number;
  status: string;
  display_title: string;
  html_url?: string;
}

export const GITHUB_OWNER = 'phuongnguyenit93';
export const GITHUB_REPO = 'java-learning';
export const LOCAL_RUN_WORKFLOW = 'local-run-build.yml';
export const LOCAL_RUN_RELEASE_TAG = 'local-run';
export const GITHUB_API_VERSION = '2026-03-10';

const MODULE_ID_PATTERN = /^[A-Z0-9][A-Z0-9_-]{0,63}$/;
const SOURCE_FINGERPRINT_PATTERN = /^[0-9a-f]{40,64}$/;

export function normalizeModuleId(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }

  const normalized = value.trim().toUpperCase();
  return MODULE_ID_PATTERN.test(normalized) ? normalized : null;
}

export function normalizeRunId(value: string | null): string | null {
  if (!value || !/^\d+$/.test(value)) {
    return null;
  }

  return value;
}

export function normalizeSourceFingerprint(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }

  const normalized = value.trim().toLowerCase();
  return SOURCE_FINGERPRINT_PATTERN.test(normalized) ? normalized : null;
}

export function releaseAssetLabel(moduleId: string, sourceFingerprint: string): string {
  return `${moduleId} · fingerprint:${sourceFingerprint}`;
}

export function localRunWorkflowRunName(moduleId: string, sourceFingerprint: string): string {
  return `Local Run ${moduleId} · fingerprint:${sourceFingerprint}`;
}

export function jarFileName(moduleId: string): string {
  const normalized = moduleId
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '');

  return `${normalized || 'module'}.jar`;
}

export async function findLocalRunReleaseAsset(
  env: LocalRunEnv,
  moduleId: string,
): Promise<LocalRunReleaseAsset | null> {
  if (!env.GITHUB_ACTION_TOKEN) {
    throw new Error('GITHUB_ACTION_TOKEN is not configured for this Pages environment.');
  }

  const releaseResponse = await fetch(
    `https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/tags/${LOCAL_RUN_RELEASE_TAG}`,
    { headers: githubHeaders(env.GITHUB_ACTION_TOKEN) },
  );

  if (releaseResponse.status === 404) {
    return null;
  }

  if (!releaseResponse.ok) {
    const body = await releaseResponse.text();
    throw new Error(`GitHub API ${releaseResponse.status}: ${body.slice(0, 500)}`);
  }

  const release = await releaseResponse.json() as { id?: number };
  if (!release.id) {
    throw new Error('GitHub Local Run release has no release id.');
  }

  const expectedName = jarFileName(moduleId);
  for (let page = 1; page <= 10; page += 1) {
    const response = await githubFetch(
      env,
      `/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/${release.id}/assets?per_page=100&page=${page}`,
    );
    const assets = await response.json() as LocalRunReleaseAsset[];
    const asset = assets.find((candidate) => candidate.name === expectedName);
    if (asset) {
      return asset;
    }
    if (assets.length < 100) {
      break;
    }
  }

  return null;
}

export async function findActiveLocalRunWorkflowRun(
  env: LocalRunEnv,
  moduleId: string,
  sourceFingerprint: string,
): Promise<LocalRunWorkflowRun | null> {
  const response = await githubFetch(
    env,
    `/repos/${GITHUB_OWNER}/${GITHUB_REPO}/actions/workflows/${LOCAL_RUN_WORKFLOW}/runs?event=workflow_dispatch&per_page=100`,
  );
  const payload = await response.json() as { workflow_runs?: LocalRunWorkflowRun[] };
  const expectedName = localRunWorkflowRunName(moduleId, sourceFingerprint);
  const activeStatuses = new Set(['queued', 'in_progress', 'requested', 'waiting', 'pending']);

  return payload.workflow_runs?.find((run) => (
    run.display_title === expectedName && activeStatuses.has(run.status)
  )) ?? null;
}

export function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
    },
  });
}

export function githubHeaders(token: string): Headers {
  return new Headers({
    accept: 'application/vnd.github+json',
    authorization: `Bearer ${token}`,
    'x-github-api-version': GITHUB_API_VERSION,
    'user-agent': 'java-learning-project-portal',
  });
}

export async function githubFetch(
  env: LocalRunEnv,
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  if (!env.GITHUB_ACTION_TOKEN) {
    throw new Error('GITHUB_ACTION_TOKEN is not configured for this Pages environment.');
  }

  const headers = githubHeaders(env.GITHUB_ACTION_TOKEN);
  if (init.body && !headers.has('content-type')) {
    headers.set('content-type', 'application/json');
  }

  const response = await fetch(`https://api.github.com${path}`, {
    ...init,
    headers,
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GitHub API ${response.status}: ${body.slice(0, 500)}`);
  }

  return response;
}

export function sameOriginRequest(request: Request): boolean {
  const origin = request.headers.get('origin');
  if (!origin) {
    return true;
  }

  return origin === new URL(request.url).origin;
}
