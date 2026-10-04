import { useEffect, useMemo, useState } from 'react';
import { loadReferenceDocument } from '../data/reference';
import { useLanguage } from '../state/LanguageContext';
import type { ReferenceDocument } from '../types/learning';

interface ReferencePanelProps {
  path?: string;
  searchQuery: string;
}

export function ReferencePanel({ path, searchQuery }: ReferencePanelProps) {
  const { language } = useLanguage();
  const [document, setDocument] = useState<ReferenceDocument | null>(null);
  const [loading, setLoading] = useState(Boolean(path));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    setDocument(null);
    setError(null);

    if (!path) {
      setLoading(false);
      return () => {
        active = false;
      };
    }

    setLoading(true);

    loadReferenceDocument(path)
      .then((result) => {
        if (!active) {
          return;
        }

        setDocument(result);
        setLoading(false);
      })
      .catch((caught: unknown) => {
        if (!active) {
          return;
        }

        setDocument(null);
        setLoading(false);
        setError(caught instanceof Error ? caught.message : 'Unable to load Reference.');
      });

    return () => {
      active = false;
    };
  }, [path]);

  const references = useMemo(() => {
    const items = document?.references ?? [];
    const query = searchQuery.trim().toLocaleLowerCase();

    if (!query) {
      return items;
    }

    return items.filter((item) => (
      `${item.title} ${item.description} ${item.url}`
        .toLocaleLowerCase()
        .includes(query)
    ));
  }, [document, searchQuery]);

  if (loading) {
    return (
      <div className="empty-state">
        <strong>{language === 'vi' ? 'Đang tải tài liệu tham khảo...' : 'Loading references...'}</strong>
      </div>
    );
  }

  if (error) {
    return (
      <div className="empty-state">
        <strong>{language === 'vi' ? 'Không thể tải tài liệu tham khảo' : 'Unable to load references'}</strong>
        <span>{error}</span>
      </div>
    );
  }

  if (!path || !document) {
    return (
      <div className="empty-state">
        <strong>{language === 'vi' ? 'Module chưa có tài liệu tham khảo.' : 'This module has no references yet.'}</strong>
      </div>
    );
  }

  if (references.length === 0) {
    return (
      <div className="empty-state">
        <strong>{language === 'vi' ? 'Không có tài liệu phù hợp với tìm kiếm.' : 'No references match your search.'}</strong>
      </div>
    );
  }

  return (
    <section className="reference-panel">
      <div className="reference-panel__header">
        <span className="eyebrow">REFERENCE</span>
        <h2>{language === 'vi' ? 'Tài liệu tham khảo' : 'References'}</h2>
        <p>
          {language === 'vi'
            ? 'Các nguồn chung đã được chọn lọc để học và kiểm chứng nội dung của module.'
            : 'Curated shared sources for studying and verifying this module.'}
        </p>
      </div>

      <div className="reference-list">
        {references.map((item, index) => (
          <article className="reference-card" key={`${item.url}:${index}`}>
            <div className="reference-card__number">{String(index + 1).padStart(2, '0')}</div>
            <div className="reference-card__content">
              <h3>{item.title}</h3>
              {item.description && <p>{item.description}</p>}
              <a href={item.url} target="_blank" rel="noreferrer">
                {language === 'vi' ? 'Mở tài liệu ↗' : 'Open reference ↗'}
              </a>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
