import { parse } from 'yaml';
import type {
  QuizAnswer,
  QuizAnswerId,
  QuizDocument,
  QuizQuestion,
} from '../types/learning';

const answerIds: QuizAnswerId[] = ['A', 'B', 'C', 'D'];

export async function loadQuizDocument(path: string): Promise<QuizDocument> {
  const response = await fetch(path, { cache: 'no-cache' });

  if (!response.ok) {
    throw new Error(`Unable to load Quiz: ${response.status} ${response.statusText}`);
  }

  const parsed = parse(await response.text()) as unknown;
  return parseQuizDocument(parsed);
}

export async function loadQuizQuestionCount(path: string): Promise<number> {
  return (await loadQuizDocument(path)).questions.length;
}

function parseQuizDocument(value: unknown): QuizDocument {
  if (!isRecord(value) || !Array.isArray(value.questions)) {
    throw new Error('Invalid Quiz document: questions must be an array.');
  }

  const questions = value.questions.map((question, index) => parseQuestion(question, index));
  const ids = questions.map((question) => question.id);

  if (new Set(ids).size !== ids.length) {
    throw new Error('Invalid Quiz document: question ids must be unique.');
  }

  return { questions };
}

function parseQuestion(value: unknown, index: number): QuizQuestion {
  if (!isRecord(value)) {
    throw new Error(`Invalid Quiz question at index ${index}.`);
  }

  const id = asNonBlankString(value.id, `questions[${index}].id`);
  const question = asNonBlankString(value.question, `questions[${index}].question`);
  const aiGenerated = asBoolean(value.aiGenerated, `questions[${index}].aiGenerated`);
  const reviewed = asBoolean(value.reviewed, `questions[${index}].reviewed`);
  const readmeRelated = parseReadmeRelated(value.readmeRelated, `questions[${index}].readmeRelated`);
  const apiRelated = parseApiRelated(value.apiRelated, `questions[${index}].apiRelated`);

  if (!Array.isArray(value.answers) || value.answers.length !== 4) {
    throw new Error(`Invalid Quiz question '${id}': answers must contain exactly four items.`);
  }

  const answers = value.answers.map((answer, answerIndex) => parseAnswer(answer, id, answerIndex));
  const ids = answers.map((answer) => answer.id);

  if (new Set(ids).size !== 4 || answerIds.some((answerId) => !ids.includes(answerId))) {
    throw new Error(`Invalid Quiz question '${id}': answer ids must be unique A, B, C and D.`);
  }

  const correctAnswerId = asAnswerId(value.correctAnswerId, `${id}.correctAnswerId`);

  return {
    id,
    question,
    aiGenerated,
    reviewed,
    readmeRelated,
    apiRelated,
    answers,
    correctAnswerId,
  };
}

function parseReadmeRelated(value: unknown, path: string) {
  if (!isRecord(value)) {
    throw new Error(`Invalid Quiz value at ${path}: expected an object.`);
  }

  return {
    file: asString(value.file, `${path}.file`),
    anchor: asString(value.anchor, `${path}.anchor`),
  };
}

function parseApiRelated(value: unknown, path: string) {
  if (!isRecord(value)) {
    throw new Error(`Invalid Quiz value at ${path}: expected an object.`);
  }

  return {
    controller: asString(value.controller, `${path}.controller`),
    methodSignature: asString(value.methodSignature, `${path}.methodSignature`),
  };
}

function parseAnswer(value: unknown, questionId: string, index: number): QuizAnswer {
  if (!isRecord(value)) {
    throw new Error(`Invalid Quiz answer at ${questionId}.answers[${index}].`);
  }

  return {
    id: asAnswerId(value.id, `${questionId}.answers[${index}].id`),
    answer: asNonBlankString(value.answer, `${questionId}.answers[${index}].answer`),
    explanation: asNonBlankString(value.explanation, `${questionId}.answers[${index}].explanation`),
  };
}

function asAnswerId(value: unknown, path: string): QuizAnswerId {
  if (typeof value !== 'string' || !answerIds.includes(value as QuizAnswerId)) {
    throw new Error(`Invalid Quiz value at ${path}: expected A, B, C or D.`);
  }

  return value as QuizAnswerId;
}

function asNonBlankString(value: unknown, path: string): string {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`Invalid Quiz value at ${path}: expected a non-blank string.`);
  }

  return value.trim();
}

function asString(value: unknown, path: string): string {
  if (typeof value !== 'string') {
    throw new Error(`Invalid Quiz value at ${path}: expected a string.`);
  }

  return value.trim();
}

function asBoolean(value: unknown, path: string): boolean {
  if (typeof value !== 'boolean') {
    throw new Error(`Invalid Quiz value at ${path}: expected a boolean.`);
  }

  return value;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
