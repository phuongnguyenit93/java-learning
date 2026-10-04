export type Language = 'vi' | 'en';

export type LocalizedText = Record<Language, string>;

export interface ModuleStats {
  knowledge: number;
  quiz: number;
  interview: number;
  apiDocs: number;
}

export interface RoadmapRelatedModule {
  routeId: string;
  label?: string;
  note?: string;
}

export interface RoadmapMilestone {
  id: string;
  title: string;
  purpose: string;
  dependsOn: string[];
  objectives: string[];
  relatedKnowledge: string[];
  relatedModules: RoadmapRelatedModule[];
}

export interface RoadmapDocument {
  roadmap: RoadmapMilestone[];
}

export interface CapabilityState {
  knowledge: boolean;
  quiz: boolean;
  interview: boolean;
  apiDocs: boolean;
  execution: boolean;
  download: boolean;
}

export interface LearningModule {
  id: string;
  sourceFingerprint: string;
  name: LocalizedText;
  shortName: string;
  description: LocalizedText;
  path: string[];
  questionCount: number;
  overview: Partial<Record<Language, string>>;
  knowledge: Partial<Record<Language, string>>;
  video: Partial<Record<Language, string>>;
  quiz: Partial<Record<Language, string>>;
  interview: Partial<Record<Language, string>>;
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
  sourceFingerprint?: string;
  serviceName?: string;
  moduleType?: 'SERVLET' | 'REACTIVE' | 'LIBRARY' | 'PLATFORM' | string;
  javaBasePackage?: string;
  description?: string;
  moduleDepend?: boolean;
  overview?: Partial<Record<Language, string>>;
  knowledge?: Partial<Record<Language, string>>;
  video?: Partial<Record<Language, string>>;
  roadmap?: Partial<Record<Language, string>>;
  quiz?: Partial<Record<Language, string>>;
  interview?: Partial<Record<Language, string>>;
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

export interface VideoIndexItem {
  categoryId: string;
  title: string;
  sourcePath: string;
  content: string;
  url: string;
  sectionCount: number;
  sceneCount: number;
  transitionCount: number;
}

export interface VideoIndex {
  version: number;
  moduleId: string;
  language: Language;
  videoCount: number;
  items: VideoIndexItem[];
}

export type VideoScriptItemType = 'SCENE' | 'TRANSITION';

export interface VideoScriptItem {
  type: VideoScriptItemType;
  title?: string;
  time: string;
  visual: string;
  script: string;
  purpose: string;
}

export interface VideoScriptSection {
  order: number;
  title: string;
  items: VideoScriptItem[];
}

export interface VideoScriptDocument {
  version: number;
  moduleId: string;
  language: Language;
  categoryId: string;
  title: string;
  sourcePath: string;
  video: {
    url: string;
  };
  sections: VideoScriptSection[];
}

export type KnowledgeSearchDocumentType = 'CATEGORY' | 'SECTION';

export interface KnowledgeSearchDocument {
  documentId: string;
  type: KnowledgeSearchDocumentType;
  moduleId: string;
  categoryId: string;
  categoryTitle: string;
  sectionId?: string;
  title: string;
  sourcePath: string;
  difficulty?: KnowledgeDifficulty;
  preview: string;
  searchText: string;
}

export interface KnowledgeSearchIndex {
  version: number;
  language: Language;
  documentCount: number;
  documents: KnowledgeSearchDocument[];
}

export interface KnowledgeSearchResult {
  document: KnowledgeSearchDocument;
  score: number;
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

export type QuizAnswerId = 'A' | 'B' | 'C' | 'D';

export interface QuizAnswer {
  id: QuizAnswerId;
  answer: string;
  explanation: string;
}

export interface QuizReadmeRelated {
  file: string;
  anchor: string;
}

export interface QuizApiRelated {
  controller: string;
  methodSignature: string;
}

export interface QuizQuestion {
  id: string;
  question: string;
  aiGenerated: boolean;
  reviewed: boolean;
  readmeRelated: QuizReadmeRelated;
  apiRelated: QuizApiRelated;
  answers: QuizAnswer[];
  correctAnswerId: QuizAnswerId;
}

export interface QuizDocument {
  questions: QuizQuestion[];
}

export interface InterviewQuestion {
  question: string;
  answer: string;
  aiGenerated: boolean;
  reviewed: boolean;
  readmeRelated: QuizReadmeRelated;
  apiRelated: QuizApiRelated;
}

export interface InterviewDocument {
  questions: InterviewQuestion[];
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
