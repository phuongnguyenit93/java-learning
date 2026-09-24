export type LocalRunBuildStatus = 'QUEUED' | 'BUILDING' | 'SUCCESS' | 'FAILED';

export interface LocalRunBuildRequest {
  moduleId: string;
  sourceFingerprint: string;
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
  stale?: boolean;
  fileName: string;
  downloadUrl?: string;
}

export interface LocalRunApi {
  startBuild(request: LocalRunBuildRequest): Promise<LocalRunBuildResponse>;
  getBuildStatus(runId: string, moduleId: string): Promise<LocalRunStatusResponse>;
  getArtifact(moduleId: string, sourceFingerprint: string): Promise<LocalRunArtifactResponse>;
}

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
  startBuild({ moduleId, sourceFingerprint }) {
    return requestJson<LocalRunBuildResponse>('/api/local-run/build', {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
      },
      body: JSON.stringify({ moduleId, sourceFingerprint }),
    });
  },

  getBuildStatus(runId, moduleId) {
    const search = new URLSearchParams({ runId, moduleId });
    return requestJson<LocalRunStatusResponse>(`/api/local-run/status?${search}`);
  },

  getArtifact(moduleId, sourceFingerprint) {
    const search = new URLSearchParams({ moduleId, sourceFingerprint });
    return requestJson<LocalRunArtifactResponse>(`/api/local-run/artifact?${search}`);
  },
};

export const localRunApi: LocalRunApi = httpLocalRunApi;
