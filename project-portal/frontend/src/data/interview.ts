import { parse } from 'yaml';
import type { InterviewDocument, InterviewQuestion } from '../types/learning';

export async function loadInterviewDocument(path: string): Promise<InterviewDocument> {
  const response = await fetch(path, { cache: 'no-cache' });

  if (!response.ok) {
    throw new Error(`Unable to load Interview: ${response.status} ${response.statusText}`);
  }

  const parsed = parse(await response.text()) as unknown;
  return parseInterviewDocument(parsed);
}

export async function loadInterviewQuestionCount(path: string): Promise<number> {
  return (await loadInterviewDocument(path)).questions.length;
}

function parseInterviewDocument(value: unknown): InterviewDocument {
  if (!isRecord(value) || !Array.isArray(value.questions)) {
    throw new Error('Invalid Interview document: questions must be an array.');
  }

  return {
    questions: value.questions.map((question, index) => parseQuestion(question, index)),
  };
}

function parseQuestion(value: unknown, index: number): InterviewQuestion {
  if (!isRecord(value)) {
    throw new Error(`Invalid Interview question at index ${index}.`);
  }

  return {
    question: asNonBlankString(value.question, `questions[${index}].question`),
    answer: asNonBlankString(value.answer, `questions[${index}].answer`),
    aiGenerated: asBoolean(value.aiGenerated, `questions[${index}].aiGenerated`),
    reviewed: asBoolean(value.reviewed, `questions[${index}].reviewed`),
    readmeRelated: parseReadmeRelated(value.readmeRelated, `questions[${index}].readmeRelated`),
    apiRelated: parseApiRelated(value.apiRelated, `questions[${index}].apiRelated`),
  };
}

function parseReadmeRelated(value: unknown, path: string) {
  if (!isRecord(value)) {
    throw new Error(`Invalid Interview value at ${path}: expected an object.`);
  }

  return {
    file: asString(value.file, `${path}.file`),
    anchor: asString(value.anchor, `${path}.anchor`),
  };
}

function parseApiRelated(value: unknown, path: string) {
  if (!isRecord(value)) {
    throw new Error(`Invalid Interview value at ${path}: expected an object.`);
  }

  return {
    controller: asString(value.controller, `${path}.controller`),
    methodSignature: asString(value.methodSignature, `${path}.methodSignature`),
  };
}

function asNonBlankString(value: unknown, path: string): string {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`Invalid Interview value at ${path}: expected a non-blank string.`);
  }

  return value.trim();
}

function asString(value: unknown, path: string): string {
  if (typeof value !== 'string') {
    throw new Error(`Invalid Interview value at ${path}: expected a string.`);
  }

  return value.trim();
}

function asBoolean(value: unknown, path: string): boolean {
  if (typeof value !== 'boolean') {
    throw new Error(`Invalid Interview value at ${path}: expected a boolean.`);
  }

  return value;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
