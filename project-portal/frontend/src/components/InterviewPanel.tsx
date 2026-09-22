import { useEffect, useMemo, useState } from 'react';
import DOMPurify from 'dompurify';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { loadApiDocs } from '../data/apiDocs';
import { loadInterviewDocument } from '../data/interview';
import { collectKnowledgeSections, formatKnowledgeDisplayTitle } from '../data/knowledge';
import { useLanguage } from '../state/LanguageContext';
import type {
  ApiDocController,
  ApiDocOperation,
  ApiDocsDocument,
  InterviewQuestion,
  KnowledgeIndex,
  KnowledgeSectionRef,
} from '../types/learning';
import { CustomTooltip } from './CustomTooltip';

interface InterviewPanelProps {
  path?: string;
  knowledgeIndex: KnowledgeIndex | null;
  apiBasePath?: string;
}

interface ResolvedApiRelated {
  controller: ApiDocController;
  operation: ApiDocOperation;
}

type RelatedPanelType = 'knowledge' | 'api';

export function InterviewPanel({ path, knowledgeIndex, apiBasePath }: InterviewPanelProps) {
  const { language } = useLanguage();
  const [questions, setQuestions] = useState<InterviewQuestion[]>([]);
  const [expandedAnswers, setExpandedAnswers] = useState<Set<number>>(() => new Set());
  const [relatedPanelByQuestion, setRelatedPanelByQuestion] = useState<Record<string, RelatedPanelType>>({});
  const [knowledgeContentCache, setKnowledgeContentCache] = useState<Record<string, string>>({});
  const [knowledgeLoadingPaths, setKnowledgeLoadingPaths] = useState<Set<string>>(() => new Set());
  const [knowledgeErrors, setKnowledgeErrors] = useState<Record<string, string>>({});
  const [apiDocument, setApiDocument] = useState<ApiDocsDocument | null>(null);
  const [loading, setLoading] = useState(Boolean(path));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    setExpandedAnswers(new Set());
    setRelatedPanelByQuestion({});
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

    loadInterviewDocument(path)
      .then((document) => {
        if (!active) {
          return;
        }

        setQuestions(document.questions);
        setLoading(false);
      })
      .catch((caught: unknown) => {
        if (!active) {
          return;
        }

        setQuestions([]);
        setError(caught instanceof Error ? caught.message : 'Unable to load Interview.');
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

  const openRelatedKnowledge = (questionKey: string, section: KnowledgeSectionRef) => {
    setRelatedPanelByQuestion((current) => ({ ...current, [questionKey]: 'knowledge' }));

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
  };

  const openRelatedApi = (questionKey: string) => {
    setRelatedPanelByQuestion((current) => ({ ...current, [questionKey]: 'api' }));
  };

  const closeRelatedPanel = (questionKey: string) => {
    setRelatedPanelByQuestion((current) => {
      if (current[questionKey] === undefined) {
        return current;
      }

      const next = { ...current };
      delete next[questionKey];
      return next;
    });
  };

  if (loading) {
    return (
      <div className="empty-state">
        <strong>{language === 'vi' ? 'Đang tải Interview...' : 'Loading Interview...'}</strong>
      </div>
    );
  }

  if (error) {
    return (
      <div className="empty-state">
        <strong>{language === 'vi' ? 'Không thể tải Interview' : 'Unable to load Interview'}</strong>
        <span>{error}</span>
      </div>
    );
  }

  if (questions.length === 0) {
    return (
      <div className="empty-state empty-state--large">
        <span className="empty-state__icon" aria-hidden="true">?</span>
        <strong>{language === 'vi' ? 'Interview chưa có nội dung' : 'Interview content is not available yet'}</strong>
        <span>
          {language === 'vi'
            ? 'Module này chưa có câu hỏi Interview cho ngôn ngữ hiện tại.'
            : 'This module does not have Interview questions for the current language yet.'}
        </span>
      </div>
    );
  }

  return (
    <section className="interview-list">
      {questions.map((question, index) => {
        const expanded = expandedAnswers.has(index);
        const questionKey = `${index}:${question.question}`;
        const knowledgeKey = question.readmeRelated.file && question.readmeRelated.anchor
          ? `${question.readmeRelated.file}#${question.readmeRelated.anchor}`
          : '';
        const relatedKnowledge = knowledgeKey ? knowledgeByKey.get(knowledgeKey) : undefined;
        const apiKey = question.apiRelated.controller && question.apiRelated.methodSignature
          ? `${question.apiRelated.controller}#${question.apiRelated.methodSignature}`
          : '';
        const relatedApi = apiKey ? apiByKey.get(apiKey) : undefined;
        const activeRelatedPanel = relatedPanelByQuestion[questionKey];
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
          <article key={`${index}-${question.question}`} className="interview-card">
            <div className="interview-card__topline">
              <span className="interview-card__number">
                {language === 'vi' ? 'Câu' : 'Question'} {index + 1}
              </span>

              <span className="interview-card__metadata">
                {question.aiGenerated && (
                  <CustomTooltip
                    content={language === 'vi'
                      ? 'Nội dung này do AI sinh ra và có thể có sai sót.'
                      : 'This content was generated by AI and may contain errors.'}
                  >
                    <span className="interview-card__governance interview-card__governance--ai">
                      AI Generated
                    </span>
                  </CustomTooltip>
                )}

                <CustomTooltip
                  content={question.reviewed
                    ? (language === 'vi'
                      ? 'Nội dung này đã được kiểm tra và sửa chữa.'
                      : 'This content has been reviewed and corrected.')
                    : (language === 'vi'
                      ? 'Nội dung này chưa được kiểm tra và sửa chữa.'
                      : 'This content has not been reviewed and corrected.')}
                >
                  <span className={`interview-card__governance interview-card__governance--${question.reviewed ? 'reviewed' : 'not-reviewed'}`}>
                    {question.reviewed
                      ? (language === 'vi' ? 'Đã review' : 'Reviewed')
                      : (language === 'vi' ? 'Chưa review' : 'Not Reviewed')}
                  </span>
                </CustomTooltip>
              </span>
            </div>

            <h3>{question.question}</h3>

            <button
              type="button"
              className={`interview-answer-toggle${expanded ? ' is-open' : ''}`}
              aria-expanded={expanded}
              onClick={() => {
                setExpandedAnswers((current) => {
                  const next = new Set(current);
                  if (next.has(index)) {
                    next.delete(index);
                  } else {
                    next.add(index);
                  }
                  return next;
                });
              }}
            >
              <span>{language === 'vi' ? 'Câu trả lời tham khảo' : 'Reference Answer'}</span>
              <span aria-hidden="true">{expanded ? '−' : '+'}</span>
            </button>

            {expanded && (
              <>
                <div className="interview-answer">
                  <p>{question.answer}</p>

                  {(relatedKnowledge || relatedApi) && (
                    <div className="quiz-related-actions">
                      {relatedKnowledge && (
                        <button
                          type="button"
                          className={`quiz-related-action quiz-related-action--knowledge${activeRelatedPanel === 'knowledge' ? ' is-active' : ''}`}
                          onClick={() => openRelatedKnowledge(questionKey, relatedKnowledge)}
                        >
                          Related Knowledge
                        </button>
                      )}
                      {relatedApi && (
                        <button
                          type="button"
                          className={`quiz-related-action quiz-related-action--api${activeRelatedPanel === 'api' ? ' is-active' : ''}`}
                          onClick={() => openRelatedApi(questionKey)}
                        >
                          Related API
                        </button>
                      )}
                    </div>
                  )}
                </div>

                {(activeRelatedKnowledge || activeRelatedApi) && (
                  <div className="quiz-related-panel">
                    <button
                      type="button"
                      className="quiz-related-panel__close"
                      aria-label={language === 'vi' ? 'Đóng nội dung liên quan' : 'Close related content'}
                      title={language === 'vi' ? 'Đóng' : 'Close'}
                      onClick={() => closeRelatedPanel(questionKey)}
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
                              <span className="interview-card__governance interview-card__governance--ai">AI Generated</span>
                            )}
                            <span className={`interview-card__governance interview-card__governance--${activeRelatedKnowledge.reviewed ? 'reviewed' : 'not-reviewed'}`}>
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
