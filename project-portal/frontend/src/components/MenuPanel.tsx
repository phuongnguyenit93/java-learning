import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { formatKnowledgeDisplayTitle } from '../data/knowledge';
import { useLanguage } from '../state/LanguageContext';
import type { KnowledgeCategoryNode, KnowledgeIndex, KnowledgeTreeNode } from '../types/learning';

interface MenuPanelProps {
  index: KnowledgeIndex | null;
  loading: boolean;
  error: string | null;
  searchQuery: string;
  selectedSectionId: string | null;
  onSelectSection: (categoryId: string, sectionId: string) => void;
}

interface MenuExpandRequest {
  version: number;
  expanded: boolean;
}

function categoryMatches(category: KnowledgeCategoryNode, query: string): boolean {
  if (!query) {
    return true;
  }

  return `${category.title} ${category.id} ${category.sourcePath}`.toLowerCase().includes(query);
}

function nodeHasVisibleContent(node: KnowledgeTreeNode, query: string): boolean {
  if (!query) {
    return true;
  }

  if (node.kind === 'CATEGORY') {
    if (categoryMatches(node, query)) {
      return true;
    }

    return node.sections.some((section) => `${section.title} ${section.id}`.toLowerCase().includes(query));
  }

  if (`${node.title} ${node.id}`.toLowerCase().includes(query)) {
    return true;
  }

  return node.children.some((child) => nodeHasVisibleContent(child, query));
}

function nodeContainsSelectedSection(node: KnowledgeTreeNode, selectedSectionId: string | null): boolean {
  if (!selectedSectionId) {
    return false;
  }

  if (node.kind === 'CATEGORY') {
    return node.sections.some((section) => section.id === selectedSectionId);
  }

  return node.children.some((child) => nodeContainsSelectedSection(child, selectedSectionId));
}

function MenuNode({
  node,
  depth,
  query,
  selectedSectionId,
  expandRequest,
  onSelectSection,
}: {
  node: KnowledgeTreeNode;
  depth: number;
  query: string;
  selectedSectionId: string | null;
  expandRequest: MenuExpandRequest;
  onSelectSection: (categoryId: string, sectionId: string) => void;
}) {
  const { language } = useLanguage();
  const [expanded, setExpanded] = useState(false);
  const visible = nodeHasVisibleContent(node, query);
  const selectedInsideNode = nodeContainsSelectedSection(node, selectedSectionId);

  useEffect(() => {
    if ((query && visible) || selectedInsideNode) {
      setExpanded(true);
    }
  }, [query, selectedInsideNode, visible]);

  useEffect(() => {
    setExpanded(expandRequest.expanded);
  }, [expandRequest]);

  if (!visible) {
    return null;
  }

  if (node.kind === 'FOLDER') {
    const folderMatches = !query || `${node.title} ${node.id}`.toLowerCase().includes(query);

    return (
      <li className="knowledge-menu__folder">
        <div className="knowledge-menu__group knowledge-menu__group--folder" style={{ '--menu-depth': depth } as CSSProperties}>
          <button
            type="button"
            className="knowledge-menu__toggle"
            onClick={() => setExpanded((current) => !current)}
            aria-expanded={expanded}
            aria-label={`${expanded ? 'Collapse' : 'Expand'} ${node.title}`}
          >
            <span aria-hidden="true">{expanded ? '−' : '+'}</span>
          </button>
          <strong>{formatKnowledgeDisplayTitle(node.title)}</strong>
        </div>
        {expanded && (
          <ul className="knowledge-menu__branch">
            {node.children.map((child) => (
              <MenuNode
                key={`${child.kind}-${child.id}`}
                node={child}
                depth={depth + 1}
                query={folderMatches ? '' : query}
                selectedSectionId={selectedSectionId}
                expandRequest={expandRequest}
                onSelectSection={onSelectSection}
              />
            ))}
          </ul>
        )}
      </li>
    );
  }

  const fullCategoryMatch = categoryMatches(node, query);
  const visibleSections = fullCategoryMatch
    ? node.sections
    : node.sections.filter((section) => `${section.title} ${section.id}`.toLowerCase().includes(query));

  if (visibleSections.length === 0 && query) {
    return null;
  }

  return (
    <li className="knowledge-menu__category">
      <div className="knowledge-menu__group knowledge-menu__group--category" style={{ '--menu-depth': depth } as CSSProperties}>
        <button
          type="button"
          className="knowledge-menu__toggle knowledge-menu__toggle--category"
          onClick={() => setExpanded((current) => !current)}
          aria-expanded={expanded}
          aria-label={`${expanded ? 'Collapse' : 'Expand'} ${node.title}`}
        >
          <span aria-hidden="true">{expanded ? '−' : '+'}</span>
        </button>
        <strong>{formatKnowledgeDisplayTitle(node.title)}</strong>
        <span className="knowledge-menu__count">{node.sections.length}</span>
      </div>
      {expanded && (
        <ul className="knowledge-menu__sections">
          {visibleSections.map((section) => (
            <li key={section.id}>
              <button
                type="button"
                className={`knowledge-menu__section${selectedSectionId === section.id ? ' is-active' : ''}`}
                style={{ '--menu-depth': depth + 1 } as CSSProperties}
                onClick={() => onSelectSection(node.id, section.id)}
              >
                <span className="knowledge-menu__section-dot" aria-hidden="true">•</span>
                <span className="knowledge-menu__section-copy">
                  <span className="knowledge-menu__section-title">{formatKnowledgeDisplayTitle(section.title)}</span>
                  <span className="knowledge-menu__section-hint">
                    {language === 'vi' ? 'Nhấn vào để học' : 'Click to learn'}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </li>
  );
}

export function MenuPanel({
  index,
  loading,
  error,
  searchQuery,
  selectedSectionId,
  onSelectSection,
}: MenuPanelProps) {
  const { language } = useLanguage();
  const query = useMemo(() => searchQuery.trim().toLowerCase(), [searchQuery]);
  const [expandRequest, setExpandRequest] = useState<MenuExpandRequest>({ version: 0, expanded: false });

  if (loading) {
    return <div className="empty-state empty-state--large"><strong>{language === 'vi' ? 'Đang tải Menu...' : 'Loading Menu...'}</strong></div>;
  }

  if (error) {
    return (
      <div className="empty-state empty-state--large">
        <strong>{language === 'vi' ? 'Không thể tải Menu' : 'Unable to load Menu'}</strong>
        <span>{error}</span>
      </div>
    );
  }

  if (!index) {
    return (
      <div className="empty-state empty-state--large">
        <strong>{language === 'vi' ? 'Chưa có dữ liệu Knowledge' : 'No Knowledge data yet'}</strong>
      </div>
    );
  }

  const hasVisibleNode = index.tree.some((node) => nodeHasVisibleContent(node, query));

  return (
    <section className="knowledge-menu-panel">
      <div className="knowledge-menu-panel__toolbar">
        <div className="knowledge-menu-panel__summary">
          <span>{index.categoryCount} {language === 'vi' ? 'nhóm' : 'categories'}</span>
          <span>{index.sectionCount} sections</span>
        </div>

        <div className="knowledge-menu-panel__actions">
          <button
            type="button"
            onClick={() => setExpandRequest((current) => ({ version: current.version + 1, expanded: true }))}
          >
            {language === 'vi' ? 'Mở tất cả' : 'Expand all'}
          </button>
          <button
            type="button"
            onClick={() => setExpandRequest((current) => ({ version: current.version + 1, expanded: false }))}
          >
            {language === 'vi' ? 'Thu gọn tất cả' : 'Collapse all'}
          </button>
        </div>
      </div>

      {hasVisibleNode ? (
        <ul className="knowledge-menu">
          {index.tree.map((node) => (
            <MenuNode
              key={`${node.kind}-${node.id}`}
              node={node}
              depth={0}
              query={query}
              selectedSectionId={selectedSectionId}
              expandRequest={expandRequest}
              onSelectSection={onSelectSection}
            />
          ))}
        </ul>
      ) : (
        <div className="empty-state">
          <strong>{language === 'vi' ? 'Không tìm thấy section phù hợp' : 'No matching section found'}</strong>
        </div>
      )}
    </section>
  );
}
