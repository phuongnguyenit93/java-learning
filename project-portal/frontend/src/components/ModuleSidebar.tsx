import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { moduleTree } from '../data/mockLearningData';
import { useLanguage } from '../state/LanguageContext';
import type { ModuleTreeNode } from '../types/learning';

interface ModuleSidebarProps {
  activeModuleId: string;
}

interface TreeNodeProps {
  node: ModuleTreeNode;
  depth: number;
  activeModuleId: string;
  filter: string;
}

function nodeMatches(node: ModuleTreeNode, filter: string, language: 'vi' | 'en'): boolean {
  if (!filter) {
    return true;
  }

  if (node.label[language].toLowerCase().includes(filter)) {
    return true;
  }

  return node.children?.some((child) => nodeMatches(child, filter, language)) ?? false;
}

function TreeNode({ node, depth, activeModuleId, filter }: TreeNodeProps) {
  const navigate = useNavigate();
  const { language } = useLanguage();
  const hasChildren = Boolean(node.children?.length);
  const [expanded, setExpanded] = useState(depth < 2);

  if (!nodeMatches(node, filter, language)) {
    return null;
  }

  const open = filter ? true : expanded;
  const isActive = node.moduleId === activeModuleId;

  const handleClick = () => {
    if (node.moduleId) {
      navigate(`/learning/${node.moduleId}`);
      return;
    }

    if (hasChildren) {
      setExpanded((current) => !current);
    }
  };

  return (
    <li className="module-tree__item">
      <button
        type="button"
        className={`module-tree__button${isActive ? ' is-active' : ''}`}
        onClick={handleClick}
      >
        <span className="module-tree__indent" data-depth={Math.min(depth, 5)} />
        <span className="module-tree__caret" aria-hidden="true">{hasChildren ? (open ? '⌄' : '›') : '•'}</span>
        <span className="module-tree__label">{node.label[language]}</span>
        {node.count !== undefined && <span className="module-tree__count">{node.count}</span>}
      </button>

      {hasChildren && open && (
        <ul className="module-tree__branch">
          {node.children!.map((child) => (
            <TreeNode
              key={child.id}
              node={child}
              depth={depth + 1}
              activeModuleId={activeModuleId}
              filter={filter}
            />
          ))}
        </ul>
      )}
    </li>
  );
}

export function ModuleSidebar({ activeModuleId }: ModuleSidebarProps) {
  const { language } = useLanguage();
  const [filterInput, setFilterInput] = useState('');
  const filter = useMemo(() => filterInput.trim().toLowerCase(), [filterInput]);

  return (
    <aside className="learning-sidebar">
      <div className="learning-sidebar__headline">
        <span>{language === 'vi' ? 'Tất cả module' : 'All modules'}</span>
        <span className="learning-sidebar__total">{moduleTree.length}</span>
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

      <div className="learning-sidebar__section-title">
        {language === 'vi' ? 'CẤU TRÚC HỌC TẬP' : 'LEARNING STRUCTURE'}
      </div>

      <ul className="module-tree">
        {moduleTree.map((node) => (
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

