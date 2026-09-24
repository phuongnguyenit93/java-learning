export type LocalRunBuildStatus = 'QUEUED' | 'BUILDING' | 'SUCCESS' | 'FAILED';

export interface LocalRunBuildRequest {
  moduleId: string;
}

export interface LocalRunBuildResponse {
  runId: string;
  moduleId: string;
  status: LocalRunBuildStatus;
}

export interface LocalRunStatusResponse extends LocalRunBuildResponse {
  message?: string;
}

export interface LocalRunArtifactResponse {
  moduleId: string;
  available: boolean;
  fileName: string;
  downloadUrl?: string;
}

export interface LocalRunApi {
  startBuild(request: LocalRunBuildRequest): Promise<LocalRunBuildResponse>;
  getBuildStatus(runId: string, moduleId: string): Promise<LocalRunStatusResponse>;
  getArtifact(runId: string, moduleId: string): Promise<LocalRunArtifactResponse>;
}

interface MockRun {
  moduleId: string;
  startedAt: number;
}

const mockRuns = new Map<string, MockRun>();
const mockArtifacts = new Set<string>();

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

function toJarFileName(moduleId: string): string {
  const normalized = moduleId
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '');

  return `${normalized || 'module'}.jar`;
}

const mockLocalRunApi: LocalRunApi = {
  async startBuild({ moduleId }) {
    await delay(350);

    const runId = `mock-${moduleId.toLowerCase()}-${Date.now()}`;
    mockRuns.set(runId, {
      moduleId,
      startedAt: Date.now(),
    });
    mockArtifacts.delete(moduleId);

    return {
      runId,
      moduleId,
      status: 'QUEUED',
    };
  },

  async getBuildStatus(runId) {
    await delay(180);

    const run = mockRuns.get(runId);
    if (!run) {
      throw new Error(`Mock Local Run not found: ${runId}`);
    }

    const elapsed = Date.now() - run.startedAt;

    if (elapsed < 900) {
      return {
        runId,
        moduleId: run.moduleId,
        status: 'QUEUED',
        message: 'Waiting for a GitHub Actions runner.',
      };
    }

    if (elapsed < 2400) {
      return {
        runId,
        moduleId: run.moduleId,
        status: 'BUILDING',
        message: 'Running the module bootJar task.',
      };
    }

    mockArtifacts.add(run.moduleId);

    return {
      runId,
      moduleId: run.moduleId,
      status: 'SUCCESS',
      message: 'Executable JAR generated successfully.',
    };
  },

  async getArtifact(_runId, moduleId) {
    await delay(250);

    return {
      moduleId,
      available: mockArtifacts.has(moduleId),
      fileName: toJarFileName(moduleId),
      // Mock mode intentionally does not create a fake .jar binary.
      // The real Cloudflare/GitHub implementation will provide this URL.
      downloadUrl: undefined,
    };
  },
};

async function readApiError(response: Response): Promise<string> {
  try {
    const payload = await response.json() as { message?: unknown };
    if (typeof payload.message === 'string' && payload.message.trim()) {
      return payload.message;
    }
  } catch {
    // Fall through to the generic status message.
  }

  return `Local Run API returned ${response.status} ${response.statusText}`.trim();
}

async function requestJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    cache: 'no-store',
    ...init,
  });

  if (!response.ok) {
    throw new Error(await readApiError(response));
  }

  return response.json() as Promise<T>;
}

const httpLocalRunApi: LocalRunApi = {
  startBuild({ moduleId }) {
    return requestJson<LocalRunBuildResponse>('/api/local-run/build', {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
      },
      body: JSON.stringify({ moduleId }),
    });
  },

  getBuildStatus(runId, moduleId) {
    const search = new URLSearchParams({ runId, moduleId });
    return requestJson<LocalRunStatusResponse>(`/api/local-run/status?${search}`);
  },

  getArtifact(runId, moduleId) {
    const search = new URLSearchParams({ runId, moduleId });
    return requestJson<LocalRunArtifactResponse>(`/api/local-run/artifact?${search}`);
  },
};

// Production uses the same-origin Cloudflare Pages Functions API.
// Local frontend development can opt into the in-memory simulator with:
// VITE_LOCAL_RUN_MODE=mock
export const localRunApi: LocalRunApi = import.meta.env.VITE_LOCAL_RUN_MODE === 'mock'
  ? mockLocalRunApi
  : httpLocalRunApi;
