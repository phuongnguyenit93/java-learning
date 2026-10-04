import { parse } from 'yaml';
import type { ReferenceDocument, ReferenceItem } from '../types/learning';

const referenceCache = new Map<string, Promise<ReferenceDocument>>();

export async function loadReferenceDocument(path: string): Promise<ReferenceDocument> {
  const cached = referenceCache.get(path);

  if (cached) {
    return cached;
  }

  const request = fetch(path, { cache: 'no-cache' })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`Unable to load Reference: ${response.status} ${response.statusText}`);
      }

      return parseReferenceDocument(parse(await response.text()));
    })
    .catch((error) => {
      referenceCache.delete(path);
      throw error;
    });

  referenceCache.set(path, request);
  return request;
}

function parseReferenceDocument(value: unknown): ReferenceDocument {
  if (!isRecord(value) || !Array.isArray(value.references)) {
    throw new Error('Invalid Reference document: references must be an array.');
  }

  return {
    references: value.references.map((item, index) => parseReference(item, index)),
  };
}

function parseReference(value: unknown, index: number): ReferenceItem {
  if (!isRecord(value)) {
    throw new Error(`Invalid Reference entry at index ${index}.`);
  }

  return {
    title: requireString(value.title, `references[${index}].title`),
    url: requireString(value.url, `references[${index}].url`),
    description: optionalString(value.description, `references[${index}].description`),
  };
}

function requireString(value: unknown, path: string): string {
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(`Invalid Reference value at ${path}: expected a non-blank string.`);
  }

  return value.trim();
}

function optionalString(value: unknown, path: string): string {
  if (value === undefined || value === null) {
    return '';
  }

  if (typeof value !== 'string') {
    throw new Error(`Invalid Reference value at ${path}: expected a string.`);
  }

  return value.trim();
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
