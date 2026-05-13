import type { SplitNode, Tab, TabArea, Workspace } from '../transport/protocol';

import {
  findArea,
  flattenAreas,
  flattenTabs,
  mapAreas,
  mergeWorkspaceUpdate,
} from './workspaceTree';

function tab(id: string, paneID = `p-${id}`): Tab {
  return { id, kind: 'terminal', title: id, isPinned: false, paneID };
}

function area(id: string, tabs: Tab[], activeTabID?: string): TabArea {
  return { id, projectPath: '/p', tabs, activeTabID };
}

function leaf(a: TabArea): SplitNode {
  return { type: 'tabArea', tabArea: a };
}

function split(first: SplitNode, second: SplitNode): SplitNode {
  return {
    type: 'split',
    split: { direction: 'horizontal', first, second },
  };
}

describe('flattenAreas', () => {
  it('returns a single area for a leaf node', () => {
    const a = area('a', [tab('t1')]);
    expect(flattenAreas(leaf(a))).toEqual([a]);
  });

  it('walks splits in left-then-right order', () => {
    const a = area('a', []);
    const b = area('b', []);
    const c = area('c', []);
    const tree = split(leaf(a), split(leaf(b), leaf(c)));
    expect(flattenAreas(tree).map((x) => x.id)).toEqual(['a', 'b', 'c']);
  });
});

describe('flattenTabs', () => {
  it('returns each tab paired with its area id', () => {
    const a = area('a', [tab('t1'), tab('t2')]);
    const b = area('b', [tab('t3')]);
    const tree = split(leaf(a), leaf(b));
    expect(flattenTabs(tree)).toEqual([
      { tab: a.tabs[0], areaId: 'a' },
      { tab: a.tabs[1], areaId: 'a' },
      { tab: b.tabs[0], areaId: 'b' },
    ]);
  });

  it('returns an empty list for an area with no tabs', () => {
    expect(flattenTabs(leaf(area('a', [])))).toEqual([]);
  });
});

describe('findArea', () => {
  it('finds an area by id in nested splits', () => {
    const target = area('target', [tab('t')]);
    const tree = split(leaf(area('a', [])), split(leaf(area('b', [])), leaf(target)));
    expect(findArea(tree, 'target')).toBe(target);
  });

  it('returns null when the area is not present', () => {
    const tree = leaf(area('a', []));
    expect(findArea(tree, 'missing')).toBeNull();
  });
});

describe('mapAreas', () => {
  it('applies the transform to every area while preserving split shape', () => {
    const tree = split(leaf(area('a', [tab('t1')])), leaf(area('b', [tab('t2')])));
    const out = mapAreas(tree, (a) => ({ ...a, projectPath: `/new/${a.id}` }));
    const flat = flattenAreas(out);
    expect(flat.map((a) => a.projectPath)).toEqual(['/new/a', '/new/b']);
    expect(out.type).toBe('split');
  });

  it('does not mutate the input tree', () => {
    const a = area('a', [tab('t1')]);
    const tree = leaf(a);
    mapAreas(tree, (x) => ({ ...x, projectPath: 'other' }));
    expect(a.projectPath).toBe('/p');
  });
});

describe('mergeWorkspaceUpdate', () => {
  function ws(root: SplitNode, focusedAreaID: string): Workspace {
    return { projectID: 'proj', worktreeID: 'wt', focusedAreaID, root };
  }

  it('keeps the previous active tab when it still exists in the new tree', () => {
    const prev = ws(leaf(area('a', [tab('t1'), tab('t2')], 't1')), 'a');
    const next = ws(leaf(area('a', [tab('t1'), tab('t2')], 't2')), 'a');
    const merged = mergeWorkspaceUpdate(prev, next);
    const a = findArea(merged.root, 'a');
    expect(a?.activeTabID).toBe('t1');
  });

  it('falls back to the last tab when the previous active tab is gone', () => {
    const prev = ws(leaf(area('a', [tab('t1'), tab('t2'), tab('t3')], 't2')), 'a');
    const next = ws(leaf(area('a', [tab('t1'), tab('t3')], 't1')), 'a');
    const merged = mergeWorkspaceUpdate(prev, next);
    const a = findArea(merged.root, 'a');
    expect(a?.activeTabID).toBe('t3');
  });

  it('returns undefined active tab when the area is empty', () => {
    const prev = ws(leaf(area('a', [tab('t1')], 't1')), 'a');
    const next = ws(leaf(area('a', [], undefined)), 'a');
    const merged = mergeWorkspaceUpdate(prev, next);
    expect(findArea(merged.root, 'a')?.activeTabID).toBeUndefined();
  });

  it('keeps the previous focused area when still present', () => {
    const prev = ws(split(leaf(area('a', [])), leaf(area('b', []))), 'a');
    const next = ws(split(leaf(area('a', [])), leaf(area('b', []))), 'b');
    expect(mergeWorkspaceUpdate(prev, next).focusedAreaID).toBe('a');
  });

  it('uses the next focused area when the previous one is missing', () => {
    const prev = ws(leaf(area('a', [])), 'a');
    const next = ws(leaf(area('b', [])), 'b');
    expect(mergeWorkspaceUpdate(prev, next).focusedAreaID).toBe('b');
  });

  it('does not introduce activeTabID into areas that had none before', () => {
    const prev = ws(leaf(area('a', [tab('t1')], undefined)), 'a');
    const next = ws(leaf(area('a', [tab('t1')], 't1')), 'a');
    const merged = mergeWorkspaceUpdate(prev, next);
    expect(findArea(merged.root, 'a')?.activeTabID).toBe('t1');
  });
});
