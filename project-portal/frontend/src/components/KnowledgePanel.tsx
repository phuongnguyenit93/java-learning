import { useMemo, useState } from 'react';
import { knowledgeItems, knowledgeTopics } from '../data/mockLearningData';
import { useLanguage } from '../state/LanguageContext';

interface KnowledgePanelProps {
  moduleId: string;
  searchQuery: string;
}

const levelLabels = {
  vi: { basic: 'CƠ BẢN', intermediate: 'TRUNG BÌNH', advanced: 'NÂNG CAO' },
  en: { basic: 'BASIC', intermediate: 'INTERMEDIATE', advanced: 'ADVANCED' },
};

export function KnowledgePanel({ moduleId, searchQuery }: KnowledgePanelProps) {
  const { language } = useLanguage();
  const [activeTopic, setActiveTopic] = useState('all');
  const [expandedIds, setExpandedIds] = useState<Set<string>>(() => new Set(['thread-1']));

  const items = useMemo(() => {
    const normalized = searchQuery.trim().toLowerCase();

    return knowledgeItems.filter((item) => {
      if (item.moduleId !== moduleId) {
        return false;
      }

      if (activeTopic !== 'all' && item.topicId !== activeTopic) {
        return false;
      }

      if (!normalized) {
        return true;
      }

      const searchable = `${item.title[language]} ${item.summary[language]} ${item.paragraphs
        .map((paragraph) => paragraph[language])
        .join(' ')}`.toLowerCase();

      return searchable.includes(normalized);
    });
  }, [activeTopic, language, moduleId, searchQuery]);

  const topicCounts = useMemo(() => {
    const baseItems = knowledgeItems.filter((item) => item.moduleId === moduleId);

    return new Map(
      knowledgeTopics.map((topic) => [
        topic.id,
        topic.id === 'all' ? baseItems.length : baseItems.filter((item) => item.topicId === topic.id).length,
      ]),
    );
  }, [moduleId]);

  const toggle = (id: string) => {
    setExpandedIds((current) => {
      const next = new Set(current);

      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }

      return next;
    });
  };

  return (
    <section className="knowledge-panel">
      <div className="knowledge-topic-strip" aria-label="Knowledge topics">
        {knowledgeTopics.map((topic) => (
          <button
            key={topic.id}
            type="button"
            className={`knowledge-topic-strip__button${activeTopic === topic.id ? ' is-active' : ''}`}
            onClick={() => setActiveTopic(topic.id)}
          >
            {topic.label[language]}
            <span>{topicCounts.get(topic.id) ?? 0}</span>
          </button>
        ))}
      </div>

      <div className="knowledge-list">
        {items.map((item, index) => {
          const expanded = expandedIds.has(item.id);

          return (
            <article key={item.id} className={`knowledge-card${expanded ? ' is-expanded' : ''}`}>
              <button
                type="button"
                className="knowledge-card__header"
                onClick={() => toggle(item.id)}
                aria-expanded={expanded}
              >
                <span className="knowledge-card__index">#{index + 1}</span>
                <span className="knowledge-card__title">{item.title[language]}</span>
                <span className={`knowledge-card__level knowledge-card__level--${item.level}`}>
                  {levelLabels[language][item.level]}
                </span>
                <span className="knowledge-card__expand" aria-hidden="true">{expanded ? '−' : '+'}</span>
              </button>

              {expanded && (
                <div className="knowledge-card__body">
                  <p className="knowledge-card__lead">{item.summary[language]}</p>

                  {item.paragraphs.map((paragraph, paragraphIndex) => (
                    <p key={`${item.id}-paragraph-${paragraphIndex}`}>{paragraph[language]}</p>
                  ))}

                  {item.bullets.length > 0 && (
                    <ul>
                      {item.bullets.map((bullet, bulletIndex) => (
                        <li key={`${item.id}-bullet-${bulletIndex}`}>{bullet[language]}</li>
                      ))}
                    </ul>
                  )}

                  {item.code && <pre><code>{item.code}</code></pre>}
                </div>
              )}
            </article>
          );
        })}

        {items.length === 0 && (
          <div className="empty-state">
            <strong>{language === 'vi' ? 'Không tìm thấy kiến thức phù hợp' : 'No matching knowledge found'}</strong>
            <span>
              {language === 'vi'
                ? 'Thử đổi module, category hoặc từ khoá tìm kiếm.'
                : 'Try another module, category or search term.'}
            </span>
          </div>
        )}
      </div>
    </section>
  );
}

