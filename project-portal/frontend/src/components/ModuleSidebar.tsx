import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { collectRealModules, formatCatalogName } from '../data/moduleCatalog';
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
  renderAsPicker?: boolean;
}

interface SidebarExpandRequest {
  version: number;
  expanded: boolean;
}

interface ModulePickerEntry {
  node: ModuleCatalogNode;
  sequence: number;
}

interface ModulePickerPosition {
  top: number;
  left: number;
  width: number;
  maxHeight: number;
}

const MODULE_PICKER_OPEN_EVENT = 'java-learning:module-picker-open';

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

function isTerminalModuleGroup(node: ModuleCatalogNode): boolean {
  return node.kind === 'GROUP'
    && node.children.length > 0
    && node.children.every(
      (child) => child.kind === 'MODULE' && Boolean(child.routeId) && child.children.length === 0,
    );
}

function hasMixedDirectChildren(node: ModuleCatalogNode): boolean {
  const hasGroup = node.children.some((child) => child.kind === 'GROUP');
  const hasModule = node.children.some((child) => child.kind === 'MODULE');

  return hasGroup && hasModule;
}

function isLeafModule(node: ModuleCatalogNode): boolean {
  return node.kind === 'MODULE' && Boolean(node.routeId) && node.children.length === 0;
}

function ModuleBadges({
  routeId,
  knowledgeCounts,
  quizCounts,
  interviewCounts,
  apiCounts,
}: {
  routeId: string;
  knowledgeCounts: Record<string, number>;
  quizCounts: Record<string, number>;
  interviewCounts: Record<string, number>;
  apiCounts: Record<string, number>;
}) {
  const knowledgeCount = knowledgeCounts[routeId] ?? 0;
  const quizCount = quizCounts[routeId] ?? 0;
  const interviewCount = interviewCounts[routeId] ?? 0;
  const apiCount = apiCounts[routeId] ?? 0;

  if (knowledgeCount === 0 && quizCount === 0 && interviewCount === 0 && apiCount === 0) {
    return null;
  }

  return (
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
  );
}

function ModulePickerPopup({
  group,
  entries,
  position,
  activeModuleId,
  knowledgeCounts,
  quizCounts,
  interviewCounts,
  apiCounts,
  popupRef,
  onMouseEnter,
  onMouseLeave,
  onClose,
}: {
  group: ModuleCatalogNode;
  entries: ModulePickerEntry[];
  position: ModulePickerPosition;
  activeModuleId: string;
  knowledgeCounts: Record<string, number>;
  quizCounts: Record<string, number>;
  interviewCounts: Record<string, number>;
  apiCounts: Record<string, number>;
  popupRef: React.RefObject<HTMLDivElement | null>;
  onMouseEnter: () => void;
  onMouseLeave: () => void;
  onClose: () => void;
}) {
  const navigate = useNavigate();
  const { language } = useLanguage();
  const groupName = formatCatalogName(group.name);

  return createPortal(
    <div
      ref={popupRef}
      className="module-picker"
      style={{
        top: position.top,
        left: position.left,
        width: position.width,
        maxHeight: position.maxHeight,
      }}
      role="dialog"
      aria-label={`${groupName} modules`}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    >
      <div className="module-picker__header">
        <div>
          <div className="module-picker__eyebrow">
            {language === 'vi' ? 'THỨ TỰ MODULE' : 'MODULE ORDER'}
          </div>
          <div className="module-picker__title">{groupName}</div>
        </div>
        <span className="module-picker__total">
          {entries.length} {language === 'vi' ? 'module' : 'modules'}
        </span>
      </div>

      <div className="module-picker__list">
        {entries.map(({ node: moduleNode, sequence }) => {
          const routeId = moduleNode.routeId;

          if (!routeId) {
            return null;
          }

          const active = routeId === activeModuleId;

          return (
            <button
              key={moduleNode.id}
              type="button"
              className={`module-picker__item${active ? ' is-active' : ''}`}
              onClick={() => {
                onClose();
                navigate(`/learning/${routeId}`);
              }}
            >
              <span className="module-picker__sequence" aria-label={`Order ${sequence}`}>
                {sequence}
              </span>
              <span className="module-picker__module-name">{formatCatalogName(moduleNode.name)}</span>
              <ModuleBadges
                routeId={routeId}
                knowledgeCounts={knowledgeCounts}
                quizCounts={quizCounts}
                interviewCounts={interviewCounts}
                apiCounts={apiCounts}
              />
              <span className="module-picker__open-cue" aria-hidden="true">›</span>
            </button>
          );
        })}
      </div>
    </div>,
    document.body,
  );
}

function ModulePickerRow({
  group,
  entries,
  depth,
  activeModuleId,
  knowledgeCounts,
  quizCounts,
  interviewCounts,
  apiCounts,
}: {
  group: ModuleCatalogNode;
  entries: ModulePickerEntry[];
  depth: number;
  activeModuleId: string;
  knowledgeCounts: Record<string, number>;
  quizCounts: Record<string, number>;
  interviewCounts: Record<string, number>;
  apiCounts: Record<string, number>;
}) {
  const [pickerVisible, setPickerVisible] = useState(false);
  const [pickerPinned, setPickerPinned] = useState(false);
  const [pickerPosition, setPickerPosition] = useState<ModulePickerPosition | null>(null);
  const pickerAnchorRef = useRef<HTMLButtonElement>(null);
  const pickerPopupRef = useRef<HTMLDivElement>(null);
  const pickerCloseTimerRef = useRef<number | null>(null);
  const displayName = formatCatalogName(group.name);
  const containsActiveModule = entries.some(({ node }) => node.routeId === activeModuleId);

  const clearPickerCloseTimer = useCallback(() => {
    if (pickerCloseTimerRef.current !== null) {
      window.clearTimeout(pickerCloseTimerRef.current);
      pickerCloseTimerRef.current = null;
    }
  }, []);

  const closePicker = useCallback(() => {
    clearPickerCloseTimer();
    setPickerVisible(false);
    setPickerPinned(false);
  }, [clearPickerCloseTimer]);

  const updatePickerPosition = useCallback(() => {
    const anchor = pickerAnchorRef.current;

    if (!anchor) {
      return;
    }

    const rect = anchor.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    const mobile = viewportWidth <= 820;
    const width = mobile
      ? Math.max(280, viewportWidth - 24)
      : Math.min(580, Math.max(360, viewportWidth - rect.right - 22));
    const left = mobile
      ? 12
      : Math.max(12, Math.min(rect.right + 10, viewportWidth - width - 12));
    const top = mobile
      ? 12
      : Math.max(12, Math.min(rect.top - 24, viewportHeight - 540));

    setPickerPosition({
      top,
      left,
      width,
      maxHeight: Math.max(240, viewportHeight - top - 12),
    });
  }, []);

  const openPicker = useCallback(() => {
    clearPickerCloseTimer();
    window.dispatchEvent(new CustomEvent<string>(MODULE_PICKER_OPEN_EVENT, { detail: group.id }));
    updatePickerPosition();
    setPickerVisible(true);
  }, [clearPickerCloseTimer, group.id, updatePickerPosition]);

  const schedulePickerClose = useCallback(() => {
    if (pickerPinned) {
      return;
    }

    clearPickerCloseTimer();
    pickerCloseTimerRef.current = window.setTimeout(() => {
      setPickerVisible(false);
    }, 180);
  }, [clearPickerCloseTimer, pickerPinned]);

  useEffect(() => {
    closePicker();
  }, [activeModuleId, closePicker]);

  useEffect(() => {
    const handleOtherPickerOpen = (event: Event) => {
      const pickerEvent = event as CustomEvent<string>;

      if (pickerEvent.detail !== group.id) {
        closePicker();
      }
    };

    window.addEventListener(MODULE_PICKER_OPEN_EVENT, handleOtherPickerOpen);

    return () => {
      window.removeEventListener(MODULE_PICKER_OPEN_EVENT, handleOtherPickerOpen);
    };
  }, [closePicker, group.id]);

  useEffect(() => {
    if (!pickerVisible) {
      return undefined;
    }

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target;

      if (!(target instanceof Node)) {
        return;
      }

      if (pickerAnchorRef.current?.contains(target) || pickerPopupRef.current?.contains(target)) {
        return;
      }

      closePicker();
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        closePicker();
        pickerAnchorRef.current?.focus();
      }
    };

    const handleViewportChange = () => {
      updatePickerPosition();
    };

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    window.addEventListener('resize', handleViewportChange);
    window.addEventListener('scroll', handleViewportChange, true);

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('resize', handleViewportChange);
      window.removeEventListener('scroll', handleViewportChange, true);
    };
  }, [closePicker, pickerVisible, updatePickerPosition]);

  useEffect(() => () => clearPickerCloseTimer(), [clearPickerCloseTimer]);

  return (
    <li className="module-tree__item">
      <div className={`module-tree__row is-module-picker${containsActiveModule ? ' is-active' : ''}`}>
        <button
          ref={pickerAnchorRef}
          type="button"
          className={`module-tree__picker-zone${pickerVisible ? ' is-open' : ''}`}
          onMouseEnter={openPicker}
          onMouseLeave={schedulePickerClose}
          onFocus={openPicker}
          onBlur={schedulePickerClose}
          onClick={() => {
            clearPickerCloseTimer();

            if (pickerPinned) {
              closePicker();
              return;
            }

            window.dispatchEvent(new CustomEvent<string>(MODULE_PICKER_OPEN_EVENT, { detail: group.id }));
            updatePickerPosition();
            setPickerPinned(true);
            setPickerVisible(true);
          }}
          aria-haspopup="dialog"
          aria-expanded={pickerVisible}
          aria-label={`Open ${displayName} modules`}
          title={`Open ${displayName} modules`}
        >
          <span className="module-tree__indent" data-depth={Math.min(depth, 5)} />
          <span className="module-tree__vertical-cue module-tree__vertical-cue--placeholder" aria-hidden="true" />
          <span className="module-tree__caret" aria-hidden="true">›</span>
          <span className={`module-tree__content${containsActiveModule ? ' is-active' : ''}`}>
            <span className="module-tree__label">{displayName}</span>
          </span>
          <span className="module-tree__count">{entries.length}</span>
          <span className="module-tree__picker-cue" aria-hidden="true">›</span>
        </button>
      </div>

      {pickerVisible && pickerPosition && (
        <ModulePickerPopup
          group={group}
          entries={entries}
          position={pickerPosition}
          activeModuleId={activeModuleId}
          knowledgeCounts={knowledgeCounts}
          quizCounts={quizCounts}
          interviewCounts={interviewCounts}
          apiCounts={apiCounts}
          popupRef={pickerPopupRef}
          onMouseEnter={openPicker}
          onMouseLeave={schedulePickerClose}
          onClose={closePicker}
        />
      )}
    </li>
  );
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
  renderAsPicker = false,
}: TreeNodeProps) {
  const navigate = useNavigate();
  const hasChildren = node.children.length > 0;
  const terminalModuleGroup = isTerminalModuleGroup(node);
  const hasInlineChildren = hasChildren && !terminalModuleGroup;
  const mixedDirectChildren = hasMixedDirectChildren(node);
  const [expanded, setExpanded] = useState(expandRequest.expanded);
  const [searchExpanded, setSearchExpanded] = useState(expandRequest.expanded);
  const selfMatches = nodeMatchesSelf(node, filter);
  const containsMatch = nodeContainsMatch(node, filter);
  const visible = !filter || showEntireSubtree || containsMatch;
  const childShowEntireSubtree = showEntireSubtree || (Boolean(filter) && selfMatches);
  const open = filter ? searchExpanded : expanded;
  const isModule = node.kind === 'MODULE' && Boolean(node.routeId);
  const isActive = isModule && node.routeId === activeModuleId;
  const structuralModule = isModule && hasInlineChildren;
  const isRealModule = isQualifiedRealModule(node, knowledgeCounts, quizCounts, interviewCounts, apiCounts);
  const displayName = formatCatalogName(node.name);
  const pickerEntries = useMemo<ModulePickerEntry[]>(() => {
    const ordered = node.children.map((child, index) => ({ node: child, sequence: index + 1 }));

    if (!filter || showEntireSubtree || selfMatches) {
      return ordered;
    }

    return ordered.filter(({ node: child }) => nodeContainsMatch(child, filter));
  }, [filter, node.children, selfMatches, showEntireSubtree]);

  useEffect(() => {
    if (filter && visible && hasInlineChildren) {
      setSearchExpanded(true);
    }
  }, [filter, visible, hasInlineChildren]);

  useEffect(() => {
    setExpanded(expandRequest.expanded);
    setSearchExpanded(expandRequest.expanded);
  }, [expandRequest]);

  if (!visible) {
    return null;
  }

  if (renderAsPicker && isLeafModule(node)) {
    return (
      <ModulePickerRow
        group={node}
        entries={[{ node, sequence: 1 }]}
        depth={depth}
        activeModuleId={activeModuleId}
        knowledgeCounts={knowledgeCounts}
        quizCounts={quizCounts}
        interviewCounts={interviewCounts}
        apiCounts={apiCounts}
      />
    );
  }

  if (terminalModuleGroup) {
    return (
      <ModulePickerRow
        group={node}
        entries={pickerEntries}
        depth={depth}
        activeModuleId={activeModuleId}
        knowledgeCounts={knowledgeCounts}
        quizCounts={quizCounts}
        interviewCounts={interviewCounts}
        apiCounts={apiCounts}
      />
    );
  }

  const toggleChildren = () => {
    if (hasInlineChildren) {
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
    <span className={`module-tree__content${isActive && !structuralModule ? ' is-active' : ''}`}>
      <span className="module-tree__label">{displayName}</span>

      {isModule && node.routeId && !structuralModule && (
        <ModuleBadges
          routeId={node.routeId}
          knowledgeCounts={knowledgeCounts}
          quizCounts={quizCounts}
          interviewCounts={interviewCounts}
          apiCounts={apiCounts}
        />
      )}
    </span>
  );

  return (
    <li className="module-tree__item">
      <div
        className={`module-tree__row${hasInlineChildren ? ' is-expandable' : ''}${isModule && !structuralModule ? ' is-module' : ''}${isRealModule ? ' is-real-module' : ''}${isActive && !structuralModule ? ' is-active' : ''}`}
      >
        {hasInlineChildren && (
          <button
            type="button"
            className={`module-tree__collapse-zone is-full${open ? ' is-open' : ' is-closed'}`}
            onClick={toggleChildren}
            aria-expanded={open}
            aria-label={`${open ? 'Collapse' : 'Expand'} ${displayName}`}
            title={`${open ? 'Collapse' : 'Expand'} ${displayName}`}
          >
            <span className="module-tree__indent" data-depth={Math.min(depth, 5)} />
            <span className="module-tree__vertical-cue" aria-hidden="true">{open ? '↑↑↑' : '↓↓↓'}</span>
            <span className="module-tree__caret" aria-hidden="true">{open ? '−' : '+'}</span>
            {content}
          </button>
        )}

        {isModule && !hasChildren && (
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
                <span className="module-tree__vertical-cue module-tree__vertical-cue--placeholder" aria-hidden="true" />
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
            <span className="module-tree__vertical-cue module-tree__vertical-cue--placeholder" aria-hidden="true" />
            <span className="module-tree__caret module-tree__caret--leaf" aria-hidden="true">•</span>
            {content}
          </div>
        )}
      </div>

      {hasInlineChildren && open && (
        <ul className="module-tree__branch">
          {structuralModule && (!filter || showEntireSubtree || selfMatches) && (
            <ModulePickerRow
              group={node}
              entries={[{ node, sequence: 1 }]}
              depth={depth + 1}
              activeModuleId={activeModuleId}
              knowledgeCounts={knowledgeCounts}
              quizCounts={quizCounts}
              interviewCounts={interviewCounts}
              apiCounts={apiCounts}
            />
          )}

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
              renderAsPicker={mixedDirectChildren && isLeafModule(child)}
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
