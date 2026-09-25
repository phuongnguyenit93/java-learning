import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiDocsPanel } from '../components/ApiDocsPanel';
import { KnowledgePanel } from '../components/KnowledgePanel';
import { InterviewPanel } from '../components/InterviewPanel';
import { LearningSearch } from '../components/LearningSearch';
import { LocalRunPanel } from '../components/LocalRunPanel';
import { MenuPanel } from '../components/MenuPanel';
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
import { loadApiOperationCount } from '../data/apiDocs';
import { loadInterviewQuestionCount } from '../data/interview';
import { loadKnowledgeIndex } from '../data/knowledge';
import { loadQuizQuestionCount } from '../data/quiz';
import { resolveModuleStats } from '../data/moduleStats';
import { useLanguage } from '../state/LanguageContext';
import type { KnowledgeIndex, ModuleCatalog } from '../types/learning';

export function LearningPage() {
  const { moduleId } = useParams();
  const navigate = useNavigate();
  const { language } = useLanguage();
  const [catalog, setCatalog] = useState<ModuleCatalog | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<ModuleTab>('knowledge');
  const [visitedTabs, setVisitedTabs] = useState<Set<ModuleTab>>(() => new Set<ModuleTab>(['knowledge']));
  const [searchQuery, setSearchQuery] = useState('');
  const [downloadTarget, setDownloadTarget] = useState<'api-notice' | null>(null);
  const [knowledgeIndex, setKnowledgeIndex] = useState<KnowledgeIndex | null>(null);
  const [knowledgeCounts, setKnowledgeCounts] = useState<Record<string, number>>({});
  const [quizCounts, setQuizCounts] = useState<Record<string, number>>({});
  const [interviewCounts, setInterviewCounts] = useState<Record<string, number>>({});
  const [apiCounts, setApiCounts] = useState<Record<string, number>>({});
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [knowledgeLoading, setKnowledgeLoading] = useState(false);
  const [knowledgeError, setKnowledgeError] = useState<string | null>(null);
  const [activeKnowledgeCategory, setActiveKnowledgeCategory] = useState('all');
  const [selectedKnowledgeSection, setSelectedKnowledgeSection] = useState<string | null>(null);

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
  const knowledgePath = activeModule?.knowledge[language];
  const quizPath = activeModule?.quiz[language];
  const interviewPath = activeModule?.interview[language];
  const apiPath = activeModule?.api[language];
  const displayedStats = useMemo(
    () => ({
      ...activeStats,
      knowledge: knowledgeIndex?.sectionCount ?? knowledgeCounts[activeModuleId] ?? 0,
      quiz: quizCounts[activeModuleId] ?? 0,
      interview: interviewCounts[activeModuleId] ?? 0,
      apiDocs: apiCounts[activeModuleId] ?? 0,
    }),
    [activeModuleId, activeStats, apiCounts, interviewCounts, knowledgeCounts, knowledgeIndex, quizCounts],
  );

  useEffect(() => {
    if (activeTab === 'execution' && activeModule && !activeModule.capabilities.execution) {
      setActiveTab('knowledge');
      setVisitedTabs((current) => {
        const next = new Set(current);
        next.add('knowledge');
        return next;
      });
    }
  }, [activeModule, activeTab]);

  useEffect(() => {
    let active = true;

    setKnowledgeCounts({});

    if (!catalog) {
      return () => {
        active = false;
      };
    }

    const modulesWithKnowledge = collectRealModules(catalog.root)
      .map((node) => ({
        routeId: node.routeId,
        path: node.knowledge?.[language],
      }))
      .filter((entry): entry is { routeId: string; path: string } => Boolean(entry.routeId && entry.path));

    Promise.all(
      modulesWithKnowledge.map(async ({ routeId, path }) => {
        try {
          const index = await loadKnowledgeIndex(path);
          return [routeId, index.sectionCount] as const;
        } catch {
          return null;
        }
      }),
    )
      .then((entries) => {
        if (active) {
          setKnowledgeCounts(Object.fromEntries(entries.filter((entry) => entry !== null)));
        }
      });

    return () => {
      active = false;
    };
  }, [catalog, language]);

  useEffect(() => {
    let active = true;

    setInterviewCounts({});

    if (!catalog) {
      return () => {
        active = false;
      };
    }

    const modulesWithInterview = collectRealModules(catalog.root)
      .map((node) => ({
        routeId: node.routeId,
        path: node.interview?.[language],
      }))
      .filter((entry): entry is { routeId: string; path: string } => Boolean(entry.routeId && entry.path));

    Promise.all(
      modulesWithInterview.map(async ({ routeId, path }) => {
        try {
          return [routeId, await loadInterviewQuestionCount(path)] as const;
        } catch {
          return null;
        }
      }),
    ).then((entries) => {
      if (active) {
        setInterviewCounts(Object.fromEntries(entries.filter((entry) => entry !== null)));
      }
    });

    return () => {
      active = false;
    };
  }, [catalog, language]);

  useEffect(() => {
    let active = true;

    setQuizCounts({});

    if (!catalog) {
      return () => {
        active = false;
      };
    }

    const modulesWithQuiz = collectRealModules(catalog.root)
      .map((node) => ({
        routeId: node.routeId,
        path: node.quiz?.[language],
      }))
      .filter((entry): entry is { routeId: string; path: string } => Boolean(entry.routeId && entry.path));

    Promise.all(
      modulesWithQuiz.map(async ({ routeId, path }) => {
        try {
          return [routeId, await loadQuizQuestionCount(path)] as const;
        } catch {
          return null;
        }
      }),
    ).then((entries) => {
      if (active) {
        setQuizCounts(Object.fromEntries(entries.filter((entry) => entry !== null)));
      }
    });

    return () => {
      active = false;
    };
  }, [catalog, language]);

  useEffect(() => {
    let active = true;

    setApiCounts({});

    if (!catalog) {
      return () => {
        active = false;
      };
    }

    const modulesWithApi = collectRealModules(catalog.root)
      .map((node) => ({
        routeId: node.routeId,
        path: node.api?.[language],
      }))
      .filter((entry): entry is { routeId: string; path: string } => Boolean(entry.routeId && entry.path));

    Promise.all(
      modulesWithApi.map(async ({ routeId, path }) => {
        try {
          const count = await loadApiOperationCount(path);
          return [routeId, count] as const;
        } catch {
          return null;
        }
      }),
    ).then((entries) => {
      if (active) {
        setApiCounts(Object.fromEntries(entries.filter((entry) => entry !== null)));
      }
    });

    return () => {
      active = false;
    };
  }, [catalog, language]);

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
    setDownloadTarget(null);
    setActiveKnowledgeCategory('all');
    setSelectedKnowledgeSection(null);
    setVisitedTabs(new Set<ModuleTab>([activeTab]));
  }, [activeModule?.id]);

  useEffect(() => {
    if (!downloadTarget) {
      return undefined;
    }

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target;

      if (target instanceof Element && target.closest('[data-download-control]')) {
        return;
      }

      setDownloadTarget(null);
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setDownloadTarget(null);
      }
    };

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [downloadTarget]);

  useEffect(() => {
    let active = true;

    if (!knowledgePath) {
      setKnowledgeIndex(null);
      setKnowledgeLoading(false);
      setKnowledgeError(null);
      return () => {
        active = false;
      };
    }

    setKnowledgeLoading(true);
    setKnowledgeError(null);

    loadKnowledgeIndex(knowledgePath)
      .then((result) => {
        if (!active) {
          return;
        }

        setKnowledgeIndex(result);
        setKnowledgeLoading(false);
      })
      .catch((error: unknown) => {
        if (!active) {
          return;
        }

        setKnowledgeIndex(null);
        setKnowledgeLoading(false);
        setKnowledgeError(error instanceof Error ? error.message : 'Unable to load Knowledge.');
      });

    return () => {
      active = false;
    };
  }, [knowledgePath]);

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
    <main className={`learning-layout${sidebarCollapsed ? ' is-sidebar-collapsed' : ''}`}>
      <ModuleSidebar
        nodes={catalog.root.children}
        activeModuleId={activeModule.id}
        knowledgeCounts={knowledgeCounts}
        quizCounts={quizCounts}
        interviewCounts={interviewCounts}
        apiCounts={apiCounts}
      />

      {!sidebarCollapsed && (
        <button
          type="button"
          className="learning-sidebar-collapse-control"
          onClick={() => setSidebarCollapsed(true)}
          aria-label={language === 'vi' ? 'Thu gọn thanh menu' : 'Collapse sidebar'}
          title={language === 'vi' ? 'Thu gọn thanh menu' : 'Collapse sidebar'}
        >
          ‹
        </button>
      )}

      {sidebarCollapsed && (
        <button
          type="button"
          className="learning-sidebar-reveal"
          onClick={() => setSidebarCollapsed(false)}
          aria-label={language === 'vi' ? 'Mở thanh menu' : 'Open sidebar'}
          title={language === 'vi' ? 'Mở thanh menu' : 'Open sidebar'}
        >
          ›
        </button>
      )}

      <section className="learning-main">
        <div className="learning-main__topline">
          <div>
            <span className="eyebrow">{activeModule.path.join(' / ')}</span>
            <h1>{activeModule.name[language]}</h1>
            <p className="learning-main__module-description">{activeModule.description[language]}</p>
          </div>
          <span className="module-id-badge">{activeModule.id}</span>
        </div>

        <LearningSearch value={searchQuery} onChange={setSearchQuery} />

        <div className="module-tab-wrapper">
          <ModuleTabs
            activeTab={activeTab}
            stats={displayedStats}
            executionEnabled={activeModule.capabilities.execution}
            onChange={(tab) => {
              setActiveTab(tab);
              setVisitedTabs((current) => {
                if (current.has(tab)) {
                  return current;
                }

                const next = new Set(current);
                next.add(tab);
                return next;
              });
              setDownloadTarget(null);
            }}
          />
        </div>

        <div className="learning-content">
          {activeTab === 'overview' && <OverviewPanel module={activeModule} />}
          {visitedTabs.has('menu') && (
            <div hidden={activeTab !== 'menu'}>
              <MenuPanel
                key={`${activeModule.id}:menu`}
                index={knowledgeIndex}
                loading={knowledgeLoading}
                error={knowledgeError}
                searchQuery={searchQuery}
                selectedSectionId={selectedKnowledgeSection}
                onSelectSection={(categoryId, sectionId) => {
                  setSearchQuery('');
                  setActiveKnowledgeCategory(categoryId);
                  setSelectedKnowledgeSection(sectionId);
                  setVisitedTabs((current) => {
                    const next = new Set(current);
                    next.add('knowledge');
                    return next;
                  });
                  setActiveTab('knowledge');
                  setDownloadTarget(null);
                }}
              />
            </div>
          )}
          {visitedTabs.has('knowledge') && (
            <div hidden={activeTab !== 'knowledge'}>
              <KnowledgePanel
                key={`${activeModule.id}:knowledge`}
                index={knowledgeIndex}
                loading={knowledgeLoading}
                error={knowledgeError}
                searchQuery={searchQuery}
                activeCategoryId={activeKnowledgeCategory}
                selectedSectionId={selectedKnowledgeSection}
                apiBasePath={apiPath}
                onCategoryChange={(categoryId) => {
                  setActiveKnowledgeCategory(categoryId);
                  setSelectedKnowledgeSection(null);
                }}
                onSectionChange={setSelectedKnowledgeSection}
              />
            </div>
          )}
          {visitedTabs.has('quiz') && (
            <div hidden={activeTab !== 'quiz'}>
              <QuizPanel
                key={`${activeModule.id}:quiz`}
                path={quizPath}
                knowledgeIndex={knowledgeIndex}
                apiBasePath={apiPath}
              />
            </div>
          )}
          {visitedTabs.has('interview') && (
            <div hidden={activeTab !== 'interview'}>
              <InterviewPanel
                key={`${activeModule.id}:interview`}
                path={interviewPath}
                knowledgeIndex={knowledgeIndex}
                apiBasePath={apiPath}
              />
            </div>
          )}
          {visitedTabs.has('api') && (
            <div hidden={activeTab !== 'api'}>
              <ApiDocsPanel
                key={`${activeModule.id}:api`}
                basePath={apiPath}
                knowledgeIndex={knowledgeIndex}
                searchQuery={searchQuery}
                downloadOpen={downloadTarget === 'api-notice'}
                onDownloadToggle={() => setDownloadTarget((current) => (current === 'api-notice' ? null : 'api-notice'))}
              />
            </div>
          )}
          {activeTab === 'execution' && activeModule.capabilities.execution && (
            <LocalRunPanel
              moduleId={activeModule.id}
              moduleName={activeModule.shortName}
              sourceFingerprint={activeModule.sourceFingerprint}
            />
          )}
        </div>
      </section>
    </main>
  );
}
