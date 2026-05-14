import type { SplitNode, Tab, TabArea, Workspace } from '@/transport';

import { createTerminalTab, type WorkspaceActionRequest } from './workspaceActions';

function tab(id: string): Tab {
  return { id, kind: 'terminal', title: id, isPinned: false, paneID: `pane-${id}` };
}

function area(id: string, tabs: Tab[], activeTabID?: string): TabArea {
  return { id, projectPath: '/p', tabs, activeTabID };
}

function leaf(tabArea: TabArea): SplitNode {
  return { type: 'tabArea', tabArea };
}

function workspace(focusedAreaID: string): Workspace {
  return {
    projectID: 'project-1',
    worktreeID: 'worktree-1',
    focusedAreaID,
    root: leaf(area(focusedAreaID, [tab('tab-1')], 'tab-1')),
  };
}

describe('createTerminalTab', () => {
  it('creates a terminal tab in the focused area and refreshes workspace state', async () => {
    const nextWorkspace = workspace('area-1');
    const request: WorkspaceActionRequest = jest.fn(async (method) => {
      if (method === 'createTab') return { type: 'tab', value: tab('tab-2') };
      return { type: 'workspace', value: nextWorkspace };
    }) as WorkspaceActionRequest;
    const setWorkspace = jest.fn();

    const result = await createTerminalTab({
      projectId: 'project-1',
      workspace: workspace('area-1'),
      request,
      setWorkspace,
    });

    expect(request).toHaveBeenNthCalledWith(1, 'createTab', {
      type: 'createTab',
      value: { projectID: 'project-1', areaID: 'area-1', kind: 'terminal' },
    });
    expect(request).toHaveBeenNthCalledWith(2, 'getWorkspace', {
      type: 'getWorkspace',
      value: { projectID: 'project-1' },
    });
    expect(setWorkspace).toHaveBeenCalledWith(nextWorkspace);
    expect(result).toEqual(tab('tab-2'));
  });

  it('omits the area when workspace state is not loaded yet', async () => {
    const nextWorkspace = workspace('area-1');
    const request: WorkspaceActionRequest = jest.fn(async (method) => {
      if (method === 'createTab') return { type: 'tab', value: tab('tab-2') };
      return { type: 'workspace', value: nextWorkspace };
    }) as WorkspaceActionRequest;

    await createTerminalTab({
      projectId: 'project-1',
      workspace: null,
      request,
      setWorkspace: jest.fn(),
    });

    expect(request).toHaveBeenNthCalledWith(1, 'createTab', {
      type: 'createTab',
      value: { projectID: 'project-1', kind: 'terminal' },
    });
  });
});
