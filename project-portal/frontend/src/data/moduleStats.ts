import { knowledgeItems, quizItems } from './mockLearningData';
import type { ModuleStats } from '../types/learning';

export function resolveModuleStats(moduleId: string): ModuleStats {
  return {
    knowledge: knowledgeItems.filter((item) => item.moduleId === moduleId).length,
    quiz: quizItems.filter((item) => item.moduleId === moduleId).length,
    apiDocs: 0,
  };
}
