export type Language = 'vi' | 'en';

export type LocalizedText = Record<Language, string>;

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

export interface ModuleTreeNode {
  id: string;
  label: LocalizedText;
  count?: number;
  moduleId?: string;
  children?: ModuleTreeNode[];
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

