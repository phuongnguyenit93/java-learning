import { useLanguage } from '../state/LanguageContext';

export type ModuleTab = 'overview' | 'knowledge' | 'quiz' | 'api' | 'execution';

interface ModuleTabsProps {
  activeTab: ModuleTab;
  onChange: (tab: ModuleTab) => void;
  onDownload: () => void;
}

const tabs: Array<{ id: ModuleTab; vi: string; en: string }> = [
  { id: 'overview', vi: 'Overview', en: 'Overview' },
  { id: 'knowledge', vi: 'Knowledge', en: 'Knowledge' },
  { id: 'quiz', vi: 'Quiz', en: 'Quiz' },
  { id: 'api', vi: 'API Docs', en: 'API Docs' },
  { id: 'execution', vi: 'Execution', en: 'Execution' },
];

export function ModuleTabs({ activeTab, onChange, onDownload }: ModuleTabsProps) {
  const { language } = useLanguage();

  return (
    <div className="module-tabs-row">
      <div className="module-tabs" role="tablist" aria-label="Module sections">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.id}
            className={`module-tabs__button${activeTab === tab.id ? ' is-active' : ''}`}
            onClick={() => onChange(tab.id)}
          >
            {tab[language]}
          </button>
        ))}
      </div>

      <button type="button" className="download-action" onClick={onDownload}>
        <span aria-hidden="true">↓</span>
        Download
      </button>
    </div>
  );
}

