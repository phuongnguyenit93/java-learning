import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { loadKnowledgeSearchIndex, searchKnowledge } from '../data/knowledgeSearch';
import { collectRealModules, formatCatalogName } from '../data/moduleCatalog';
import { useLanguage } from '../state/LanguageContext';
import type {
  KnowledgeSearchIndex,
  KnowledgeSearchResult,
  ModuleCatalogNode,
} from '../types/learning';

interface ModuleSidebarProps {
  nodes: ModuleCatalogNode[];
  activeModuleId: string;
  knowledgeCounts: Record<string, number>;
  quizCounts: Record<string, number>;
  interviewCounts: Record<string, number>;
  apiCounts: Record<string, number>;
  roadmapAvailability: Record<string, boolean>;
}

interface TreeNodeProps {
  node: ModuleCatalogNode;
  depth: number;
  activeModuleId: string;
  knowledgeCounts: Record<string, number>;
  quizCounts: Record<string, number>;
  interviewCounts: Record<string, number>;
  apiCounts: Record<string, number>;
  roadmapAvailability: Record<string, boolean>;
  filter: string;
  knowledgeSearchActive: boolean;
  knowledgeMatchModuleIds: ReadonlySet<string>;
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

type ModuleTreeCssProperties = CSSProperties & {
  '--module-tree-depth'?: number;
  '--module-menu-border'?: string;
  '--module-menu-accent'?: string;
};

function moduleTreeIndentStyle(depth: number): ModuleTreeCssProperties {
  return {
    '--module-tree-depth': depth,
  };
}

function moduleMenuLevelStyle(depth: number): ModuleTreeCssProperties {
  const level = depth + 1;
  const levelHues = [
    210, // level 1 - blue
    145, // level 2 - green
    38,  // level 3 - amber
    274, // level 4 - violet
    188, // level 5 - cyan / teal
    338, // level 6 - rose
    72,  // level 7 - lime
  ];
  const hue = levelHues[(level - 1) % levelHues.length];

  return {
    '--module-menu-border': `hsla(${hue}, 78%, 64%, 0.30)`,
    '--module-menu-accent': `hsl(${hue}, 82%, 72%)`,
  };
}

function ModuleTreeIndent({ depth }: { depth: number }) {
  return (
    <span
      className="module-tree__indent"
      style={moduleTreeIndentStyle(depth)}
      aria-hidden="true"
    />
  );
}

function ModuleTreeLevelBadge({ depth }: { depth: number }) {
  return (
    <span
      className="module-tree__level-badge"
      aria-label={`Menu level ${depth + 1}`}
      title={`Menu level ${depth + 1}`}
    >
      {depth + 1}
    </span>
  );
}

interface ModulePickerPosition {
  top: number;
  left: number;
  width: number;
  maxHeight: number;
}

interface ModuleSearchMetadata {
  displayName: string;
  hierarchy: string;
}

const MODULE_PICKER_OPEN_EVENT = 'java-learning:module-picker-open';
const KNOWLEDGE_RESULTS_PAGE_SIZE = 12;

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

function nodeContainsKnowledgeMatch(
  node: ModuleCatalogNode,
  matchingModuleIds: ReadonlySet<string>,
): boolean {
  if (node.routeId && matchingModuleIds.has(node.routeId)) {
    return true;
  }

  return node.children.some((child) => nodeContainsKnowledgeMatch(child, matchingModuleIds));
}

function collectModuleSearchMetadata(
  nodes: ModuleCatalogNode[],
  parentPath: string[] = [],
  result: Map<string, ModuleSearchMetadata> = new Map(),
): Map<string, ModuleSearchMetadata> {
  nodes.forEach((node) => {
    const displayName = formatCatalogName(node.name);
    const currentPath = [...parentPath, displayName];

    if (node.routeId) {
      result.set(node.routeId, {
        displayName,
        hierarchy: currentPath.slice(0, -1).join(' / '),
      });
    }

    collectModuleSearchMetadata(node.children, currentPath, result);
  });

  return result;
}

function isQualifiedRealModule(
  node: ModuleCatalogNode,
  knowledgeCounts: Record<string, number>,
  quizCounts: Record<string, number>,
  interviewCounts: Record<string, number>,
  apiCounts: Record<string, number>,
  roadmapAvailability: Record<string, boolean>,
): boolean {
  if (node.kind !== 'MODULE' || !node.routeId) {
    return false;
  }

  return (knowledgeCounts[node.routeId] ?? 0) > 0
    || (quizCounts[node.routeId] ?? 0) > 0
    || (interviewCounts[node.routeId] ?? 0) > 0
    || (apiCounts[node.routeId] ?? 0) > 0
    || roadmapAvailability[node.routeId] === true;
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
  roadmapAvailability,
}: {
  routeId: string;
  knowledgeCounts: Record<string, number>;
  quizCounts: Record<string, number>;
  interviewCounts: Record<string, number>;
  apiCounts: Record<string, number>;
  roadmapAvailability: Record<string, boolean>;
}) {
  const knowledgeCount = knowledgeCounts[routeId] ?? 0;
  const quizCount = quizCounts[routeId] ?? 0;
  const interviewCount = interviewCounts[routeId] ?? 0;
  const apiCount = apiCounts[routeId] ?? 0;
  const hasRoadmap = roadmapAvailability[routeId] === true;

  if (knowledgeCount === 0 && quizCount === 0 && interviewCount === 0 && apiCount === 0 && !hasRoadmap) {
    return null;
  }

  return (
    <span className="module-tree__badges" aria-label="Module content counts">
      {hasRoadmap && (
        <span className="module-tree__badge module-tree__badge--roadmap" title="Roadmap" aria-label="Roadmap">
          R
        </span>
      )}
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
  roadmapAvailability,
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
  roadmapAvailability: Record<string, boolean>;
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
                navigate(`/learning/java/knowledge/${routeId}`);
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
                roadmapAvailability={roadmapAvailability}
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
  roadmapAvailability,
}: {
  group: ModuleCatalogNode;
  entries: ModulePickerEntry[];
  depth: number;
  activeModuleId: string;
  knowledgeCounts: Record<string, number>;
  quizCounts: Record<string, number>;
  interviewCounts: Record<string, number>;
  apiCounts: Record<string, number>;
  roadmapAvailability: Record<string, boolean>;
}) {
  const [pickerVisible, setPickerVisible] = useState(false);
  const [pickerPinned, setPickerPinned] = useState(false);
  const [pickerPosition, setPickerPosition] = useState<ModulePickerPosition | null>(null);
  const pickerAnchorRef = useRef<HTMLButtonElement>(null);
  const pickerPopupRef = useRef<HTMLDivElement>(null);
  const pickerCloseTimerRef = useRef<number | null>(null);
  const displayName = formatCatalogName(group.name);
  const activeEntry = entries.find(({ node }) => node.routeId === activeModuleId);
  const containsActiveModule = Boolean(activeEntry);
  const activeModuleName = activeEntry ? formatCatalogName(activeEntry.node.name) : null;

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
          <ModuleTreeIndent depth={depth} />
          <span className="module-tree__vertical-cue module-tree__vertical-cue--placeholder" aria-hidden="true" />
          <span className="module-tree__caret" aria-hidden="true">›</span>
          <span className={`module-tree__content${containsActiveModule ? ' is-active' : ''}`}>
            <span className="module-tree__label">{displayName}</span>
            {activeModuleName && (
              <span className="module-tree__active-module">
                <span className="module-tree__active-module-dot" aria-hidden="true" />
                <span className="module-tree__active-module-prefix">ACTIVE</span>
                <span className="module-tree__active-module-name">{activeModuleName}</span>
              </span>
            )}
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
          roadmapAvailability={roadmapAvailability}
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
  roadmapAvailability: Record<string, boolean>,
): ModuleCatalogNode[] {
  const result: ModuleCatalogNode[] = [];

  nodes.forEach((node) => {
    const projectedChildren = projectRealModuleNodes(
      node.children,
      knowledgeCounts,
      quizCounts,
      interviewCounts,
      apiCounts,
      roadmapAvailability,
    );
    const keepModule = isQualifiedRealModule(
      node,
      knowledgeCounts,
      quizCounts,
      interviewCounts,
      apiCounts,
      roadmapAvailability,
    );

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
  roadmapAvailability,
  filter,
  knowledgeSearchActive,
  knowledgeMatchModuleIds,
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
  const containsKnowledgeMatch = nodeContainsKnowledgeMatch(node, knowledgeMatchModuleIds);
  const treeSearchActive = Boolean(filter) || knowledgeSearchActive;
  const visible = knowledgeSearchActive
    ? containsKnowledgeMatch
    : (!filter || showEntireSubtree || containsMatch);
  const childShowEntireSubtree = knowledgeSearchActive
    ? false
    : showEntireSubtree || (Boolean(filter) && selfMatches);
  const open = treeSearchActive ? searchExpanded : expanded;
  const isModule = node.kind === 'MODULE' && Boolean(node.routeId);
  const isActive = isModule && node.routeId === activeModuleId;
  const structuralModule = isModule && hasInlineChildren;
  const isRealModule = isQualifiedRealModule(
    node,
    knowledgeCounts,
    quizCounts,
    interviewCounts,
    apiCounts,
    roadmapAvailability,
  );
  const displayName = formatCatalogName(node.name);
  const pickerEntries = useMemo<ModulePickerEntry[]>(() => {
    const ordered = node.children.map((child, index) => ({ node: child, sequence: index + 1 }));

    if (knowledgeSearchActive) {
      return ordered.filter(({ node: child }) => nodeContainsKnowledgeMatch(child, knowledgeMatchModuleIds));
    }

    if (!filter || showEntireSubtree || selfMatches) {
      return ordered;
    }

    return ordered.filter(({ node: child }) => nodeContainsMatch(child, filter));
  }, [filter, knowledgeMatchModuleIds, knowledgeSearchActive, node.children, selfMatches, showEntireSubtree]);

  useEffect(() => {
    if (treeSearchActive && visible && hasInlineChildren) {
      setSearchExpanded(true);
    }
  }, [hasInlineChildren, treeSearchActive, visible]);

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
        roadmapAvailability={roadmapAvailability}
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
        roadmapAvailability={roadmapAvailability}
      />
    );
  }

  const toggleChildren = () => {
    if (hasInlineChildren) {
      if (treeSearchActive) {
        setSearchExpanded((current) => !current);
      } else {
        setExpanded((current) => !current);
      }
    }
  };

  const navigateToModule = () => {
    if (isModule && node.routeId) {
      navigate(`/learning/java/knowledge/${node.routeId}`);
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
          roadmapAvailability={roadmapAvailability}
        />
      )}
    </span>
  );

  return (
    <li className="module-tree__item">
      <div
        className={`module-tree__row${hasChildren ? ' is-menu-level' : ''}${hasInlineChildren ? ' is-expandable' : ''}${isModule && !structuralModule ? ' is-module' : ''}${isRealModule ? ' is-real-module' : ''}${isActive && !structuralModule ? ' is-active' : ''}`}
        style={hasChildren ? moduleMenuLevelStyle(depth) : undefined}
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
            <ModuleTreeIndent depth={depth} />
            <ModuleTreeLevelBadge depth={depth} />
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
                <ModuleTreeIndent depth={depth} />
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
            <ModuleTreeIndent depth={depth} />
            <span className="module-tree__vertical-cue module-tree__vertical-cue--placeholder" aria-hidden="true" />
            <span className="module-tree__caret module-tree__caret--leaf" aria-hidden="true">•</span>
            {content}
          </div>
        )}
      </div>

      {hasInlineChildren && open && (
        <ul className="module-tree__branch">
          {structuralModule && (
            knowledgeSearchActive
              ? Boolean(node.routeId && knowledgeMatchModuleIds.has(node.routeId))
              : (!filter || showEntireSubtree || selfMatches)
          ) && (
            <ModulePickerRow
              group={node}
              entries={[{ node, sequence: 1 }]}
              depth={depth + 1}
              activeModuleId={activeModuleId}
              knowledgeCounts={knowledgeCounts}
              quizCounts={quizCounts}
              interviewCounts={interviewCounts}
              apiCounts={apiCounts}
              roadmapAvailability={roadmapAvailability}
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
              roadmapAvailability={roadmapAvailability}
              filter={filter}
              knowledgeSearchActive={knowledgeSearchActive}
              knowledgeMatchModuleIds={knowledgeMatchModuleIds}
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
  roadmapAvailability,
}: ModuleSidebarProps) {
  const navigate = useNavigate();
  const { language } = useLanguage();
  const [filterInput, setFilterInput] = useState('');
  const [realModulesOnly, setRealModulesOnly] = useState(true);
  const [knowledgeSearchInput, setKnowledgeSearchInput] = useState('');
  const [knowledgeSearchIndex, setKnowledgeSearchIndex] = useState<KnowledgeSearchIndex | null>(null);
  const [knowledgeSearchLoading, setKnowledgeSearchLoading] = useState(false);
  const [knowledgeSearchError, setKnowledgeSearchError] = useState<string | null>(null);
  const [visibleKnowledgeResultLimit, setVisibleKnowledgeResultLimit] = useState(KNOWLEDGE_RESULTS_PAGE_SIZE);
  const [expandRequest, setExpandRequest] = useState<SidebarExpandRequest>({ version: 0, expanded: true });
  const filter = useMemo(() => filterInput.trim().toLowerCase(), [filterInput]);
  const knowledgeQuery = knowledgeSearchInput.trim();
  const knowledgeSearchActive = knowledgeQuery.length > 0;
  const moduleSearchMetadata = useMemo(() => collectModuleSearchMetadata(nodes), [nodes]);

  useEffect(() => {
    let active = true;

    if (!knowledgeSearchActive) {
      setKnowledgeSearchLoading(false);
      setKnowledgeSearchError(null);
      return () => {
        active = false;
      };
    }

    if (knowledgeSearchIndex?.language === language) {
      return () => {
        active = false;
      };
    }

    setKnowledgeSearchLoading(true);
    setKnowledgeSearchError(null);

    loadKnowledgeSearchIndex(language)
      .then((result) => {
        if (!active) {
          return;
        }

        setKnowledgeSearchIndex(result);
        setKnowledgeSearchLoading(false);
      })
      .catch((error: unknown) => {
        if (!active) {
          return;
        }

        setKnowledgeSearchIndex(null);
        setKnowledgeSearchLoading(false);
        setKnowledgeSearchError(error instanceof Error ? error.message : 'Unable to load Knowledge search.');
      });

    return () => {
      active = false;
    };
  }, [knowledgeSearchActive, knowledgeSearchIndex?.language, language]);

  const knowledgeResults = useMemo<KnowledgeSearchResult[]>(() => {
    if (!knowledgeSearchActive || !knowledgeSearchIndex || knowledgeSearchIndex.language !== language) {
      return [];
    }

    return searchKnowledge(knowledgeSearchIndex, knowledgeQuery);
  }, [knowledgeQuery, knowledgeSearchActive, knowledgeSearchIndex, language]);

  const knowledgeMatchModuleIds = useMemo(
    () => new Set(knowledgeResults.map(({ document }) => document.moduleId)),
    [knowledgeResults],
  );
  const visibleKnowledgeResults = useMemo(
    () => knowledgeResults.slice(0, visibleKnowledgeResultLimit),
    [knowledgeResults, visibleKnowledgeResultLimit],
  );

  useEffect(() => {
    setVisibleKnowledgeResultLimit(KNOWLEDGE_RESULTS_PAGE_SIZE);
  }, [knowledgeQuery, language]);
  const moduleCount = useMemo(
    () => nodes.reduce((count, node) => count + collectRealModules(node).length, 0),
    [nodes],
  );
  const realModuleCount = useMemo(
    () => nodes.reduce(
      (count, node) => count + collectRealModules(node)
        .filter((moduleNode) => isQualifiedRealModule(
          moduleNode,
          knowledgeCounts,
          quizCounts,
          interviewCounts,
          apiCounts,
          roadmapAvailability,
        )).length,
      0,
    ),
    [apiCounts, interviewCounts, knowledgeCounts, nodes, quizCounts, roadmapAvailability],
  );
  const visibleNodes = useMemo(
    () => {
      if (knowledgeSearchActive) {
        return nodes;
      }

      return realModulesOnly
        ? projectRealModuleNodes(
          nodes,
          knowledgeCounts,
          quizCounts,
          interviewCounts,
          apiCounts,
          roadmapAvailability,
        )
        : nodes;
    },
    [
      apiCounts,
      interviewCounts,
      knowledgeCounts,
      knowledgeSearchActive,
      nodes,
      quizCounts,
      realModulesOnly,
      roadmapAvailability,
    ],
  );

  const navigateToKnowledgeResult = (result: KnowledgeSearchResult) => {
    const { document } = result;
    const params = new URLSearchParams({
      tab: 'knowledge',
      category: document.categoryId,
    });

    if (document.sectionId) {
      params.set('section', document.sectionId);
    }

    setKnowledgeSearchInput('');
    navigate(`/learning/java/knowledge/${document.moduleId}?${params.toString()}`);
  };

  const displayedModuleCount = knowledgeSearchActive
    ? knowledgeMatchModuleIds.size
    : (realModulesOnly ? realModuleCount : moduleCount);

  return (
    <aside className="learning-sidebar">
      <div className="learning-sidebar__headline">
        <span className="learning-sidebar__headline-copy">
          <span>{language === 'vi' ? 'Tất cả module' : 'All modules'}</span>
          <span className="learning-sidebar__total">{displayedModuleCount}</span>
        </span>
      </div>

      <label className={`sidebar-search${knowledgeSearchActive ? ' is-disabled' : ''}`}>
        <span aria-hidden="true">⌕</span>
        <input
          type="search"
          value={filterInput}
          onChange={(event) => setFilterInput(event.target.value)}
          disabled={knowledgeSearchActive}
          placeholder={language === 'vi' ? 'Lọc module...' : 'Filter modules...'}
        />
      </label>

      <div
        className={`sidebar-module-mode${knowledgeSearchActive ? ' is-disabled' : ''}`}
        aria-label={language === 'vi' ? 'Chế độ hiển thị module' : 'Module display mode'}
      >
        <span className={!realModulesOnly ? 'is-active' : undefined}>
          {language === 'vi' ? 'Đầy đủ' : 'Full tree'}
        </span>
        <button
          type="button"
          className={`sidebar-module-mode__switch${realModulesOnly ? ' is-on' : ''}`}
          role="switch"
          aria-checked={realModulesOnly}
          aria-label={language === 'vi' ? 'Chỉ hiển thị module thật' : 'Show real modules only'}
          disabled={knowledgeSearchActive}
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

      <label className={`sidebar-knowledge-search${knowledgeSearchActive ? ' is-active' : ''}`}>
        <span aria-hidden="true">⌕</span>
        <input
          type="search"
          value={knowledgeSearchInput}
          onChange={(event) => setKnowledgeSearchInput(event.target.value)}
          placeholder={language === 'vi' ? 'Tìm kiến thức...' : 'Search knowledge...'}
          aria-label={language === 'vi' ? 'Tìm trong Knowledge của tất cả module' : 'Search Knowledge across all modules'}
        />
        {knowledgeSearchInput && (
          <button
            type="button"
            className="sidebar-knowledge-search__clear"
            onClick={() => setKnowledgeSearchInput('')}
            aria-label={language === 'vi' ? 'Xóa tìm kiếm kiến thức' : 'Clear Knowledge search'}
          >
            ×
          </button>
        )}
      </label>

      {knowledgeSearchActive && (
        <div className="sidebar-knowledge-results" aria-live="polite">
          <div className="sidebar-knowledge-results__header">
            <span>{language === 'vi' ? 'KẾT QUẢ KIẾN THỨC' : 'KNOWLEDGE RESULTS'}</span>
            {!knowledgeSearchLoading && !knowledgeSearchError && (
              <span>{knowledgeResults.length}</span>
            )}
          </div>

          {knowledgeSearchLoading && (
            <div className="sidebar-knowledge-results__status">
              {language === 'vi' ? 'Đang tải chỉ mục tìm kiếm...' : 'Loading search index...'}
            </div>
          )}

          {!knowledgeSearchLoading && knowledgeSearchError && (
            <div className="sidebar-knowledge-results__status is-error">{knowledgeSearchError}</div>
          )}

          {!knowledgeSearchLoading && !knowledgeSearchError && knowledgeResults.length === 0 && (
            <div className="sidebar-knowledge-results__status">
              {language === 'vi' ? 'Không tìm thấy Knowledge phù hợp.' : 'No matching Knowledge found.'}
            </div>
          )}

          {!knowledgeSearchLoading && !knowledgeSearchError && visibleKnowledgeResults.length > 0 && (
            <div className="sidebar-knowledge-results__list">
              {visibleKnowledgeResults.map((result) => {
                const { document } = result;
                const moduleMetadata = moduleSearchMetadata.get(document.moduleId);
                const moduleName = moduleMetadata?.displayName ?? document.moduleId;
                const context = document.type === 'SECTION'
                  ? `${moduleName} › ${document.categoryTitle}`
                  : moduleName;

                return (
                  <button
                    key={document.documentId}
                    type="button"
                    className="sidebar-knowledge-result"
                    onClick={() => navigateToKnowledgeResult(result)}
                    title={moduleMetadata?.hierarchy
                      ? `${moduleMetadata.hierarchy} / ${moduleName}`
                      : moduleName}
                  >
                    <span className="sidebar-knowledge-result__title">{document.title}</span>
                    <span className="sidebar-knowledge-result__context">{context}</span>
                    {document.preview && (
                      <span className="sidebar-knowledge-result__preview">{document.preview}</span>
                    )}
                  </button>
                );
              })}
            </div>
          )}

          {!knowledgeSearchLoading && !knowledgeSearchError && knowledgeResults.length > visibleKnowledgeResults.length && (
            <button
              type="button"
              className="sidebar-knowledge-results__more"
              onClick={() => setVisibleKnowledgeResultLimit((current) => (
                Math.min(current + KNOWLEDGE_RESULTS_PAGE_SIZE, knowledgeResults.length)
              ))}
            >
              {language === 'vi'
                ? `Còn ${knowledgeResults.length - visibleKnowledgeResults.length} kết quả khác`
                : `${knowledgeResults.length - visibleKnowledgeResults.length} more results`}
            </button>
          )}
        </div>
      )}

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
            roadmapAvailability={roadmapAvailability}
            filter={knowledgeSearchActive ? '' : filter}
            knowledgeSearchActive={knowledgeSearchActive}
            knowledgeMatchModuleIds={knowledgeMatchModuleIds}
            expandRequest={expandRequest}
          />
        ))}
        {knowledgeSearchActive
          && !knowledgeSearchLoading
          && !knowledgeSearchError
          && knowledgeMatchModuleIds.size === 0 && (
            <li className="module-tree__empty-search">
              {language === 'vi' ? 'Không có module chứa Knowledge phù hợp.' : 'No module contains matching Knowledge.'}
            </li>
        )}
      </ul>
    </aside>
  );
}
