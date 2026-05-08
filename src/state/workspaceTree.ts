import type { SplitNode, Tab, TabArea, Workspace } from '@/transport';

export type TabWithArea = { tab: Tab; areaId: string };

export function flattenAreas(node: SplitNode): TabArea[] {
  if (node.type === 'tabArea') return [node.tabArea];
  return [...flattenAreas(node.split.first), ...flattenAreas(node.split.second)];
}

export function flattenTabs(node: SplitNode): TabWithArea[] {
  if (node.type === 'tabArea') {
    return node.tabArea.tabs.map((tab) => ({ tab, areaId: node.tabArea.id }));
  }
  return [...flattenTabs(node.split.first), ...flattenTabs(node.split.second)];
}

export function findArea(node: SplitNode, areaId: string): TabArea | null {
  if (node.type === 'tabArea') {
    return node.tabArea.id === areaId ? node.tabArea : null;
  }
  return findArea(node.split.first, areaId) ?? findArea(node.split.second, areaId);
}

export function mapAreas(node: SplitNode, fn: (area: TabArea) => TabArea): SplitNode {
  if (node.type === 'tabArea') {
    return { type: 'tabArea', tabArea: fn(node.tabArea) };
  }
  return {
    type: 'split',
    split: {
      ...node.split,
      first: mapAreas(node.split.first, fn),
      second: mapAreas(node.split.second, fn),
    },
  };
}

export function mergeWorkspaceUpdate(prev: Workspace, next: Workspace): Workspace {
  const focusedAreaID = findArea(next.root, prev.focusedAreaID)
    ? prev.focusedAreaID
    : next.focusedAreaID;

  const root = mapAreas(next.root, (area) => {
    const prevArea = findArea(prev.root, area.id);
    if (!prevArea?.activeTabID) return area;
    const stillExists = area.tabs.some((t) => t.id === prevArea.activeTabID);
    if (stillExists) return { ...area, activeTabID: prevArea.activeTabID };
    const fallback = area.tabs[area.tabs.length - 1]?.id;
    return { ...area, activeTabID: fallback };
  });

  return { ...next, focusedAreaID, root };
}
