import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiDocsPanel } from '../components/ApiDocsPanel';
import { EmptyPanel } from '../components/EmptyPanel';
import { KnowledgePanel } from '../components/KnowledgePanel';
import { LearningSearch } from '../components/LearningSearch';
import { ModuleSidebar } from '../components/ModuleSidebar';
import { ModuleTabs, type ModuleTab } from '../components/ModuleTabs';
import { OverviewPanel } from '../components/OverviewPanel';
import { QuizPanel } from '../components/QuizPanel';
import { learningModules } from '../data/mockLearningData';
import { useLanguage } from '../state/LanguageContext';

export function LearningPage() {
  const { moduleId } = useParams();
  const navigate = useNavigate();
  const { language } = useLanguage();
  const [activeTab, setActiveTab] = useState<ModuleTab>('knowledge');
  const [searchQuery, setSearchQuery] = useState('');
  const [downloadOpen, setDownloadOpen] = useState(false);

  const activeModule = useMemo(
    () => learningModules.find((module) => module.id === moduleId) ?? learningModules[0],
    [moduleId],
  );

  useEffect(() => {
    if (!moduleId) {
      navigate(`/learning/${learningModules[0].id}`, { replace: true });
    }
  }, [moduleId, navigate]);

  useEffect(() => {
    setSearchQuery('');
    setDownloadOpen(false);
  }, [activeModule.id]);

  return (
    <main className="learning-layout">
      <ModuleSidebar activeModuleId={activeModule.id} />

      <section className="learning-main">
        <div className="learning-main__topline">
          <div>
            <span className="eyebrow">{activeModule.path.join(' / ')}</span>
            <h1>{activeModule.name[language]}</h1>
          </div>
          <span className="module-id-badge">{activeModule.id}</span>
        </div>

        <LearningSearch value={searchQuery} onChange={setSearchQuery} />

        <div className="module-tab-wrapper">
          <ModuleTabs
            activeTab={activeTab}
            onChange={(tab) => {
              setActiveTab(tab);
              setDownloadOpen(false);
            }}
            onDownload={() => setDownloadOpen((current) => !current)}
          />

          {downloadOpen && (
            <div className="download-popover">
              <EmptyPanel type="download" />
            </div>
          )}
        </div>

        <div className="learning-content">
          {activeTab === 'overview' && <OverviewPanel module={activeModule} />}
          {activeTab === 'knowledge' && (
            <KnowledgePanel moduleId={activeModule.id} searchQuery={searchQuery} />
          )}
          {activeTab === 'quiz' && <QuizPanel moduleId={activeModule.id} />}
          {activeTab === 'api' && <ApiDocsPanel moduleId={activeModule.id} />}
          {activeTab === 'execution' && <EmptyPanel type="execution" />}
        </div>
      </section>
    </main>
  );
}

