import { useLanguage } from '../state/LanguageContext';
import type { ModuleStats } from '../types/learning';

export type ModuleTab = 'overview' | 'knowledge' | 'quiz' | 'api' | 'execution';

interface ModuleTabsProps {
  activeTab: ModuleTab;
  stats: ModuleStats;
  onChange: (tab: ModuleTab) => void;
  onDownload: () => void;
}

const tabs: Array<{ id: ModuleTab; vi: string; en: string; countKey?: keyof ModuleStats }> = [
  { id: 'overview', vi: 'Overview', en: 'Overview' },
  { id: 'knowledge', vi: 'Knowledge', en: 'Knowledge', countKey: 'knowledge' },
  { id: 'quiz', vi: 'Quiz', en: 'Quiz', countKey: 'quiz' },
  { id: 'api', vi: 'API Docs', en: 'API Docs', countKey: 'apiDocs' },
  { id: 'execution', vi: 'Execution', en: 'Execution' },
];

export function ModuleTabs({ activeTab, stats, onChange, onDownload }: ModuleTabsProps) {
  const { language } = useLanguage();

  return (
    <div className="module-tabs-row">
      <div className="module-tabs" role="tablist" aria-label="Module sections">
        {tabs.map((tab) => {
          const count = tab.countKey ? stats[tab.countKey] : 0;

          return (
            <button
              key={tab.id}
              type="button"
              role="tab"
              aria-selected={activeTab === tab.id}
              className={`module-tabs__button module-tabs__button--${tab.id}${activeTab === tab.id ? ' is-active' : ''}`}
              onClick={() => onChange(tab.id)}
            >
              <span>{tab[language]}</span>
              {count > 0 && <span className="module-tabs__count">{count}</span>}
            </button>
          );
        })}
      </div>

      <button type="button" className="download-action" onClick={onDownload}>
        <span aria-hidden="true">↓</span>
        Download
      </button>
    </div>
  );
}
