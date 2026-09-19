import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { collectRealModules, formatCatalogName } from '../data/moduleCatalog';
import { resolveModuleStats } from '../data/moduleStats';
import { useLanguage } from '../state/LanguageContext';
import type { ModuleCatalogNode } from '../types/learning';

interface ModuleSidebarProps {
  nodes: ModuleCatalogNode[];
  activeModuleId: string;
}

interface TreeNodeProps {
  node: ModuleCatalogNode;
  depth: number;
  activeModuleId: string;
  filter: string;
  showEntireSubtree?: boolean;
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

function projectRealModuleNodes(nodes: ModuleCatalogNode[]): ModuleCatalogNode[] {
  const result: ModuleCatalogNode[] = [];

  nodes.forEach((node) => {
    const projectedChildren = projectRealModuleNodes(node.children);

    if (node.kind === 'MODULE' || projectedChildren.length > 0) {
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
  filter,
  showEntireSubtree = false,
}: TreeNodeProps) {
  const navigate = useNavigate();
  const hasChildren = node.children.length > 0;
  const [expanded, setExpanded] = useState(depth < 2);
  const [searchExpanded, setSearchExpanded] = useState(false);
  const selfMatches = nodeMatchesSelf(node, filter);
  const containsMatch = nodeContainsMatch(node, filter);
  const visible = !filter || showEntireSubtree || containsMatch;
  const childShowEntireSubtree = showEntireSubtree || (Boolean(filter) && selfMatches);
  const open = filter ? searchExpanded : expanded;
  const isModule = node.kind === 'MODULE' && Boolean(node.routeId);
  const isActive = isModule && node.routeId === activeModuleId;
  const stats = isModule && node.routeId ? resolveModuleStats(node.routeId) : null;

  useEffect(() => {
    if (filter && visible && hasChildren) {
      setSearchExpanded(true);
    }
  }, [filter, visible, hasChildren]);

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

  return (
    <li className="module-tree__item">
      <div
        className={`module-tree__row${hasChildren ? ' is-expandable' : ''}${isModule ? ' is-module' : ''}${isActive ? ' is-active' : ''}`}
        onClick={hasChildren ? toggleChildren : undefined}
        onKeyDown={(event) => {
          if (!hasChildren) {
            return;
          }

          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            toggleChildren();
          }
        }}
        role={hasChildren ? 'button' : undefined}
        tabIndex={hasChildren ? 0 : undefined}
        aria-expanded={hasChildren ? open : undefined}
      >
        <span className="module-tree__indent" data-depth={Math.min(depth, 5)} />
        <span className="module-tree__caret" aria-hidden="true">{hasChildren ? (open ? '⌄' : '›') : '•'}</span>
        <span className={`module-tree__content${isActive ? ' is-active' : ''}`}>
          <span className="module-tree__label">{formatCatalogName(node.name)}</span>

          {stats && (
            <span className="module-tree__badges" aria-label="Module content counts">
              {stats.knowledge > 0 && (
                <span className="module-tree__badge module-tree__badge--knowledge" title="Knowledge">
                  {stats.knowledge}
                </span>
              )}
              {stats.quiz > 0 && (
                <span className="module-tree__badge module-tree__badge--quiz" title="Quiz">
                  {stats.quiz}
                </span>
              )}
              {stats.apiDocs > 0 && (
                <span className="module-tree__badge module-tree__badge--api" title="API Docs">
                  {stats.apiDocs}
                </span>
              )}
            </span>
          )}
        </span>

        {isModule && (
          <button
            type="button"
            className="module-tree__navigate-button"
            onClick={(event) => {
              event.stopPropagation();
              navigateToModule();
            }}
            aria-label={`Open ${formatCatalogName(node.name)}`}
            title={`Open ${formatCatalogName(node.name)}`}
          >
            ›
          </button>
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
              filter={filter}
              showEntireSubtree={childShowEntireSubtree}
            />
          ))}
        </ul>
      )}
    </li>
  );
}

export function ModuleSidebar({ nodes, activeModuleId }: ModuleSidebarProps) {
  const { language } = useLanguage();
  const [filterInput, setFilterInput] = useState('');
  const [realModulesOnly, setRealModulesOnly] = useState(false);
  const filter = useMemo(() => filterInput.trim().toLowerCase(), [filterInput]);
  const moduleCount = useMemo(
    () => nodes.reduce((count, node) => count + collectRealModules(node).length, 0),
    [nodes],
  );
  const visibleNodes = useMemo(
    () => (realModulesOnly ? projectRealModuleNodes(nodes) : nodes),
    [nodes, realModulesOnly],
  );

  return (
    <aside className="learning-sidebar">
      <div className="learning-sidebar__headline">
        <span>{language === 'vi' ? 'Tất cả module' : 'All modules'}</span>
        <span className="learning-sidebar__total">{moduleCount}</span>
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
            filter={filter}
          />
        ))}
      </ul>
    </aside>
  );
}
