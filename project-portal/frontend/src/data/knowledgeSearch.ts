import type {
  KnowledgeSearchDocument,
  KnowledgeSearchIndex,
  KnowledgeSearchResult,
  Language,
} from '../types/learning';

const searchIndexCache = new Map<Language, Promise<KnowledgeSearchIndex>>();

export async function loadKnowledgeSearchIndex(language: Language): Promise<KnowledgeSearchIndex> {
  const cached = searchIndexCache.get(language);

  if (cached) {
    return cached;
  }

  const request = fetch(`/search/knowledge-search.${language}.json`, { cache: 'no-cache' })
    .then((response) => {
      if (!response.ok) {
        throw new Error(`Unable to load Knowledge search index: ${response.status} ${response.statusText}`);
      }

      return response.json() as Promise<KnowledgeSearchIndex>;
    })
    .catch((error: unknown) => {
      searchIndexCache.delete(language);
      throw error;
    });

  searchIndexCache.set(language, request);
  return request;
}

export function normalizeKnowledgeSearchText(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function scoreDocument(
  document: KnowledgeSearchDocument,
  normalizedQuery: string,
  tokens: string[],
): number | null {
  if (!normalizedQuery || tokens.length === 0) {
    return null;
  }

  if (!tokens.every((token) => document.searchText.includes(token))) {
    return null;
  }

  const title = normalizeKnowledgeSearchText(document.title);
  const categoryTitle = normalizeKnowledgeSearchText(document.categoryTitle);
  const sectionId = normalizeKnowledgeSearchText(document.sectionId ?? '');
  const categoryId = normalizeKnowledgeSearchText(document.categoryId);

  let score = 0;

  if (title === normalizedQuery) {
    score += 240;
  } else if (title.startsWith(normalizedQuery)) {
    score += 170;
  } else if (title.includes(normalizedQuery)) {
    score += 130;
  }

  if (categoryTitle === normalizedQuery) {
    score += 120;
  } else if (categoryTitle.includes(normalizedQuery)) {
    score += 70;
  }

  if (sectionId === normalizedQuery || categoryId === normalizedQuery) {
    score += 80;
  }

  tokens.forEach((token) => {
    if (title.includes(token)) {
      score += 30;
    }

    if (categoryTitle.includes(token)) {
      score += 18;
    }
  });

  if (document.type === 'SECTION') {
    score += 8;
  }

  return score;
}

export function searchKnowledge(
  index: KnowledgeSearchIndex,
  query: string,
): KnowledgeSearchResult[] {
  const normalizedQuery = normalizeKnowledgeSearchText(query);
  const tokens = normalizedQuery.split(' ').filter(Boolean);

  if (!normalizedQuery || tokens.length === 0) {
    return [];
  }

  return index.documents
    .map((document) => {
      const score = scoreDocument(document, normalizedQuery, tokens);
      return score === null ? null : { document, score };
    })
    .filter((entry): entry is KnowledgeSearchResult => entry !== null)
    .sort((left, right) => (
      right.score - left.score
      || left.document.title.localeCompare(right.document.title)
      || left.document.documentId.localeCompare(right.document.documentId)
    ));
}
