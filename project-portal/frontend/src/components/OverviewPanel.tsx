import { useLanguage } from '../state/LanguageContext';
import type { LearningModule } from '../types/learning';

interface OverviewPanelProps {
  module: LearningModule;
}

export function OverviewPanel({ module }: OverviewPanelProps) {
  const { language } = useLanguage();

  const stats = [
    { value: module.questionCount, vi: 'Mục kiến thức', en: 'Knowledge items' },
    { value: module.capabilities.quiz ? '✓' : '—', vi: 'Quiz', en: 'Quiz' },
    { value: module.capabilities.apiDocs ? '✓' : '—', vi: 'API Docs', en: 'API Docs' },
    { value: module.capabilities.execution ? '✓' : '—', vi: 'Execution', en: 'Execution' },
  ];

  return (
    <section className="overview-panel">
      <div className="overview-panel__intro">
        <span className="eyebrow">{module.path.join(' / ')}</span>
        <h2>{module.name[language]}</h2>
        <p>{module.description[language]}</p>
      </div>

      <div className="overview-stats">
        {stats.map((stat) => (
          <div key={stat.en} className="overview-stat">
            <strong>{stat.value}</strong>
            <span>{language === 'vi' ? stat.vi : stat.en}</span>
          </div>
        ))}
      </div>

      <div className="overview-progress">
        <div className="overview-progress__copy">
          <strong>{language === 'vi' ? 'Tiến độ học tập' : 'Learning progress'}</strong>
          <span>{language === 'vi' ? 'Dữ liệu demo phía client' : 'Client-side demo data'}</span>
        </div>
        <div className="progress-bar"><span /></div>
        <strong>64%</strong>
      </div>
    </section>
  );
}

