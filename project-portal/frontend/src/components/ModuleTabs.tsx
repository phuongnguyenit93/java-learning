import { useLanguage } from '../state/LanguageContext';
import type { ModuleStats } from '../types/learning';

export type ModuleTab = 'overview' | 'menu' | 'roadmap' | 'reference' | 'knowledge' | 'quiz' | 'interview' | 'api' | 'execution' | 'feedback';

interface ModuleTabsProps {
  activeTab: ModuleTab;
  stats: ModuleStats;
  referenceEnabled: boolean;
  executionEnabled: boolean;
  onChange: (tab: ModuleTab) => void;
}

const tabs: Array<{ id: ModuleTab; vi: string; en: string; countKey?: keyof ModuleStats }> = [
  { id: 'overview', vi: 'Overview', en: 'Overview' },
  { id: 'roadmap', vi: 'Roadmap', en: 'Roadmap' },
  { id: 'reference', vi: 'Reference', en: 'Reference' },
  { id: 'menu', vi: 'Menu', en: 'Menu' },
  { id: 'knowledge', vi: 'Knowledge', en: 'Knowledge', countKey: 'knowledge' },
  { id: 'api', vi: 'API Docs', en: 'API Docs', countKey: 'apiDocs' },
  { id: 'quiz', vi: 'Quiz', en: 'Quiz', countKey: 'quiz' },
  { id: 'interview', vi: 'Interview', en: 'Interview', countKey: 'interview' },
  { id: 'execution', vi: 'Local Run', en: 'Local Run' },
];

export function ModuleTabs({ activeTab, stats, referenceEnabled, executionEnabled, onChange }: ModuleTabsProps) {
  const { language } = useLanguage();

  return (
    <div className="module-tabs-row" role="tablist" aria-label="Module sections">
      <div className="module-tabs">
        {tabs.filter((tab) => (
          (tab.id !== 'execution' || executionEnabled)
          && (tab.id !== 'reference' || referenceEnabled)
        )).map((tab) => {
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

      <button
        type="button"
        role="tab"
        aria-selected={activeTab === 'feedback'}
        className={`module-tabs__button module-tabs__button--feedback module-tabs__feedback${activeTab === 'feedback' ? ' is-active' : ''}`}
        onClick={() => onChange('feedback')}
      >
        <span>Feedback</span>
      </button>
    </div>
  );
}
