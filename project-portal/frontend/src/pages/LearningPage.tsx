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
import {
  collectRealModules,
  findModuleByRouteId,
  loadModuleCatalog,
  toLearningModule,
} from '../data/moduleCatalog';
import { resolveModuleStats } from '../data/moduleStats';
import { useLanguage } from '../state/LanguageContext';
import type { ModuleCatalog } from '../types/learning';

export function LearningPage() {
  const { moduleId } = useParams();
  const navigate = useNavigate();
  const { language } = useLanguage();
  const [catalog, setCatalog] = useState<ModuleCatalog | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<ModuleTab>('knowledge');
  const [searchQuery, setSearchQuery] = useState('');
  const [downloadOpen, setDownloadOpen] = useState(false);

  useEffect(() => {
    let active = true;

    loadModuleCatalog()
      .then((result) => {
        if (active) {
          setCatalog(result);
          setCatalogError(null);
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setCatalogError(error instanceof Error ? error.message : 'Unable to load module catalog.');
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const realModules = useMemo(
    () => (catalog ? collectRealModules(catalog.root) : []),
    [catalog],
  );

  const requestedModule = useMemo(
    () => (catalog && moduleId ? findModuleByRouteId(catalog.root, moduleId) : undefined),
    [catalog, moduleId],
  );

  const activeModuleNode = requestedModule ?? realModules[0];
  const activeModuleId = activeModuleNode?.routeId ?? activeModuleNode?.serviceName ?? activeModuleNode?.name ?? '';
  const activeStats = useMemo(
    () => resolveModuleStats(activeModuleId),
    [activeModuleId],
  );
  const activeModule = useMemo(
    () => (activeModuleNode ? toLearningModule(activeModuleNode, activeStats) : null),
    [activeModuleNode, activeStats],
  );

  useEffect(() => {
    if (!catalog || !activeModuleNode?.routeId) {
      return;
    }

    if (!moduleId || !requestedModule) {
      navigate(`/learning/${activeModuleNode.routeId}`, { replace: true });
    }
  }, [activeModuleNode, catalog, moduleId, navigate, requestedModule]);

  useEffect(() => {
    if (!activeModule) {
      return;
    }

    setSearchQuery('');
    setDownloadOpen(false);
  }, [activeModule?.id]);

  if (catalogError) {
    return (
      <main className="learning-layout learning-layout--status">
        <div className="empty-state empty-state--large">
          <strong>{language === 'vi' ? 'Không thể tải cấu trúc module' : 'Unable to load module structure'}</strong>
          <span>{catalogError}</span>
        </div>
      </main>
    );
  }

  if (!catalog || !activeModule) {
    return (
      <main className="learning-layout learning-layout--status">
        <div className="empty-state empty-state--large">
          <strong>{language === 'vi' ? 'Đang tải cấu trúc module...' : 'Loading module structure...'}</strong>
        </div>
      </main>
    );
  }

  return (
    <main className="learning-layout">
      <ModuleSidebar nodes={catalog.root.children} activeModuleId={activeModule.id} />

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
            stats={activeStats}
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
          {activeTab === 'overview' && <OverviewPanel module={activeModule} stats={activeStats} />}
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
