import { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useLanguage } from '../state/LanguageContext';
import type { LearningModule } from '../types/learning';

interface OverviewPanelProps {
  module: LearningModule;
}

export function OverviewPanel({ module }: OverviewPanelProps) {
  const { language } = useLanguage();
  const [content, setContent] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const overviewPath = module.overview[language];

  useEffect(() => {
    let active = true;

    if (!overviewPath) {
      setContent(null);
      setError(null);
      setLoading(false);
      return () => {
        active = false;
      };
    }

    setLoading(true);
    setError(null);

    fetch(overviewPath, { cache: 'no-cache' })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Unable to load overview: ${response.status} ${response.statusText}`);
        }

        return response.text();
      })
      .then((markdown) => {
        if (active) {
          setContent(markdown);
          setLoading(false);
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setContent(null);
          setLoading(false);
          setError(cause instanceof Error ? cause.message : 'Unable to load overview.');
        }
      });

    return () => {
      active = false;
    };
  }, [overviewPath]);

  if (!overviewPath) {
    return (
      <div className="empty-state empty-state--large">
        <strong>{language === 'vi' ? 'Chưa có nội dung Overview' : 'No Overview content yet'}</strong>
        <span>
          {language === 'vi'
            ? 'Module này chưa có readme/vi/BASE.md.'
            : 'This module does not have readme/en/BASE.md yet.'}
        </span>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="empty-state empty-state--large">
        <strong>{language === 'vi' ? 'Đang tải Overview...' : 'Loading Overview...'}</strong>
      </div>
    );
  }

  if (error) {
    return (
      <div className="empty-state empty-state--large">
        <strong>{language === 'vi' ? 'Không thể tải Overview' : 'Unable to load Overview'}</strong>
        <span>{error}</span>
      </div>
    );
  }

  return (
    <section className="overview-panel markdown-content">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{content ?? ''}</ReactMarkdown>
    </section>
  );
}
