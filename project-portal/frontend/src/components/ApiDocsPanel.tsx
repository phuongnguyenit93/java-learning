import { useEffect, useMemo, useState } from 'react';
import DOMPurify from 'dompurify';
import { loadApiDocs } from '../data/apiDocs';
import { collectKnowledgeCategories, formatKnowledgeDisplayTitle } from '../data/knowledge';
import { useLanguage } from '../state/LanguageContext';
import type { ApiDocController, ApiDocsDocument, KnowledgeIndex } from '../types/learning';
import { DownloadAction } from './DownloadAction';

interface ApiDocsPanelProps {
  basePath?: string;
  knowledgeIndex: KnowledgeIndex | null;
  searchQuery: string;
  downloadOpen: boolean;
  onDownloadToggle: () => void;
}

export function ApiDocsPanel({
  basePath,
  knowledgeIndex,
  searchQuery,
  downloadOpen,
  onDownloadToggle,
}: ApiDocsPanelProps) {
  const { language } = useLanguage();
  const [document, setDocument] = useState<ApiDocsDocument | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expandedControllers, setExpandedControllers] = useState<Set<string>>(new Set());
  const [expandedOperations, setExpandedOperations] = useState<Set<string>>(new Set());

  useEffect(() => {
    let active = true;

    setDocument(null);
    setExpandedControllers(new Set());
    setExpandedOperations(new Set());

    if (!basePath) {
      setLoading(false);
      setError(null);
      return () => {
        active = false;
      };
    }

    setLoading(true);
    setError(null);

    loadApiDocs(basePath, knowledgeIndex)
      .then((result) => {
        if (!active) {
          return;
        }

        setDocument(result);
        setLoading(false);
      })
      .catch((loadError: unknown) => {
        if (!active) {
          return;
        }

        setError(loadError instanceof Error ? loadError.message : 'Unable to load API Docs.');
        setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [basePath, knowledgeIndex]);

  const visibleControllers = useMemo(() => {
    if (!document) {
      return [];
    }

    const query = searchQuery.trim().toLowerCase();

    if (!query) {
      return document.controllers;
    }

    return document.controllers
      .map((controller) => {
        const controllerMatch = `${controller.name} ${controller.title} ${controller.description}`
          .toLowerCase()
          .includes(query);

        if (controllerMatch) {
          return controller;
        }

        const matchingOperations = controller.operations.filter((operation) =>
          `${operation.methodName} ${operation.methodSignature} ${operation.httpMethod} ${operation.path} ${operation.summary} ${operation.description}`
            .toLowerCase()
            .includes(query),
        );

        return matchingOperations.length > 0
          ? { ...controller, operations: matchingOperations }
          : null;
      })
      .filter((controller): controller is ApiDocController => controller !== null);
  }, [document, searchQuery]);

  const knowledgeTitleBySourcePath = useMemo(() => {
    if (!knowledgeIndex) {
      return new Map<string, string>();
    }

    return new Map(
      collectKnowledgeCategories(knowledgeIndex.tree).map((category) => [
        category.sourcePath,
        formatKnowledgeDisplayTitle(category.title),
      ]),
    );
  }, [knowledgeIndex]);

  const toggleController = (controllerName: string) => {
    setExpandedControllers((current) => {
      const next = new Set(current);
      if (next.has(controllerName)) {
        next.delete(controllerName);
      } else {
        next.add(controllerName);
      }
      return next;
    });
  };

  const toggleOperation = (operationId: string) => {
    setExpandedOperations((current) => {
      const next = new Set(current);
      if (next.has(operationId)) {
        next.delete(operationId);
      } else {
        next.add(operationId);
      }
      return next;
    });
  };

  if (!basePath) {
    return (
      <div className="empty-state empty-state--large">
        <strong>{language === 'vi' ? 'Module này chưa có API Docs' : 'This module has no API Docs yet'}</strong>
        <span>
          {language === 'vi'
            ? 'API Docs chỉ xuất hiện khi module có đầy đủ Swagger metadata được publish cho Portal.'
            : 'API Docs appear when the module publishes a complete Swagger metadata set for the Portal.'}
        </span>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="empty-state">
        <strong>{language === 'vi' ? 'Đang tải API Docs...' : 'Loading API Docs...'}</strong>
      </div>
    );
  }

  if (error) {
    return (
      <div className="empty-state empty-state--large">
        <strong>{language === 'vi' ? 'Không thể tải API Docs' : 'Unable to load API Docs'}</strong>
        <span>{error}</span>
      </div>
    );
  }

  if (!document || document.operationCount === 0) {
    return (
      <div className="empty-state">
        <strong>{language === 'vi' ? 'Không có API đang hoạt động' : 'No active APIs found'}</strong>
      </div>
    );
  }

  const queryActive = searchQuery.trim().length > 0;

  return (
    <section className="api-docs-panel">
      <div className="api-docs-toolbar">
        <div className="api-docs-toolbar__summary">
          <strong>{language === 'vi' ? 'API Reference' : 'API Reference'}</strong>
          <span>
            {document.controllers.length} {language === 'vi' ? 'controller' : 'controllers'} · {document.operationCount} APIs
          </span>
        </div>

        <div className="api-docs-toolbar__actions">
          <button
            type="button"
            onClick={() => {
              setExpandedControllers(new Set(document.controllers.map((controller) => controller.name)));
              setExpandedOperations(new Set(document.controllers.flatMap((controller) => controller.operations.map((operation) => operation.id))));
            }}
          >
            {language === 'vi' ? 'Mở tất cả' : 'Expand all'}
          </button>
          <button
            type="button"
            onClick={() => {
              setExpandedControllers(new Set());
              setExpandedOperations(new Set());
            }}
          >
            {language === 'vi' ? 'Thu gọn tất cả' : 'Collapse all'}
          </button>
        </div>
      </div>

      <div className="api-docs-notice">
        <div className="api-docs-notice__copy">
          <strong>{language === 'vi' ? 'Lưu ý khi sử dụng API Reference' : 'API Reference note'}</strong>
          <p>
            {language === 'vi'
              ? 'API Reference này chỉ dùng để tham khảo và hỗ trợ học tập. Bạn không thể chạy hoặc debug API trực tiếp tại Portal. Để thực thi và debug, hãy tải source code của module về và chạy trên máy local.'
              : 'This API Reference is provided for learning and reference only. APIs cannot be run or debugged directly in the Portal. To execute or debug them, download the module source code and run it locally.'}
          </p>
        </div>

        <DownloadAction
          open={downloadOpen}
          onToggle={onDownloadToggle}
          className="api-docs-notice__download"
        />
      </div>

      {visibleControllers.length === 0 ? (
        <div className="empty-state">
          <strong>{language === 'vi' ? 'Không tìm thấy API phù hợp' : 'No matching APIs found'}</strong>
        </div>
      ) : (
        <div className="api-controller-list">
          {visibleControllers.map((controller) => {
            const controllerOpen = queryActive || expandedControllers.has(controller.name);
            const knowledgeTitle = controller.readmeRelated.file
              ? knowledgeTitleBySourcePath.get(controller.readmeRelated.file)
              : undefined;

            return (
              <article key={controller.name} className={`api-controller${controllerOpen ? ' is-expanded' : ''}`}>
                <button
                  type="button"
                  className="api-controller__header"
                  aria-expanded={controllerOpen}
                  onClick={() => toggleController(controller.name)}
                >
                  <span className="api-controller__copy">
                    <span className="api-controller__title-row">
                      {knowledgeTitle && <span className="api-controller__knowledge">{knowledgeTitle}</span>}
                      <strong>{controller.title}</strong>
                    </span>
                    <span className="api-controller__name">{controller.name}</span>
                    {controller.description && <span className="api-controller__description">{controller.description}</span>}
                  </span>
                  <span className="api-controller__count">{controller.operations.length} APIs</span>
                </button>

                {controllerOpen && (
                  <div className="api-controller__operations">
                    {controller.operations.map((operation) => {
                      const operationOpen = expandedOperations.has(operation.id);
                      const sanitizedExecution = operation.executionHtml
                        ? DOMPurify.sanitize(operation.executionHtml)
                        : '';

                      return (
                        <article key={operation.id} className={`api-operation${operationOpen ? ' is-expanded' : ''}`}>
                          <div className="api-operation__header">
                            <button
                              type="button"
                              className="api-operation__toggle"
                              aria-expanded={operationOpen}
                              onClick={() => toggleOperation(operation.id)}
                            >
                              <span className={`http-method http-method--${operation.httpMethod.toLowerCase()}`}>
                                {operation.httpMethod}
                              </span>
                              <code>{operation.path || '—'}</code>
                              <span className="api-operation__copy">
                                <strong>{operation.summary}</strong>
                                <span>{operation.description}</span>
                              </span>
                            </button>
                          </div>

                          {operationOpen && (
                            <div className="api-operation__details">
                              <section className="api-operation__detail-section">
                                <h4>Execution</h4>
                                {sanitizedExecution ? (
                                  <div
                                    className="api-execution-content"
                                    dangerouslySetInnerHTML={{ __html: sanitizedExecution }}
                                  />
                                ) : (
                                  <p className="api-operation__empty-detail">
                                    {language === 'vi' ? 'Chưa có mô tả execution.' : 'No execution description yet.'}
                                  </p>
                                )}
                              </section>

                              {operation.params.length > 0 && (
                                <section className="api-operation__detail-section">
                                  <h4>{language === 'vi' ? 'Tham số' : 'Parameters'}</h4>
                                  <div className="api-param-list">
                                    {operation.params.map((param) => (
                                      <div key={param.name} className="api-param">
                                        <code>{param.name}</code>
                                        <span>
                                          <strong>{param.summary || param.name}</strong>
                                          {param.description && <small>{param.description}</small>}
                                        </span>
                                      </div>
                                    ))}
                                  </div>
                                </section>
                              )}
                            </div>
                          )}
                        </article>
                      );
                    })}
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
