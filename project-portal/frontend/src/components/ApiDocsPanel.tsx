import { apiOperations } from '../data/mockLearningData';
import { useLanguage } from '../state/LanguageContext';

interface ApiDocsPanelProps {
  moduleId: string;
}

export function ApiDocsPanel({ moduleId }: ApiDocsPanelProps) {
  const { language } = useLanguage();
  const operations = apiOperations.filter((operation) => operation.moduleId === moduleId);

  if (operations.length === 0) {
    return (
      <div className="empty-state">
        <strong>{language === 'vi' ? 'Module này chưa có API Docs' : 'This module has no API Docs yet'}</strong>
        <span>
          {language === 'vi'
            ? 'Sau này dữ liệu có thể được generate thành OpenAPI/API catalog tĩnh.'
            : 'Later this can be populated from a generated static OpenAPI/API catalog.'}
        </span>
      </div>
    );
  }

  return (
    <section className="api-list">
      <div className="section-intro">
        <span className="eyebrow">PSEUDO REST API</span>
        <h2>{language === 'vi' ? 'API documentation tĩnh' : 'Static API documentation'}</h2>
        <p>
          {language === 'vi'
            ? 'Dữ liệu hiện tại là fake data; chưa gọi backend và chưa execute API thật.'
            : 'The current data is mocked; no backend calls or real API execution are connected yet.'}
        </p>
      </div>

      {operations.map((operation) => (
        <article key={operation.id} className="api-operation">
          <span className={`http-method http-method--${operation.method.toLowerCase()}`}>{operation.method}</span>
          <code>{operation.path}</code>
          <div className="api-operation__copy">
            <strong>{operation.summary[language]}</strong>
            <span>{operation.description[language]}</span>
          </div>
          <button type="button" disabled>{language === 'vi' ? 'Execute sau' : 'Execute later'}</button>
        </article>
      ))}
    </section>
  );
}

