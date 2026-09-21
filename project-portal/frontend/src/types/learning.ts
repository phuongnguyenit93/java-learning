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
  overview: Partial<Record<Language, string>>;
  knowledge: Partial<Record<Language, string>>;
  api: Partial<Record<Language, string>>;
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
  overview?: Partial<Record<Language, string>>;
  knowledge?: Partial<Record<Language, string>>;
  api?: Partial<Record<Language, string>>;
  children: ModuleCatalogNode[];
}

export interface KnowledgeSection {
  id: string;
  title: string;
  content: string;
  difficulty: KnowledgeDifficulty;
  aiGenerated: boolean;
  reviewed: boolean;
}

export type KnowledgeDifficulty = 'BASIC' | 'INTERMEDIATE' | 'ADVANCED';

export interface KnowledgeCategoryNode {
  kind: 'CATEGORY';
  id: string;
  title: string;
  sourcePath: string;
  intro?: string;
  sections: KnowledgeSection[];
}

export interface KnowledgeFolderNode {
  kind: 'FOLDER';
  id: string;
  title: string;
  children: KnowledgeTreeNode[];
}

export type KnowledgeTreeNode = KnowledgeFolderNode | KnowledgeCategoryNode;

export interface KnowledgeIndex {
  version: number;
  moduleId: string;
  language: Language;
  categoryCount: number;
  sectionCount: number;
  tree: KnowledgeTreeNode[];
}

export interface KnowledgeSectionRef extends KnowledgeSection {
  categoryId: string;
  categoryTitle: string;
  sourcePath: string;
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

export interface ApiReadmeRelated {
  file?: string;
  anchor?: string;
}

export interface ApiParamDefinition {
  name: string;
  summary: string;
  description: string;
}

export interface ApiDocOperation {
  id: string;
  controllerName: string;
  methodSignature: string;
  methodName: string;
  httpMethod: string;
  path: string;
  summary: string;
  description: string;
  executionHtml: string;
  consumes: string[];
  produces: string[];
  params: ApiParamDefinition[];
  readmeRelated: ApiReadmeRelated;
  aiGenerated: boolean;
  reviewed: boolean;
}

export interface ApiDocController {
  name: string;
  title: string;
  description: string;
  descriptionHtml: string;
  readmeRelated: ApiReadmeRelated;
  chapterOrder: number | null;
  operations: ApiDocOperation[];
}

export interface ApiDocsDocument {
  operationCount: number;
  controllers: ApiDocController[];
}

export interface ApiKnowledgeRelation {
  controller: ApiDocController;
  operation: ApiDocOperation;
  sourcePath: string;
  anchor: string;
}
