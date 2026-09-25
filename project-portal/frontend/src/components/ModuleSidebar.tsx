import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { collectRealModules, formatCatalogName } from '../data/moduleCatalog';
import { resolveModuleStats } from '../data/moduleStats';
import { useLanguage } from '../state/LanguageContext';
import type { ModuleCatalogNode } from '../types/learning';

interface ModuleSidebarProps {
  nodes: ModuleCatalogNode[];
  activeModuleId: string;
  knowledgeCounts: Record<string, number>;
  quizCounts: Record<string, number>;
  interviewCounts: Record<string, number>;
  apiCounts: Record<string, number>;
}

interface TreeNodeProps {
  node: ModuleCatalogNode;
  depth: number;
  activeModuleId: string;
  knowledgeCounts: Record<string, number>;
  quizCounts: Record<string, number>;
  interviewCounts: Record<string, number>;
  apiCounts: Record<string, number>;
  filter: string;
  expandRequest: SidebarExpandRequest;
  showEntireSubtree?: boolean;
}

interface SidebarExpandRequest {
  version: number;
  expanded: boolean;
}

function nodeMatchesSelf(node: ModuleCatalogNode, filter: string): boolean {
  if (!filter) {
    return true;
  }

  const searchable = [
    formatCatalogName(node.name),
    node.name,
    node.routeId,
    node.serviceName,
    node.description,
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();

  return searchable.includes(filter);
}

function nodeContainsMatch(node: ModuleCatalogNode, filter: string): boolean {
  if (!filter) {
    return true;
  }

  if (nodeMatchesSelf(node, filter)) {
    return true;
  }

  return node.children.some((child) => nodeContainsMatch(child, filter));
}

function isQualifiedRealModule(
  node: ModuleCatalogNode,
  knowledgeCounts: Record<string, number>,
  quizCounts: Record<string, number>,
  interviewCounts: Record<string, number>,
  apiCounts: Record<string, number>,
): boolean {
  if (node.kind !== 'MODULE' || !node.routeId) {
    return false;
  }

  return (knowledgeCounts[node.routeId] ?? 0) > 0
    || (quizCounts[node.routeId] ?? 0) > 0
    || (interviewCounts[node.routeId] ?? 0) > 0
    || (apiCounts[node.routeId] ?? 0) > 0;
}

function projectRealModuleNodes(
  nodes: ModuleCatalogNode[],
  knowledgeCounts: Record<string, number>,
  quizCounts: Record<string, number>,
  interviewCounts: Record<string, number>,
  apiCounts: Record<string, number>,
): ModuleCatalogNode[] {
  const result: ModuleCatalogNode[] = [];

  nodes.forEach((node) => {
    const projectedChildren = projectRealModuleNodes(node.children, knowledgeCounts, quizCounts, interviewCounts, apiCounts);
    const keepModule = isQualifiedRealModule(node, knowledgeCounts, quizCounts, interviewCounts, apiCounts);

    if (keepModule || projectedChildren.length > 0) {
      result.push({
        ...node,
        children: projectedChildren,
      });
    }
  });

  return result;
}

function TreeNode({
  node,
  depth,
  activeModuleId,
  knowledgeCounts,
  quizCounts,
  interviewCounts,
  apiCounts,
  filter,
  expandRequest,
  showEntireSubtree = false,
}: TreeNodeProps) {
  const navigate = useNavigate();
  const hasChildren = node.children.length > 0;
  const [expanded, setExpanded] = useState(expandRequest.expanded);
  const [searchExpanded, setSearchExpanded] = useState(expandRequest.expanded);
  const selfMatches = nodeMatchesSelf(node, filter);
  const containsMatch = nodeContainsMatch(node, filter);
  const visible = !filter || showEntireSubtree || containsMatch;
  const childShowEntireSubtree = showEntireSubtree || (Boolean(filter) && selfMatches);
  const open = filter ? searchExpanded : expanded;
  const isModule = node.kind === 'MODULE' && Boolean(node.routeId);
  const isActive = isModule && node.routeId === activeModuleId;
  const stats = isModule && node.routeId ? resolveModuleStats(node.routeId) : null;
  const knowledgeCount = isModule && node.routeId ? (knowledgeCounts[node.routeId] ?? 0) : 0;
  const quizCount = isModule && node.routeId ? (quizCounts[node.routeId] ?? 0) : 0;
  const interviewCount = isModule && node.routeId ? (interviewCounts[node.routeId] ?? 0) : 0;
  const apiCount = isModule && node.routeId ? (apiCounts[node.routeId] ?? 0) : 0;
  const isRealModule = isQualifiedRealModule(node, knowledgeCounts, quizCounts, interviewCounts, apiCounts);
  const displayName = formatCatalogName(node.name);

  useEffect(() => {
    if (filter && visible && hasChildren) {
      setSearchExpanded(true);
    }
  }, [filter, visible, hasChildren]);

  useEffect(() => {
    setExpanded(expandRequest.expanded);
    setSearchExpanded(expandRequest.expanded);
  }, [expandRequest]);

  if (!visible) {
    return null;
  }

  const toggleChildren = () => {
    if (hasChildren) {
      if (filter) {
        setSearchExpanded((current) => !current);
      } else {
        setExpanded((current) => !current);
      }
    }
  };

  const navigateToModule = () => {
    if (isModule && node.routeId) {
      navigate(`/learning/${node.routeId}`);
    }
  };

  const content = (
    <span className={`module-tree__content${isActive ? ' is-active' : ''}`}>
      <span className="module-tree__label">{displayName}</span>

      {stats && (
        <span className="module-tree__badges" aria-label="Module content counts">
          {knowledgeCount > 0 && (
            <span className="module-tree__badge module-tree__badge--knowledge" title="Knowledge">
              {knowledgeCount}
            </span>
          )}
          {quizCount > 0 && (
            <span className="module-tree__badge module-tree__badge--quiz" title="Quiz">
              {quizCount}
            </span>
          )}
          {interviewCount > 0 && (
            <span className="module-tree__badge module-tree__badge--interview" title="Interview">
              {interviewCount}
            </span>
          )}
          {apiCount > 0 && (
            <span className="module-tree__badge module-tree__badge--api" title="API Docs">
              {apiCount}
            </span>
          )}
        </span>
      )}
    </span>
  );

  return (
    <li className="module-tree__item">
      <div
        className={`module-tree__row${hasChildren ? ' is-expandable' : ''}${isModule ? ' is-module' : ''}${isRealModule ? ' is-real-module' : ''}${isActive ? ' is-active' : ''}`}
      >
        {hasChildren && (
          <button
            type="button"
            className={`module-tree__collapse-zone${isModule ? ' is-split' : ' is-full'}${open ? ' is-open' : ' is-closed'}`}
            onClick={toggleChildren}
            aria-expanded={open}
            aria-label={`${open ? 'Collapse' : 'Expand'} ${displayName}`}
            title={`${open ? 'Collapse' : 'Expand'} ${displayName}`}
          >
            <span className="module-tree__indent" data-depth={Math.min(depth, 5)} />
            <span className="module-tree__vertical-cue" aria-hidden="true">{open ? '↑↑↑' : '↓↓↓'}</span>
            <span className="module-tree__caret" aria-hidden="true">{open ? '−' : '+'}</span>
            {!isModule && content}
          </button>
        )}

        {isModule && (
          <button
            type="button"
            className={`module-tree__dashboard-zone${hasChildren ? ' is-split' : ' is-full'}`}
            onClick={navigateToModule}
            aria-label={`Open ${displayName} dashboard`}
            title={`Open ${displayName}`}
          >
            {!hasChildren && (
              <>
                <span className="module-tree__indent" data-depth={Math.min(depth, 5)} />
                <span className="module-tree__caret module-tree__caret--leaf" aria-hidden="true">•</span>
              </>
            )}
            {content}
            <span className="module-tree__dashboard-cue" aria-hidden="true">›››</span>
          </button>
        )}

        {!hasChildren && !isModule && (
          <div className="module-tree__static-zone">
            <span className="module-tree__indent" data-depth={Math.min(depth, 5)} />
            <span className="module-tree__caret module-tree__caret--leaf" aria-hidden="true">•</span>
            {content}
          </div>
        )}
      </div>

      {hasChildren && open && (
        <ul className="module-tree__branch">
          {node.children.map((child) => (
            <TreeNode
              key={child.id}
              node={child}
              depth={depth + 1}
              activeModuleId={activeModuleId}
              knowledgeCounts={knowledgeCounts}
              quizCounts={quizCounts}
              interviewCounts={interviewCounts}
              apiCounts={apiCounts}
              filter={filter}
              expandRequest={expandRequest}
              showEntireSubtree={childShowEntireSubtree}
            />
          ))}
        </ul>
      )}
    </li>
  );
}

export function ModuleSidebar({
  nodes,
  activeModuleId,
  knowledgeCounts,
  quizCounts,
  interviewCounts,
  apiCounts,
}: ModuleSidebarProps) {
  const { language } = useLanguage();
  const [filterInput, setFilterInput] = useState('');
  const [realModulesOnly, setRealModulesOnly] = useState(true);
  const [expandRequest, setExpandRequest] = useState<SidebarExpandRequest>({ version: 0, expanded: true });
  const filter = useMemo(() => filterInput.trim().toLowerCase(), [filterInput]);
  const moduleCount = useMemo(
    () => nodes.reduce((count, node) => count + collectRealModules(node).length, 0),
    [nodes],
  );
  const realModuleCount = useMemo(
    () => nodes.reduce(
      (count, node) => count + collectRealModules(node)
        .filter((moduleNode) => isQualifiedRealModule(moduleNode, knowledgeCounts, quizCounts, interviewCounts, apiCounts)).length,
      0,
    ),
    [apiCounts, interviewCounts, knowledgeCounts, nodes, quizCounts],
  );
  const visibleNodes = useMemo(
    () => (realModulesOnly ? projectRealModuleNodes(nodes, knowledgeCounts, quizCounts, interviewCounts, apiCounts) : nodes),
    [apiCounts, interviewCounts, knowledgeCounts, nodes, quizCounts, realModulesOnly],
  );

  return (
    <aside className="learning-sidebar">
      <div className="learning-sidebar__headline">
        <span className="learning-sidebar__headline-copy">
          <span>{language === 'vi' ? 'Tất cả module' : 'All modules'}</span>
          <span className="learning-sidebar__total">{realModulesOnly ? realModuleCount : moduleCount}</span>
        </span>
      </div>

      <label className="sidebar-search">
        <span aria-hidden="true">⌕</span>
        <input
          type="search"
          value={filterInput}
          onChange={(event) => setFilterInput(event.target.value)}
          placeholder={language === 'vi' ? 'Lọc module...' : 'Filter modules...'}
        />
      </label>

      <div className="sidebar-module-mode" aria-label={language === 'vi' ? 'Chế độ hiển thị module' : 'Module display mode'}>
        <span className={!realModulesOnly ? 'is-active' : undefined}>
          {language === 'vi' ? 'Đầy đủ' : 'Full tree'}
        </span>
        <button
          type="button"
          className={`sidebar-module-mode__switch${realModulesOnly ? ' is-on' : ''}`}
          role="switch"
          aria-checked={realModulesOnly}
          aria-label={language === 'vi' ? 'Chỉ hiển thị module thật' : 'Show real modules only'}
          onClick={() => setRealModulesOnly((current) => !current)}
        >
          <span className="sidebar-module-mode__thumb" />
        </button>
        <span className={realModulesOnly ? 'is-active' : undefined}>
          {language === 'vi' ? 'Module thật' : 'Real modules'}
        </span>

        <div className="sidebar-module-mode__tree-actions" aria-label={language === 'vi' ? 'Điều khiển cây module' : 'Module tree controls'}>
          <button
            type="button"
            onClick={() => setExpandRequest((current) => ({ version: current.version + 1, expanded: true }))}
            aria-label={language === 'vi' ? 'Mở tất cả module' : 'Expand all modules'}
            title={language === 'vi' ? 'Mở tất cả' : 'Expand all'}
          >
            + All
          </button>
          <button
            type="button"
            onClick={() => setExpandRequest((current) => ({ version: current.version + 1, expanded: false }))}
            aria-label={language === 'vi' ? 'Thu gọn tất cả module' : 'Collapse all modules'}
            title={language === 'vi' ? 'Thu gọn tất cả' : 'Collapse all'}
          >
            − All
          </button>
        </div>
      </div>

      <div className="learning-sidebar__section-title">
        {language === 'vi' ? 'CẤU TRÚC HỌC TẬP' : 'LEARNING STRUCTURE'}
      </div>

      <ul className="module-tree">
        {visibleNodes.map((node) => (
          <TreeNode
            key={node.id}
            node={node}
            depth={0}
            activeModuleId={activeModuleId}
            knowledgeCounts={knowledgeCounts}
            quizCounts={quizCounts}
            interviewCounts={interviewCounts}
            apiCounts={apiCounts}
            filter={filter}
            expandRequest={expandRequest}
          />
        ))}
      </ul>
    </aside>
  );
}
