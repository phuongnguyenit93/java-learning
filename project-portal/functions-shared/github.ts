export interface LocalRunEnv {
  GITHUB_ACTION_TOKEN: string;
}

export const GITHUB_OWNER = 'phuongnguyenit93';
export const GITHUB_REPO = 'java-learning';
export const LOCAL_RUN_WORKFLOW = 'local-run-build.yml';
export const GITHUB_API_VERSION = '2026-03-10';

const MODULE_ID_PATTERN = /^[A-Z0-9][A-Z0-9_-]{0,63}$/;

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

export function jarFileName(moduleId: string): string {
  const normalized = moduleId
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '');

  return `${normalized || 'module'}.jar`;
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
