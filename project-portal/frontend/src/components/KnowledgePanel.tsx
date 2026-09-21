import { useEffect, useMemo, useRef, useState } from 'react';
import DOMPurify from 'dompurify';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { collectApiKnowledgeRelations, loadApiDocs } from '../data/apiDocs';
import {
  collectKnowledgeCategories,
  collectKnowledgeSections,
  formatKnowledgeDisplayTitle,
} from '../data/knowledge';
import { useLanguage } from '../state/LanguageContext';
import type {
  ApiDocsDocument,
  ApiKnowledgeRelation,
  KnowledgeDifficulty,
  KnowledgeIndex,
} from '../types/learning';
import { CustomTooltip } from './CustomTooltip';

function resolveDifficultyLabel(difficulty: KnowledgeDifficulty, language: 'vi' | 'en'): string {
  if (difficulty === 'INTERMEDIATE') {
    return language === 'vi' ? 'TRUNG BÌNH' : 'INTERMEDIATE';
  }

  if (difficulty === 'ADVANCED') {
    return language === 'vi' ? 'NÂNG CAO' : 'ADVANCED';
  }

  return language === 'vi' ? 'CƠ BẢN' : 'BASIC';
}

interface KnowledgePanelProps {
  index: KnowledgeIndex | null;
  loading: boolean;
  error: string | null;
  searchQuery: string;
  activeCategoryId: string;
  selectedSectionId: string | null;
  apiBasePath?: string;
  onCategoryChange: (categoryId: string) => void;
  onSectionChange: (sectionId: string | null) => void;
}

export function KnowledgePanel({
  index,
  loading,
  error,
  searchQuery,
  activeCategoryId,
  selectedSectionId,
  apiBasePath,
  onCategoryChange,
  onSectionChange,
}: KnowledgePanelProps) {
  const { language } = useLanguage();
  const [contentCache, setContentCache] = useState<Record<string, string>>({});
  const [expandedSectionIds, setExpandedSectionIds] = useState<Set<string>>(() => new Set());
  const [loadingSectionIds, setLoadingSectionIds] = useState<Set<string>>(() => new Set());
  const [sectionErrors, setSectionErrors] = useState<Record<string, string>>({});
  const [apiDocument, setApiDocument] = useState<ApiDocsDocument | null>(null);
  const [previewOperationId, setPreviewOperationId] = useState<string | null>(null);
  const [canScrollTopicsLeft, setCanScrollTopicsLeft] = useState(false);
  const [canScrollTopicsRight, setCanScrollTopicsRight] = useState(false);
  const [draggingTopics, setDraggingTopics] = useState(false);
  const topicStripRef = useRef<HTMLDivElement>(null);
  const topicButtonRefs = useRef<Record<string, HTMLButtonElement | null>>({});
  const topicDragRef = useRef({
    pointerId: -1,
    startX: 0,
    startScrollLeft: 0,
    dragged: false,
    active: false,
  });
  const suppressTopicClickRef = useRef(false);

  const categories = useMemo(() => (index ? collectKnowledgeCategories(index.tree) : []), [index]);
  const allSections = useMemo(() => (index ? collectKnowledgeSections(index.tree) : []), [index]);

  const visibleSections = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();

    return allSections.filter((section) => {
      if (activeCategoryId !== 'all' && section.categoryId !== activeCategoryId) {
        return false;
      }

      if (!query) {
        return true;
      }

      return `${section.title} ${section.id} ${section.categoryTitle}`.toLowerCase().includes(query);
    });
  }, [activeCategoryId, allSections, searchQuery]);

  const relatedApisByKnowledgeKey = useMemo(() => {
    const result = new Map<string, ApiKnowledgeRelation[]>();

    if (!apiDocument) {
      return result;
    }

    collectApiKnowledgeRelations(apiDocument).forEach((relation) => {
      const key = `${relation.sourcePath}#${relation.anchor}`;
      const current = result.get(key) ?? [];
      current.push(relation);
      result.set(key, current);
    });

    return result;
  }, [apiDocument]);

  useEffect(() => {
    setContentCache({});
    setExpandedSectionIds(new Set());
    setLoadingSectionIds(new Set());
    setSectionErrors({});
  }, [index?.moduleId, index?.language]);

  useEffect(() => {
    let active = true;

    setApiDocument(null);
    setPreviewOperationId(null);

    if (!apiBasePath || !index) {
      return () => {
        active = false;
      };
    }

    loadApiDocs(apiBasePath, index)
      .then((result) => {
        if (active) {
          setApiDocument(result);
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
  }, [apiBasePath, index]);

  useEffect(() => {
    if (!previewOperationId) {
      return undefined;
    }

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target;
      if (target instanceof Element && target.closest('[data-related-api-control]')) {
        return;
      }
      setPreviewOperationId(null);
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setPreviewOperationId(null);
      }
    };

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [previewOperationId]);

  useEffect(() => {
    if (!selectedSectionId) {
      return;
    }

    setExpandedSectionIds((current) => {
      if (current.has(selectedSectionId)) {
        return current;
      }

      const next = new Set(current);
      next.add(selectedSectionId);
      return next;
    });
  }, [selectedSectionId]);

  useEffect(() => {
    const sectionsToLoad = allSections.filter((section) => (
      expandedSectionIds.has(section.id)
      && contentCache[section.content] === undefined
      && !loadingSectionIds.has(section.id)
      && sectionErrors[section.id] === undefined
    ));

    sectionsToLoad.forEach((section) => {
      setLoadingSectionIds((current) => {
        const next = new Set(current);
        next.add(section.id);
        return next;
      });

      fetch(section.content, { cache: 'no-cache' })
        .then((response) => {
          if (!response.ok) {
            throw new Error(`Unable to load section: ${response.status} ${response.statusText}`);
          }

          return response.text();
        })
        .then((markdown) => {
          setContentCache((current) => ({ ...current, [section.content]: markdown }));
          setLoadingSectionIds((current) => {
            const next = new Set(current);
            next.delete(section.id);
            return next;
          });
        })
        .catch((cause: unknown) => {
          setSectionErrors((current) => ({
            ...current,
            [section.id]: cause instanceof Error ? cause.message : 'Unable to load section.',
          }));
          setLoadingSectionIds((current) => {
            const next = new Set(current);
            next.delete(section.id);
            return next;
          });
        });
    });
  }, [allSections, contentCache, expandedSectionIds, loadingSectionIds, sectionErrors]);

  useEffect(() => {
    if (!selectedSectionId) {
      return;
    }

    const frame = window.requestAnimationFrame(() => {
      document
        .getElementById(`knowledge-section-${selectedSectionId}`)
        ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });

    return () => window.cancelAnimationFrame(frame);
  }, [selectedSectionId]);

  useEffect(() => {
    const strip = topicStripRef.current;

    if (!strip) {
      return;
    }

    const updateScrollState = () => {
      const remaining = strip.scrollWidth - strip.clientWidth - strip.scrollLeft;
      setCanScrollTopicsLeft(strip.scrollLeft > 2);
      setCanScrollTopicsRight(remaining > 2);
    };

    updateScrollState();
    strip.addEventListener('scroll', updateScrollState, { passive: true });

    const resizeObserver = new ResizeObserver(updateScrollState);
    resizeObserver.observe(strip);

    return () => {
      strip.removeEventListener('scroll', updateScrollState);
      resizeObserver.disconnect();
    };
  }, [categories.length, index?.language, index?.moduleId]);

  useEffect(() => {
    const key = activeCategoryId === 'all' ? 'all' : activeCategoryId;
    const activeButton = topicButtonRefs.current[key];

    if (!activeButton) {
      return;
    }

    activeButton.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
      inline: 'center',
    });
  }, [activeCategoryId, index?.language, index?.moduleId]);

  const scrollTopics = (direction: -1 | 1) => {
    const strip = topicStripRef.current;

    if (!strip) {
      return;
    }

    strip.scrollBy({
      left: direction * 180,
      behavior: 'smooth',
    });
  };

  const handleTopicPointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) {
      return;
    }

    const strip = topicStripRef.current;
    if (!strip) {
      return;
    }

    topicDragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startScrollLeft: strip.scrollLeft,
      dragged: false,
      active: true,
    };
  };

  const handleTopicPointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    const strip = topicStripRef.current;
    const drag = topicDragRef.current;

    if (!strip || !drag.active || drag.pointerId !== event.pointerId) {
      return;
    }

    const delta = event.clientX - drag.startX;
    if (!drag.dragged && Math.abs(delta) > 5) {
      drag.dragged = true;
      setDraggingTopics(true);

      if (!strip.hasPointerCapture(event.pointerId)) {
        strip.setPointerCapture(event.pointerId);
      }
    }

    if (!drag.dragged) {
      return;
    }

    event.preventDefault();
    strip.scrollLeft = drag.startScrollLeft - delta;
  };

  const finishTopicDrag = (event: React.PointerEvent<HTMLDivElement>) => {
    const strip = topicStripRef.current;
    const drag = topicDragRef.current;

    if (!drag.active || drag.pointerId !== event.pointerId) {
      return;
    }

    suppressTopicClickRef.current = drag.dragged;
    window.setTimeout(() => {
      suppressTopicClickRef.current = false;
    }, 0);
    topicDragRef.current = {
      pointerId: -1,
      startX: 0,
      startScrollLeft: 0,
      dragged: false,
      active: false,
    };
    setDraggingTopics(false);

    if (strip?.hasPointerCapture(event.pointerId)) {
      strip.releasePointerCapture(event.pointerId);
    }
  };

  const toggleSection = (sectionId: string) => {
    const willExpand = !expandedSectionIds.has(sectionId);

    setExpandedSectionIds((current) => {
      const next = new Set(current);

      if (next.has(sectionId)) {
        next.delete(sectionId);
      } else {
        next.add(sectionId);
      }

      return next;
    });

    if (willExpand) {
      setSectionErrors((current) => {
        if (current[sectionId] === undefined) {
          return current;
        }

        const next = { ...current };
        delete next[sectionId];
        return next;
      });
      onSectionChange(sectionId);
    } else if (selectedSectionId === sectionId) {
      onSectionChange(null);
    }
  };

  if (loading) {
    return <div className="empty-state empty-state--large"><strong>{language === 'vi' ? 'Đang tải Knowledge...' : 'Loading Knowledge...'}</strong></div>;
  }

  if (error) {
    return (
      <div className="empty-state empty-state--large">
        <strong>{language === 'vi' ? 'Không thể tải Knowledge' : 'Unable to load Knowledge'}</strong>
        <span>{error}</span>
      </div>
    );
  }

  if (!index) {
    return (
      <div className="empty-state empty-state--large">
        <strong>{language === 'vi' ? 'Chưa có dữ liệu Knowledge' : 'No Knowledge data yet'}</strong>
      </div>
    );
  }

  return (
    <section className="knowledge-panel">
      <div className="knowledge-topic-nav">
        <button
          type="button"
          className="knowledge-topic-nav__scroll"
          onClick={() => scrollTopics(-1)}
          disabled={!canScrollTopicsLeft}
          aria-label={language === 'vi' ? 'Cuộn danh mục sang trái' : 'Scroll categories left'}
        >
          ‹
        </button>

        <div
          ref={topicStripRef}
          className={`knowledge-topic-strip${draggingTopics ? ' is-dragging' : ''}`}
          aria-label="Knowledge categories"
          onPointerDown={handleTopicPointerDown}
          onPointerMove={handleTopicPointerMove}
          onPointerUp={finishTopicDrag}
          onPointerCancel={finishTopicDrag}
          onClickCapture={(event) => {
            if (!suppressTopicClickRef.current) {
              return;
            }

            suppressTopicClickRef.current = false;
            event.preventDefault();
            event.stopPropagation();
          }}
        >
          <button
            ref={(element) => { topicButtonRefs.current.all = element; }}
            type="button"
            className={`knowledge-topic-strip__button${activeCategoryId === 'all' ? ' is-active' : ''}`}
            onClick={() => onCategoryChange('all')}
          >
            {language === 'vi' ? 'Tất cả' : 'All'}
            <span>{index.sectionCount}</span>
          </button>

          {categories.map((category) => (
            <button
              key={category.id}
              ref={(element) => { topicButtonRefs.current[category.id] = element; }}
              type="button"
              className={`knowledge-topic-strip__button${activeCategoryId === category.id ? ' is-active' : ''}`}
              onClick={() => onCategoryChange(category.id)}
            >
              {category.title}
              <span>{category.sections.length}</span>
            </button>
          ))}
        </div>

        <button
          type="button"
          className="knowledge-topic-nav__scroll"
          onClick={() => scrollTopics(1)}
          disabled={!canScrollTopicsRight}
          aria-label={language === 'vi' ? 'Cuộn danh mục sang phải' : 'Scroll categories right'}
        >
          ›
        </button>
      </div>

      <div className="knowledge-list">
        {visibleSections.map((section, indexInList) => {
          const expanded = expandedSectionIds.has(section.id);
          const markdown = contentCache[section.content];
          const isLoading = loadingSectionIds.has(section.id);
          const sectionError = sectionErrors[section.id];
          const relatedApis = relatedApisByKnowledgeKey.get(`${section.sourcePath}#${section.id}`) ?? [];

          return (
            <article key={section.id} id={`knowledge-section-${section.id}`} className={`knowledge-card${expanded ? ' is-expanded' : ''}`}>
              <button
                type="button"
                className={`knowledge-card__header${activeCategoryId === 'all' ? '' : ' knowledge-card__header--without-index'}`}
                onClick={() => toggleSection(section.id)}
                aria-expanded={expanded}
              >
                {activeCategoryId === 'all' && (
                  <span className="knowledge-card__index">#{indexInList + 1}</span>
                )}
                <span className="knowledge-card__title-group">
                  <span className="knowledge-card__title">{formatKnowledgeDisplayTitle(section.title)}</span>
                  <span className="knowledge-card__category">{section.categoryTitle}</span>
                </span>
                <span className="knowledge-card__metadata">
                  {section.aiGenerated && (
                    <CustomTooltip
                      content={language === 'vi'
                        ? 'Nội dung này do AI Sinh ra và có thể có sai sót'
                        : 'This content was generated by AI and may contain errors.'}
                    >
                      <span className="knowledge-card__governance knowledge-card__governance--ai">
                        AI Generated
                      </span>
                    </CustomTooltip>
                  )}
                  <CustomTooltip
                    content={section.reviewed
                      ? (language === 'vi'
                        ? 'Nội dung này đã được kiểm tra và sửa chữa'
                        : 'This content has been reviewed and corrected.')
                      : (language === 'vi'
                        ? 'Nội dung này chưa được kiểm tra và sửa chữa'
                        : 'This content has not been reviewed and corrected.')}
                  >
                    <span className={`knowledge-card__governance knowledge-card__governance--${section.reviewed ? 'reviewed' : 'not-reviewed'}`}>
                      {section.reviewed
                        ? (language === 'vi' ? 'Đã review' : 'Reviewed')
                        : (language === 'vi' ? 'Chưa review' : 'Not Reviewed')}
                    </span>
                  </CustomTooltip>
                  <span className={`knowledge-card__level knowledge-card__level--${section.difficulty.toLowerCase()}`}>
                    {resolveDifficultyLabel(section.difficulty, language)}
                  </span>
                </span>
                <span className="knowledge-card__expand" aria-hidden="true">{expanded ? '−' : '+'}</span>
              </button>

              {expanded && (
                <div className="knowledge-card__body">
                  {isLoading && <div className="knowledge-section-status">{language === 'vi' ? 'Đang tải nội dung...' : 'Loading content...'}</div>}

                  {!isLoading && sectionError && (
                    <div className="knowledge-section-status knowledge-section-status--error">{sectionError}</div>
                  )}

                  {!isLoading && markdown !== undefined && (
                    <div className="markdown-content knowledge-card__markdown">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>{markdown}</ReactMarkdown>
                    </div>
                  )}

                  {relatedApis.length > 0 && (
                    <section className="knowledge-related-apis">
                      <div className="knowledge-related-apis__heading">
                        <strong>{language === 'vi' ? 'API liên quan' : 'Related APIs'} · {relatedApis.length}</strong>
                      </div>

                      <div className="knowledge-related-apis__list">
                        {relatedApis.map(({ controller, operation }) => {
                          const previewOpen = previewOperationId === operation.id;
                          const sanitizedExecution = operation.executionHtml
                            ? DOMPurify.sanitize(operation.executionHtml)
                            : '';

                          return (
                            <div
                              key={operation.id}
                              className="knowledge-related-api-control"
                              data-related-api-control
                            >
                              <button
                                type="button"
                                className="knowledge-related-api-row"
                                onClick={() => setPreviewOperationId((current) => (
                                  current === operation.id ? null : operation.id
                                ))}
                              >
                                <span className={`http-method http-method--${operation.httpMethod.toLowerCase()}`}>
                                  {operation.httpMethod}
                                </span>
                                <code>{operation.path || '—'}</code>
                                <span className="knowledge-related-api-row__copy">
                                  <strong>{operation.summary}</strong>
                                  <small>{controller.title}</small>
                                </span>
                              </button>

                              {previewOpen && (
                                <div className="knowledge-related-api-popover" role="dialog" aria-label={operation.summary}>
                                  <div className="knowledge-related-api-popover__header">
                                    <span className={`http-method http-method--${operation.httpMethod.toLowerCase()}`}>
                                      {operation.httpMethod}
                                    </span>
                                    <code>{operation.path || '—'}</code>
                                  </div>

                                  <div className="knowledge-related-api-popover__body">
                                    <div className="knowledge-related-api-popover__basic-info">
                                      <strong>{operation.summary}</strong>
                                      <div className="api-operation__metadata">
                                        {operation.aiGenerated && (
                                          <CustomTooltip
                                            content={language === 'vi'
                                              ? 'Nội dung này do AI Sinh ra và có thể có sai sót'
                                              : 'This content was generated by AI and may contain errors.'}
                                          >
                                            <span className="api-operation__governance api-operation__governance--ai">
                                              AI Generated
                                            </span>
                                          </CustomTooltip>
                                        )}
                                        <CustomTooltip
                                          content={operation.reviewed
                                            ? (language === 'vi'
                                              ? 'Nội dung này đã được kiểm tra và sửa chữa'
                                              : 'This content has been reviewed and corrected.')
                                            : (language === 'vi'
                                              ? 'Nội dung này chưa được kiểm tra và sửa chữa'
                                              : 'This content has not been reviewed and corrected.')}
                                        >
                                          <span className={`api-operation__governance api-operation__governance--${operation.reviewed ? 'reviewed' : 'not-reviewed'}`}>
                                            {operation.reviewed
                                              ? (language === 'vi' ? 'Đã review' : 'Reviewed')
                                              : (language === 'vi' ? 'Chưa review' : 'Not Reviewed')}
                                          </span>
                                        </CustomTooltip>
                                      </div>
                                      {operation.description && <p>{operation.description}</p>}
                                      <small>{controller.title} · {operation.methodSignature}</small>
                                    </div>

                                    <section>
                                      <h4>Execution</h4>
                                      {sanitizedExecution ? (
                                        <div
                                          className="api-execution-content"
                                          dangerouslySetInnerHTML={{ __html: sanitizedExecution }}
                                        />
                                      ) : (
                                        <p className="api-operation__empty-detail">
                                          {language === 'vi' ? 'Chưa có mô tả execution.' : 'No execution description yet.'}
                                        </p>
                                      )}
                                    </section>

                                    {operation.params.length > 0 && (
                                      <section>
                                        <h4>{language === 'vi' ? 'Tham số' : 'Parameters'}</h4>
                                        <div className="api-param-list">
                                          {operation.params.map((param) => (
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

                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    </section>
                  )}
                </div>
              )}
            </article>
          );
        })}

        {visibleSections.length === 0 && (
          <div className="empty-state">
            <strong>{language === 'vi' ? 'Không tìm thấy kiến thức phù hợp' : 'No matching knowledge found'}</strong>
            <span>
              {language === 'vi'
                ? 'Thử đổi category hoặc từ khoá tìm kiếm.'
                : 'Try another category or search term.'}
            </span>
          </div>
        )}
      </div>
    </section>
  );
}
