import { parse } from 'yaml';
import { collectKnowledgeCategories } from './knowledge';
import type {
  ApiDocController,
  ApiKnowledgeRelation,
  ApiDocOperation,
  ApiDocsDocument,
  ApiParamDefinition,
  ApiReadmeRelated,
  KnowledgeIndex,
} from '../types/learning';

interface ApiDocsSourceBundle {
  controllerDescriptions: Record<string, unknown>;
  apiDescriptions: Record<string, unknown>;
  apiExecutions: Record<string, unknown>;
  apiParams: Record<string, unknown>;
}

const sourceCache = new Map<string, Promise<ApiDocsSourceBundle>>();

function asRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function asString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function asBoolean(value: unknown, fallback = true): boolean {
  return typeof value === 'boolean' ? value : fallback;
}

function asStringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.map((item) => String(item ?? '').trim()).filter(Boolean)
    : [];
}

function normalizeBasePath(basePath: string): string {
  return basePath.replace(/\/+$/, '');
}

async function fetchText(path: string): Promise<string> {
  const response = await fetch(path, { cache: 'no-cache' });

  if (!response.ok) {
    throw new Error(`Unable to load API metadata: ${response.status} ${response.statusText}`);
  }

  return response.text();
}

async function loadApiDocsSource(basePath: string): Promise<ApiDocsSourceBundle> {
  const normalizedBasePath = normalizeBasePath(basePath);
  const cached = sourceCache.get(normalizedBasePath);

  if (cached) {
    return cached;
  }

  const request = Promise.all([
    fetchText(`${normalizedBasePath}/controller-description.yml`),
    fetchText(`${normalizedBasePath}/api-descriptions.yml`),
    fetchText(`${normalizedBasePath}/api-execution.yml`),
    fetchText(`${normalizedBasePath}/api-params.yml`),
  ])
    .then(([controllerDescriptions, apiDescriptions, apiExecutions, apiParams]) => ({
      controllerDescriptions: asRecord(parse(controllerDescriptions)),
      apiDescriptions: asRecord(parse(apiDescriptions)),
      apiExecutions: asRecord(parse(apiExecutions)),
      apiParams: asRecord(parse(apiParams)),
    }))
    .catch((error: unknown) => {
      sourceCache.delete(normalizedBasePath);
      throw error;
    });

  sourceCache.set(normalizedBasePath, request);
  return request;
}

function normalizeReadmeRelated(value: unknown): ApiReadmeRelated {
  const related = asRecord(value);
  const file = asString(related.file).trim();
  const anchor = asString(related.anchor).trim();

  return {
    ...(file ? { file } : {}),
    ...(anchor ? { anchor } : {}),
  };
}

function resolveChapterOrder(file: string | undefined): number | null {
  if (!file) {
    return null;
  }

  const firstSegment = file.split(/[\\/]/)[0]?.trim() ?? '';
  const match = firstSegment.match(/^(\d+)/);

  return match ? Number.parseInt(match[1], 10) : null;
}

function decodeBasicHtmlEntities(value: string): string {
  return value
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'");
}

function stripHtml(value: string): string {
  return decodeBasicHtmlEntities(
    value
      .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ' ')
      .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, ' ')
      .replace(/<[^>]+>/g, ' ')
      .replace(/\s+/g, ' ')
      .trim(),
  );
}

function extractControllerTitle(descriptionHtml: string, fallback: string): string {
  const match = descriptionHtml.match(/<h2\b[^>]*>([\s\S]*?)<\/h2>/i);
  const title = match ? stripHtml(match[1]) : '';

  return title || fallback;
}

function extractControllerDescription(descriptionHtml: string): string {
  const withoutHeading = descriptionHtml.replace(/<h2\b[^>]*>[\s\S]*?<\/h2>/i, ' ');
  return stripHtml(withoutHeading);
}

function resolveParamDefinitions(
  paramNames: string[],
  apiParams: Record<string, unknown>,
): ApiParamDefinition[] {
  return paramNames.map((name) => {
    const definition = asRecord(apiParams[name]);
    return {
      name,
      summary: asString(definition.summary).trim(),
      description: asString(definition.description).trim(),
    };
  });
}

function buildReadmeAnchorOrder(index: KnowledgeIndex | null): Map<string, number> {
  const result = new Map<string, number>();

  if (!index) {
    return result;
  }

  let order = 0;
  collectKnowledgeCategories(index.tree).forEach((category) => {
    category.sections.forEach((section) => {
      result.set(`${category.sourcePath}#${section.id}`, order);
      order += 1;
    });
  });

  return result;
}

function sortOperations(
  operations: ApiDocOperation[],
  controllerReadmeFile: string | undefined,
  readmeAnchorOrder: Map<string, number>,
): ApiDocOperation[] {
  return [...operations].sort((left, right) => {
    const leftFile = left.readmeRelated.file || controllerReadmeFile;
    const rightFile = right.readmeRelated.file || controllerReadmeFile;
    const leftKey = leftFile && left.readmeRelated.anchor
      ? `${leftFile}#${left.readmeRelated.anchor}`
      : '';
    const rightKey = rightFile && right.readmeRelated.anchor
      ? `${rightFile}#${right.readmeRelated.anchor}`
      : '';
    const leftOrder = leftKey ? readmeAnchorOrder.get(leftKey) : undefined;
    const rightOrder = rightKey ? readmeAnchorOrder.get(rightKey) : undefined;

    if (leftOrder !== undefined && rightOrder !== undefined && leftOrder !== rightOrder) {
      return leftOrder - rightOrder;
    }

    if (leftOrder !== undefined && rightOrder === undefined) {
      return -1;
    }

    if (leftOrder === undefined && rightOrder !== undefined) {
      return 1;
    }

    return left.methodSignature.localeCompare(right.methodSignature);
  });
}

function buildDocument(
  source: ApiDocsSourceBundle,
  knowledgeIndex: KnowledgeIndex | null,
): ApiDocsDocument {
  const readmeAnchorOrder = buildReadmeAnchorOrder(knowledgeIndex);
  const controllers: ApiDocController[] = [];

  Object.entries(source.apiDescriptions).forEach(([controllerName, methodsValue]) => {
    const controllerDescription = asRecord(source.controllerDescriptions[controllerName]);
    const descriptionHtml = asString(controllerDescription.description).trim();
    const controllerReadmeRelated = normalizeReadmeRelated(controllerDescription.readmeRelated);
    const executionController = asRecord(source.apiExecutions[controllerName]);
    const operations: ApiDocOperation[] = [];

    Object.entries(asRecord(methodsValue)).forEach(([methodSignature, methodValue]) => {
      const method = asRecord(methodValue);

      if (!asBoolean(method.usage)) {
        return;
      }

      const execution = asRecord(executionController[methodSignature]);
      const httpMethods = asStringArray(method.httpMethods);
      const mappingPaths = asStringArray(method.mappingPaths);
      const methodName = asString(method.methodName).trim() || methodSignature.replace(/\(.*/, '');
      const operationReadmeRelated = normalizeReadmeRelated(method.readmeRelated);
      const paramNames = asStringArray(method.params);

      operations.push({
        id: `${controllerName}:${methodSignature}`,
        controllerName,
        methodSignature,
        methodName,
        httpMethod: httpMethods[0] || 'GET',
        path: mappingPaths[0] || '',
        summary: asString(method.summary).trim() || methodName,
        description: asString(method.description).trim(),
        executionHtml: asString(execution.execution).trim(),
        consumes: asStringArray(method.consumes),
        produces: asStringArray(method.produces),
        params: resolveParamDefinitions(paramNames, source.apiParams),
        readmeRelated: operationReadmeRelated,
        aiGenerated: asBoolean(method.aiGenerated, false),
        reviewed: asBoolean(method.reviewed, false),
      });
    });

    if (operations.length === 0) {
      return;
    }

    controllers.push({
      name: controllerName,
      title: extractControllerTitle(descriptionHtml, controllerName),
      description: extractControllerDescription(descriptionHtml),
      descriptionHtml,
      readmeRelated: controllerReadmeRelated,
      chapterOrder: resolveChapterOrder(controllerReadmeRelated.file),
      operations: sortOperations(operations, controllerReadmeRelated.file, readmeAnchorOrder),
    });
  });

  controllers.sort((left, right) => {
    if (left.chapterOrder !== null && right.chapterOrder !== null && left.chapterOrder !== right.chapterOrder) {
      return left.chapterOrder - right.chapterOrder;
    }

    if (left.chapterOrder !== null && right.chapterOrder === null) {
      return -1;
    }

    if (left.chapterOrder === null && right.chapterOrder !== null) {
      return 1;
    }

    return left.name.localeCompare(right.name);
  });

  return {
    operationCount: controllers.reduce((count, controller) => count + controller.operations.length, 0),
    controllers,
  };
}

export async function loadApiDocs(
  basePath: string,
  knowledgeIndex: KnowledgeIndex | null,
): Promise<ApiDocsDocument> {
  const source = await loadApiDocsSource(basePath);
  return buildDocument(source, knowledgeIndex);
}

export async function loadApiOperationCount(basePath: string): Promise<number> {
  const source = await loadApiDocsSource(basePath);
  return buildDocument(source, null).operationCount;
}

export function resolveOperationKnowledgeKey(
  controller: ApiDocController,
  operation: ApiDocOperation,
): string | null {
  const sourcePath = operation.readmeRelated.file || controller.readmeRelated.file;
  const anchor = operation.readmeRelated.anchor;

  return sourcePath && anchor ? `${sourcePath}#${anchor}` : null;
}

export function collectApiKnowledgeRelations(document: ApiDocsDocument): ApiKnowledgeRelation[] {
  return document.controllers.flatMap((controller) =>
    controller.operations.flatMap((operation) => {
      const sourcePath = operation.readmeRelated.file || controller.readmeRelated.file;
      const anchor = operation.readmeRelated.anchor;

      if (!sourcePath || !anchor) {
        return [];
      }

      return [{ controller, operation, sourcePath, anchor }];
    }),
  );
}
