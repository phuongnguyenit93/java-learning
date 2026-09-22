import type { ModuleStats } from '../types/learning';

export function resolveModuleStats(_moduleId: string): ModuleStats {
  return {
    knowledge: 0,
    quiz: 0,
    interview: 0,
    apiDocs: 0,
  };
}
