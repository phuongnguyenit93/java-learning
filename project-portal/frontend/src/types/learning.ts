export type Language = 'vi' | 'en';

export type LocalizedText = Record<Language, string>;

export interface ModuleStats {
  knowledge: number;
  quiz: number;
  apiDocs: number;
}

export interface CapabilityState {
  knowledge: boolean;
  quiz: boolean;
  apiDocs: boolean;
  execution: boolean;
  download: boolean;
}

export interface LearningModule {
  id: string;
  name: LocalizedText;
  shortName: string;
  description: LocalizedText;
  path: string[];
  questionCount: number;
  capabilities: CapabilityState;
}

export interface ModuleCatalog {
  version: number;
  root: ModuleCatalogNode;
}

export interface ModuleCatalogNode {
  id: string;
  name: string;
  path: string;
  kind: 'GROUP' | 'MODULE';
  routeId?: string;
  serviceName?: string;
  moduleType?: 'SERVLET' | 'REACTIVE' | 'LIBRARY' | 'PLATFORM' | string;
  javaBasePackage?: string;
  description?: string;
  moduleDepend?: boolean;
  children: ModuleCatalogNode[];
}

export interface KnowledgeTopic {
  id: string;
  label: LocalizedText;
}

export interface KnowledgeItem {
  id: string;
  moduleId: string;
  topicId: string;
  title: LocalizedText;
  level: 'basic' | 'intermediate' | 'advanced';
  summary: LocalizedText;
  paragraphs: LocalizedText[];
  bullets: LocalizedText[];
  code?: string;
}

export interface QuizItem {
  id: string;
  moduleId: string;
  question: LocalizedText;
  answers: LocalizedText[];
}

export interface ApiOperation {
  id: string;
  moduleId: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  path: string;
  summary: LocalizedText;
  description: LocalizedText;
}
