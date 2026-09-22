import { useCallback, useEffect, useMemo, useState } from 'react';
import DOMPurify from 'dompurify';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { loadApiDocs } from '../data/apiDocs';
import { collectKnowledgeSections, formatKnowledgeDisplayTitle } from '../data/knowledge';
import { loadQuizDocument } from '../data/quiz';
import { useLanguage } from '../state/LanguageContext';
import type {
  ApiDocController,
  ApiDocOperation,
  ApiDocsDocument,
  KnowledgeIndex,
  KnowledgeSectionRef,
  QuizAnswer,
  QuizAnswerId,
  QuizQuestion,
} from '../types/learning';
import { CustomTooltip } from './CustomTooltip';

interface QuizPanelProps {
  path?: string;
  knowledgeIndex: KnowledgeIndex | null;
  apiBasePath?: string;
}

interface ShuffledQuestion extends Omit<QuizQuestion, 'answers'> {
  answers: QuizAnswer[];
}

interface ResolvedApiRelated {
  controller: ApiDocController;
  operation: ApiDocOperation;
}

type RelatedPanelType = 'knowledge' | 'api';

const displayLabels = ['A', 'B', 'C', 'D'];

export function QuizPanel({ path, knowledgeIndex, apiBasePath }: QuizPanelProps) {
  const { language } = useLanguage();
  const [questions, setQuestions] = useState<ShuffledQuestion[]>([]);
  const [selectedAnswers, setSelectedAnswers] = useState<Record<string, QuizAnswerId>>({});
  const [relatedPanelByQuestion, setRelatedPanelByQuestion] = useState<Record<string, RelatedPanelType>>({});
  const [knowledgeContentCache, setKnowledgeContentCache] = useState<Record<string, string>>({});
  const [knowledgeLoadingPaths, setKnowledgeLoadingPaths] = useState<Set<string>>(() => new Set());
  const [knowledgeErrors, setKnowledgeErrors] = useState<Record<string, string>>({});
  const [apiDocument, setApiDocument] = useState<ApiDocsDocument | null>(null);
  const [loading, setLoading] = useState(Boolean(path));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    setKnowledgeContentCache({});
    setKnowledgeLoadingPaths(new Set());
    setKnowledgeErrors({});
    setError(null);

    if (!path) {
      setQuestions([]);
      setLoading(false);
      return () => {
        active = false;
      };
    }

    setLoading(true);

    loadQuizDocument(path)
      .then((document) => {
        if (!active) {
          return;
        }

        setQuestions((current) => {
          const previousAnswerOrder = new Map(
            current.map((question) => [question.id, question.answers.map((answer) => answer.id)]),
          );

          return document.questions.map((question) => ({
            ...question,
            answers: orderAnswers(
              question.answers,
              previousAnswerOrder.get(question.id),
            ),
          }));
        });

        const validQuestions = new Map(
          document.questions.map((question) => [
            question.id,
            new Set(question.answers.map((answer) => answer.id)),
          ]),
        );
        setSelectedAnswers((current) => Object.fromEntries(
          Object.entries(current).filter(([questionId, answerId]) => (
            validQuestions.get(questionId)?.has(answerId) ?? false
          )),
        ));
        setRelatedPanelByQuestion((current) => Object.fromEntries(
          Object.entries(current).filter(([questionId]) => validQuestions.has(questionId)),
        ));
        setLoading(false);
      })
      .catch((caught: unknown) => {
        if (!active) {
          return;
        }

        setQuestions([]);
        setError(caught instanceof Error ? caught.message : 'Unable to load Quiz.');
        setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [path]);

  useEffect(() => {
    let active = true;

    setApiDocument(null);

    if (!apiBasePath) {
      return () => {
        active = false;
      };
    }

    loadApiDocs(apiBasePath, knowledgeIndex)
      .then((document) => {
        if (active) {
          setApiDocument(document);
        }
      })
      .catch(() => {
        if (active) {
          setApiDocument(null);
        }
      });

    return () => {
      active = false;
    };
  }, [apiBasePath, knowledgeIndex]);

  const knowledgeByKey = useMemo(() => {
    if (!knowledgeIndex) {
      return new Map<string, KnowledgeSectionRef>();
    }

    return new Map(
      collectKnowledgeSections(knowledgeIndex.tree).map((section) => [
        `${section.sourcePath}#${section.id}`,
        section,
      ]),
    );
  }, [knowledgeIndex]);

  const apiByKey = useMemo(() => {
    const result = new Map<string, ResolvedApiRelated>();

    apiDocument?.controllers.forEach((controller) => {
      controller.operations.forEach((operation) => {
        result.set(`${controller.name}#${operation.methodSignature}`, { controller, operation });
      });
    });

    return result;
  }, [apiDocument]);

  const ensureKnowledgeContent = useCallback((section: KnowledgeSectionRef) => {
    if (
      knowledgeContentCache[section.content] !== undefined
      || knowledgeLoadingPaths.has(section.content)
    ) {
      return;
    }

    setKnowledgeErrors((current) => {
      if (current[section.content] === undefined) {
        return current;
      }

      const next = { ...current };
      delete next[section.content];
      return next;
    });
    setKnowledgeLoadingPaths((current) => new Set(current).add(section.content));

    fetch(section.content, { cache: 'no-cache' })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Unable to load Knowledge: ${response.status} ${response.statusText}`);
        }

        return response.text();
      })
      .then((markdown) => {
        setKnowledgeContentCache((current) => ({ ...current, [section.content]: markdown }));
      })
      .catch((caught: unknown) => {
        setKnowledgeErrors((current) => ({
          ...current,
          [section.content]: caught instanceof Error ? caught.message : 'Unable to load Knowledge.',
        }));
      })
      .finally(() => {
        setKnowledgeLoadingPaths((current) => {
          const next = new Set(current);
          next.delete(section.content);
          return next;
        });
      });
  }, [knowledgeContentCache, knowledgeLoadingPaths]);

  useEffect(() => {
    questions.forEach((question) => {
      if (relatedPanelByQuestion[question.id] !== 'knowledge') {
        return;
      }

      const knowledgeKey = question.readmeRelated.file && question.readmeRelated.anchor
        ? `${question.readmeRelated.file}#${question.readmeRelated.anchor}`
        : '';
      const relatedKnowledge = knowledgeKey ? knowledgeByKey.get(knowledgeKey) : undefined;

      if (relatedKnowledge) {
        ensureKnowledgeContent(relatedKnowledge);
      }
    });
  }, [ensureKnowledgeContent, knowledgeByKey, questions, relatedPanelByQuestion]);

  const openRelatedKnowledge = (questionId: string, section: KnowledgeSectionRef) => {
    setRelatedPanelByQuestion((current) => ({ ...current, [questionId]: 'knowledge' }));
    ensureKnowledgeContent(section);
  };

  const openRelatedApi = (questionId: string) => {
    setRelatedPanelByQuestion((current) => ({ ...current, [questionId]: 'api' }));
  };

  const closeRelatedPanel = (questionId: string) => {
    setRelatedPanelByQuestion((current) => {
      if (current[questionId] === undefined) {
        return current;
      }

      const next = { ...current };
      delete next[questionId];
      return next;
    });
  };

  if (loading) {
    return (
      <div className="empty-state">
        <strong>{language === 'vi' ? 'Đang tải Quiz...' : 'Loading Quiz...'}</strong>
      </div>
    );
  }

  if (error) {
    return (
      <div className="empty-state">
        <strong>{language === 'vi' ? 'Không thể tải Quiz' : 'Unable to load Quiz'}</strong>
        <span>{error}</span>
      </div>
    );
  }

  if (questions.length === 0) {
    return <EmptyQuiz />;
  }

  return (
    <section className="quiz-grid">
      {questions.map((question, index) => {
        const selectedId = selectedAnswers[question.id];
        const selectedAnswer = question.answers.find((answer) => answer.id === selectedId);
        const selectedIsCorrect = selectedId === question.correctAnswerId;
        const knowledgeKey = question.readmeRelated.file && question.readmeRelated.anchor
          ? `${question.readmeRelated.file}#${question.readmeRelated.anchor}`
          : '';
        const relatedKnowledge = knowledgeKey ? knowledgeByKey.get(knowledgeKey) : undefined;
        const apiKey = question.apiRelated.controller && question.apiRelated.methodSignature
          ? `${question.apiRelated.controller}#${question.apiRelated.methodSignature}`
          : '';
        const relatedApi = apiKey ? apiByKey.get(apiKey) : undefined;
        const activeRelatedPanel = relatedPanelByQuestion[question.id];
        const knowledgeMarkdown = relatedKnowledge
          ? knowledgeContentCache[relatedKnowledge.content]
          : undefined;
        const knowledgeLoading = relatedKnowledge
          ? knowledgeLoadingPaths.has(relatedKnowledge.content)
          : false;
        const knowledgeError = relatedKnowledge
          ? knowledgeErrors[relatedKnowledge.content]
          : undefined;
        const activeRelatedKnowledge = activeRelatedPanel === 'knowledge' ? relatedKnowledge : undefined;
        const activeRelatedApi = activeRelatedPanel === 'api' ? relatedApi : undefined;
        const sanitizedApiExecution = relatedApi?.operation.executionHtml
          ? DOMPurify.sanitize(relatedApi.operation.executionHtml)
          : '';

        return (
          <article key={question.id} className="quiz-card">
            <div className="quiz-card__topline">
              <span className="quiz-card__number">
                {language === 'vi' ? 'Câu' : 'Question'} {index + 1}
              </span>
              <span className="quiz-card__metadata">
                {question.aiGenerated && (
                  <CustomTooltip
                    content={language === 'vi'
                      ? 'Nội dung này do AI Sinh ra và có thể có sai sót'
                      : 'This content was generated by AI and may contain errors.'}
                  >
                    <span className="quiz-card__governance quiz-card__governance--ai">
                      AI Generated
                    </span>
                  </CustomTooltip>
                )}
                <CustomTooltip
                  content={question.reviewed
                    ? (language === 'vi'
                      ? 'Nội dung này đã được kiểm tra và sửa chữa'
                      : 'This content has been reviewed and corrected.')
                    : (language === 'vi'
                      ? 'Nội dung này chưa được kiểm tra và sửa chữa'
                      : 'This content has not been reviewed and corrected.')}
                >
                  <span className={`quiz-card__governance quiz-card__governance--${question.reviewed ? 'reviewed' : 'not-reviewed'}`}>
                    {question.reviewed
                      ? (language === 'vi' ? 'Đã review' : 'Reviewed')
                      : (language === 'vi' ? 'Chưa review' : 'Not Reviewed')}
                  </span>
                </CustomTooltip>
              </span>
            </div>
            <h3>{question.question}</h3>

            <div className="quiz-card__answers">
              {question.answers.map((answer, answerIndex) => {
                const isSelected = selectedId === answer.id;
                const isCorrect = answer.id === question.correctAnswerId;
                const stateClass = selectedId
                  ? isCorrect
                    ? ' is-correct'
                    : isSelected
                      ? ' is-incorrect'
                      : ''
                  : '';

                return (
                  <button
                    key={`${question.id}-${answer.id}`}
                    type="button"
                    className={`quiz-answer${isSelected ? ' is-selected' : ''}${stateClass}`}
                    onClick={() => {
                      setSelectedAnswers((current) => ({
                        ...current,
                        [question.id]: answer.id,
                      }));
                    }}
                  >
                    <span>{displayLabels[answerIndex]}</span>
                    <span className="quiz-answer__text">{answer.answer}</span>
                  </button>
                );
              })}
            </div>

            {selectedAnswer && (
              <>
                <div className={`quiz-explanation ${selectedIsCorrect ? 'is-correct' : 'is-incorrect'}`}>
                  <div className="quiz-explanation__heading">
                    <strong>
                      {selectedIsCorrect
                        ? language === 'vi' ? 'Chính xác' : 'Correct'
                        : language === 'vi' ? 'Chưa chính xác' : 'Incorrect'}
                    </strong>
                  </div>
                  <p>{selectedAnswer.explanation}</p>

                  {selectedIsCorrect && (relatedKnowledge || relatedApi) && (
                    <div className="quiz-related-actions">
                      {relatedKnowledge && (
                        <button
                          type="button"
                          className={`quiz-related-action quiz-related-action--knowledge${activeRelatedPanel === 'knowledge' ? ' is-active' : ''}`}
                          onClick={() => openRelatedKnowledge(question.id, relatedKnowledge)}
                        >
                          Related Knowledge
                        </button>
                      )}
                      {relatedApi && (
                        <button
                          type="button"
                          className={`quiz-related-action quiz-related-action--api${activeRelatedPanel === 'api' ? ' is-active' : ''}`}
                          onClick={() => openRelatedApi(question.id)}
                        >
                          Related API
                        </button>
                      )}
                    </div>
                  )}
                </div>

                {selectedIsCorrect && (activeRelatedKnowledge || activeRelatedApi) && (
                  <div className="quiz-related-panel">
                    <button
                      type="button"
                      className="quiz-related-panel__close"
                      aria-label={language === 'vi' ? 'Đóng nội dung liên quan' : 'Close related content'}
                      title={language === 'vi' ? 'Đóng' : 'Close'}
                      onClick={() => closeRelatedPanel(question.id)}
                    >
                      ×
                    </button>
                    {activeRelatedKnowledge ? (
                      <>
                        <div className="quiz-related-panel__header">
                          <div>
                            <span>{language === 'vi' ? 'Kiến thức liên quan' : 'Related Knowledge'}</span>
                            <strong>{formatKnowledgeDisplayTitle(activeRelatedKnowledge.title)}</strong>
                            <small>{activeRelatedKnowledge.categoryTitle}</small>
                          </div>
                          <div className="quiz-related-panel__metadata">
                            {activeRelatedKnowledge.aiGenerated && (
                              <span className="quiz-card__governance quiz-card__governance--ai">AI Generated</span>
                            )}
                            <span className={`quiz-card__governance quiz-card__governance--${activeRelatedKnowledge.reviewed ? 'reviewed' : 'not-reviewed'}`}>
                              {activeRelatedKnowledge.reviewed
                                ? (language === 'vi' ? 'Đã review' : 'Reviewed')
                                : (language === 'vi' ? 'Chưa review' : 'Not Reviewed')}
                            </span>
                          </div>
                        </div>

                        <div className="quiz-related-panel__content markdown-content">
                          {knowledgeLoading && (
                            <div className="knowledge-section-status">
                              {language === 'vi' ? 'Đang tải nội dung...' : 'Loading content...'}
                            </div>
                          )}
                          {!knowledgeLoading && knowledgeError && (
                            <div className="knowledge-section-status knowledge-section-status--error">
                              {knowledgeError}
                            </div>
                          )}
                          {!knowledgeLoading && !knowledgeError && knowledgeMarkdown !== undefined && (
                            <ReactMarkdown remarkPlugins={[remarkGfm]}>{knowledgeMarkdown}</ReactMarkdown>
                          )}
                        </div>
                      </>
                    ) : activeRelatedApi ? (
                      <>
                        <div className="quiz-related-panel__header quiz-related-panel__header--api">
                          <div>
                            <span>{language === 'vi' ? 'API liên quan' : 'Related API'}</span>
                            <strong>{activeRelatedApi.operation.summary}</strong>
                            <small>{activeRelatedApi.controller.title} · {activeRelatedApi.operation.methodSignature}</small>
                          </div>
                          <div className="quiz-related-panel__api-route">
                            <span className={`http-method http-method--${activeRelatedApi.operation.httpMethod.toLowerCase()}`}>
                              {activeRelatedApi.operation.httpMethod}
                            </span>
                            <code>{activeRelatedApi.operation.path || '—'}</code>
                          </div>
                        </div>

                        <div className="quiz-related-panel__content">
                          {activeRelatedApi.operation.description && <p>{activeRelatedApi.operation.description}</p>}

                          <section>
                            <h4>Execution</h4>
                            {sanitizedApiExecution ? (
                              <div
                                className="api-execution-content"
                                dangerouslySetInnerHTML={{ __html: sanitizedApiExecution }}
                              />
                            ) : (
                              <p className="api-operation__empty-detail">
                                {language === 'vi' ? 'Chưa có mô tả execution.' : 'No execution description yet.'}
                              </p>
                            )}
                          </section>

                          {activeRelatedApi.operation.params.length > 0 && (
                            <section>
                              <h4>{language === 'vi' ? 'Tham số' : 'Parameters'}</h4>
                              <div className="api-param-list">
                                {activeRelatedApi.operation.params.map((param) => (
                                  <div key={param.name} className="api-param">
                                    <code>{param.name}</code>
                                    <span>
                                      <strong>{param.summary || param.name}</strong>
                                      {param.description && <small>{param.description}</small>}
                                    </span>
                                  </div>
                                ))}
                              </div>
                            </section>
                          )}
                        </div>
                      </>
                    ) : null}
                  </div>
                )}
              </>
            )}
          </article>
        );
      })}
    </section>
  );
}

function EmptyQuiz() {
  const { language } = useLanguage();

  return (
    <div className="empty-state">
      <strong>{language === 'vi' ? 'Quiz chưa có dữ liệu' : 'No quiz data yet'}</strong>
      <span>
        {language === 'vi'
          ? 'Module này chưa có câu hỏi Quiz cho ngôn ngữ hiện tại.'
          : 'This module does not have Quiz questions for the current language yet.'}
      </span>
    </div>
  );
}

function shuffleAnswers(answers: QuizAnswer[]): QuizAnswer[] {
  const result = [...answers];

  for (let index = result.length - 1; index > 0; index -= 1) {
    const target = Math.floor(Math.random() * (index + 1));
    [result[index], result[target]] = [result[target], result[index]];
  }

  return result;
}

function orderAnswers(answers: QuizAnswer[], previousOrder?: QuizAnswerId[]): QuizAnswer[] {
  if (!previousOrder || previousOrder.length === 0) {
    return shuffleAnswers(answers);
  }

  const orderById = new Map(previousOrder.map((answerId, index) => [answerId, index]));
  return [...answers].sort((left, right) => (
    (orderById.get(left.id) ?? Number.MAX_SAFE_INTEGER)
    - (orderById.get(right.id) ?? Number.MAX_SAFE_INTEGER)
  ));
}
