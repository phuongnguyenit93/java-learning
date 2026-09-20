import { useEffect, useMemo, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  collectKnowledgeCategories,
  collectKnowledgeSections,
  formatKnowledgeDisplayTitle,
} from '../data/knowledge';
import { useLanguage } from '../state/LanguageContext';
import type { KnowledgeIndex } from '../types/learning';

interface KnowledgePanelProps {
  index: KnowledgeIndex | null;
  loading: boolean;
  error: string | null;
  searchQuery: string;
  activeCategoryId: string;
  selectedSectionId: string | null;
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
  onCategoryChange,
  onSectionChange,
}: KnowledgePanelProps) {
  const { language } = useLanguage();
  const [contentCache, setContentCache] = useState<Record<string, string>>({});
  const [expandedSectionIds, setExpandedSectionIds] = useState<Set<string>>(() => new Set());
  const [loadingSectionIds, setLoadingSectionIds] = useState<Set<string>>(() => new Set());
  const [sectionErrors, setSectionErrors] = useState<Record<string, string>>({});
  const [canScrollTopicsLeft, setCanScrollTopicsLeft] = useState(false);
  const [canScrollTopicsRight, setCanScrollTopicsRight] = useState(false);
  const topicStripRef = useRef<HTMLDivElement>(null);
  const topicButtonRefs = useRef<Record<string, HTMLButtonElement | null>>({});

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

  useEffect(() => {
    setContentCache({});
    setExpandedSectionIds(new Set());
    setLoadingSectionIds(new Set());
    setSectionErrors({});
  }, [index?.moduleId, index?.language]);

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

        <div ref={topicStripRef} className="knowledge-topic-strip" aria-label="Knowledge categories">
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
                <span className="knowledge-card__level knowledge-card__level--basic">
                  {language === 'vi' ? 'CƠ BẢN' : 'BASIC'}
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
