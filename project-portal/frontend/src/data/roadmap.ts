import { parse } from 'yaml';
import type { Language, RoadmapDocument, RoadmapMilestone, RoadmapRelatedModule } from '../types/learning';

const roadmapCache = new Map<string, Promise<RoadmapDocument>>();

export function resolveRoadmapPath(routeId: string, language: Language): string {
  return `/module/${encodeURIComponent(routeId)}/roadmap/${language}/roadmap.yml`;
}

export async function loadRoadmapDocument(
  routeId: string,
  language: Language,
): Promise<RoadmapDocument> {
  const path = resolveRoadmapPath(routeId, language);
  const cached = roadmapCache.get(path);

  if (cached) {
    return cached;
  }

  const request = fetch(path, { cache: 'no-cache' })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`Unable to load Roadmap: ${response.status} ${response.statusText}`);
      }

      return parseRoadmapDocument(parse(await response.text()));
    })
    .catch((error) => {
      roadmapCache.delete(path);
      throw error;
    });

  roadmapCache.set(path, request);
  return request;
}

function parseRoadmapDocument(value: unknown): RoadmapDocument {
  if (!isRecord(value) || !Array.isArray(value.roadmap)) {
    throw new Error('Invalid Roadmap document: roadmap must be an array.');
  }

  return {
    roadmap: value.roadmap.map((item, index) => parseMilestone(item, index)),
  };
}

function parseMilestone(value: unknown, index: number): RoadmapMilestone {
  if (!isRecord(value)) {
    throw new Error(`Invalid Roadmap milestone at index ${index}.`);
  }

  return {
    id: requireString(value.id, `roadmap[${index}].id`),
    title: requireString(value.title, `roadmap[${index}].title`),
    purpose: requireString(value.purpose, `roadmap[${index}].purpose`),
    dependsOn: optionalStringArray(value.dependsOn, `roadmap[${index}].dependsOn`),
    objectives: optionalStringArray(value.objectives, `roadmap[${index}].objectives`),
    relatedKnowledge: optionalStringArray(value.relatedKnowledge, `roadmap[${index}].relatedKnowledge`),
    relatedModules: optionalRelatedModules(value.relatedModules, `roadmap[${index}].relatedModules`),
  };
}

function optionalRelatedModules(value: unknown, path: string): RoadmapRelatedModule[] {
  if (value === undefined || value === null) {
    return [];
  }

  if (!Array.isArray(value)) {
    throw new Error(`Invalid Roadmap value at ${path}: expected an array.`);
  }

  return value.map((item, index) => {
    if (typeof item === 'string') {
      const routeId = item.trim();
      if (!routeId) {
        throw new Error(`Invalid Roadmap value at ${path}[${index}]: routeId must not be blank.`);
      }

      return { routeId };
    }

    if (!isRecord(item)) {
      throw new Error(`Invalid Roadmap value at ${path}[${index}]: expected a string or object.`);
    }

    return {
      routeId: requireString(item.routeId, `${path}[${index}].routeId`),
      label: optionalString(item.label),
      note: optionalString(item.note),
    };
  });
}

function optionalStringArray(value: unknown, path: string): string[] {
  if (value === undefined || value === null) {
    return [];
  }

  if (!Array.isArray(value)) {
    throw new Error(`Invalid Roadmap value at ${path}: expected an array.`);
  }

  return value.map((item, index) => requireString(item, `${path}[${index}]`));
}

function requireString(value: unknown, path: string): string {
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(`Invalid Roadmap value at ${path}: expected a non-blank string.`);
  }

  return value.trim();
}

function optionalString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
