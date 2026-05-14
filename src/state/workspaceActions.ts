import type { MethodMap, MethodParams, MethodResult, Tab, Workspace } from '@/transport';

type WorkspaceActionMethod = 'createTab' | 'getWorkspace';
export type WorkspaceActionRequest = <M extends WorkspaceActionMethod>(
  method: M,
  params: MethodParams<M>,
) => Promise<MethodResult<M>>;

type CreateTerminalTabInput = {
  projectId: string;
  workspace: Workspace | null;
  setWorkspace: (workspace: Workspace) => void;
  request: WorkspaceActionRequest;
};

export async function createTerminalTab({
  projectId,
  workspace,
  setWorkspace,
  request,
}: CreateTerminalTabInput): Promise<Tab> {
  const value: MethodMap['createTab']['params']['value'] = {
    projectID: projectId,
    kind: 'terminal',
  };

  if (workspace?.focusedAreaID) value.areaID = workspace.focusedAreaID;

  const created = await request('createTab', { type: 'createTab', value });
  const next = await request('getWorkspace', {
    type: 'getWorkspace',
    value: { projectID: projectId },
  });
  setWorkspace(next.value);

  return created.value;
}
