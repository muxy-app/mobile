export type RequestEnvelope = {
  type: 'request';
  payload: {
    id: string;
    method: string;
    params: { type: string; value: unknown } | null;
  };
};

export type ResponseResult = { type: string; value?: unknown };

export type ResponseEnvelope = {
  type: 'response';
  payload: {
    id: string;
    result?: ResponseResult;
    error?: { code: number; message: string };
  };
};

export type EventEnvelope = {
  type: 'event';
  payload: {
    event: string;
    data: { type: string; value: unknown } | null;
  };
};

export type IncomingEnvelope = ResponseEnvelope | EventEnvelope;

export type Project = {
  id: string;
  name: string;
  path: string;
  sortOrder: number;
  createdAt: string;
  icon?: string;
  logo?: string;
  iconColor?: string;
};

export type Worktree = {
  id: string;
  name: string;
  path: string;
  branch: string;
  isPrimary: boolean;
  canBeRemoved: boolean;
  createdAt: string;
};

export type TabKind = 'terminal' | 'vcs' | 'editor' | 'diffViewer';

export type Tab = {
  id: string;
  kind: TabKind;
  title: string;
  isPinned: boolean;
  paneID: string;
};

export type TabArea = {
  id: string;
  projectPath: string;
  tabs: Tab[];
  activeTabID?: string;
};

export type SplitDirection = 'horizontal' | 'vertical';
export type SplitPosition = 'first' | 'second';

export type Split = {
  direction: SplitDirection;
  first: SplitNode;
  second: SplitNode;
};

export type SplitNode =
  | { type: 'split'; split: Split }
  | { type: 'tabArea'; tabArea: TabArea };

export type Workspace = {
  projectID: string;
  worktreeID: string;
  focusedAreaID: string;
  root: SplitNode;
};

export type TerminalCell = {
  codepoint: number;
  fg: number;
  bg: number;
  flags: number;
};

export type TerminalCells = {
  paneID: string;
  cols: number;
  rows: number;
  cursorX: number;
  cursorY: number;
  cursorVisible: boolean;
  defaultFg: number;
  defaultBg: number;
  cells: TerminalCell[];
};

export type TerminalOutput = {
  paneID: string;
  bytes: string;
};

export type TerminalSnapshot = {
  paneID: string;
  bytes: string;
};

export type PaneOwner =
  | { mac: { deviceName: string } }
  | { remote: { deviceID: string; deviceName: string } };

export type PaneOwnership = {
  paneID: string;
  owner: PaneOwner;
};

export type DeviceTheme = {
  themeFg?: number;
  themeBg?: number;
  themePalette?: number[];
};

export type ThemeChange = {
  fg?: number;
  bg?: number;
  palette?: number[];
};

export type Pairing = {
  clientID: string;
  deviceName: string;
} & DeviceTheme;

export type GitFileStatus =
  | 'added'
  | 'modified'
  | 'deleted'
  | 'renamed'
  | 'copied'
  | 'untracked'
  | 'unmerged';

export type GitFile = {
  path: string;
  status: GitFileStatus;
  isUntracked: boolean;
};

export type VCSPRChecksStatus = 'none' | 'pending' | 'success' | 'failure';

export type VCSPRChecks = {
  status: VCSPRChecksStatus;
  passing: number;
  failing: number;
  pending: number;
  total: number;
};

export type VCSPRMergeStateStatus =
  | 'CLEAN'
  | 'HAS_HOOKS'
  | 'UNSTABLE'
  | 'BEHIND'
  | 'BLOCKED'
  | 'DIRTY'
  | 'DRAFT'
  | 'UNKNOWN';

export type VCSPullRequest = {
  url: string;
  number: number;
  state: string;
  isDraft: boolean;
  baseBranch: string;
  mergeable?: boolean | null;
  mergeStateStatus?: VCSPRMergeStateStatus;
  checks?: VCSPRChecks;
};

export type VCSMergeMethod = 'merge' | 'squash' | 'rebase';

export type VCSStatus = {
  branch: string;
  aheadCount: number;
  behindCount: number;
  hasUpstream: boolean;
  stagedFiles: GitFile[];
  changedFiles: GitFile[];
  defaultBranch?: string;
  pullRequest?: VCSPullRequest;
};

export type VCSBranches = {
  current: string;
  locals: string[];
  defaultBranch?: string;
};

export type VCSPRCreated = {
  url: string;
  number: number;
};

export type VCSDiffRowKind = 'hunk' | 'context' | 'addition' | 'deletion' | 'collapsed';

export type VCSDiffRow = {
  kind: VCSDiffRowKind;
  oldLineNumber?: number | null;
  newLineNumber?: number | null;
  text: string;
};

export type VCSDiff = {
  filePath: string;
  rows: VCSDiffRow[];
  additions: number;
  deletions: number;
  truncated: boolean;
  isBinary: boolean;
};

export type AuthParams = {
  deviceID: string;
  deviceName: string;
  token: string;
};

export type RegisterParams = {
  deviceName: string;
};

export type MethodMap = {
  authenticateDevice: {
    params: { type: 'authenticateDevice'; value: AuthParams };
    result: { type: 'pairing'; value: Pairing };
  };
  pairDevice: {
    params: { type: 'pairDevice'; value: AuthParams };
    result: { type: 'pairing'; value: Pairing };
  };
  registerDevice: {
    params: { type: 'registerDevice'; value: RegisterParams };
    result: { type: 'deviceInfo'; value: Pairing };
  };
  listProjects: {
    params: null;
    result: { type: 'projects'; value: Project[] };
  };
  selectProject: {
    params: { type: 'selectProject'; value: { projectID: string } };
    result: { type: 'ok' };
  };
  listWorktrees: {
    params: { type: 'listWorktrees'; value: { projectID: string } };
    result: { type: 'worktrees'; value: Worktree[] };
  };
  selectWorktree: {
    params: { type: 'selectWorktree'; value: { projectID: string; worktreeID: string } };
    result: { type: 'ok' };
  };
  getWorkspace: {
    params: { type: 'getWorkspace'; value: { projectID: string } };
    result: { type: 'workspace'; value: Workspace };
  };
  createTab: {
    params: { type: 'createTab'; value: { projectID: string; areaID?: string; kind: TabKind } };
    result: { type: 'tab'; value: Tab };
  };
  closeTab: {
    params: { type: 'closeTab'; value: { projectID: string; areaID: string; tabID: string } };
    result: { type: 'ok' };
  };
  selectTab: {
    params: { type: 'selectTab'; value: { projectID: string; areaID: string; tabID: string } };
    result: { type: 'ok' };
  };
  splitArea: {
    params: {
      type: 'splitArea';
      value: { projectID: string; areaID: string; direction: SplitDirection; position: SplitPosition };
    };
    result: { type: 'ok' };
  };
  closeArea: {
    params: { type: 'closeArea'; value: { projectID: string; areaID: string } };
    result: { type: 'ok' };
  };
  focusArea: {
    params: { type: 'focusArea'; value: { projectID: string; areaID: string } };
    result: { type: 'ok' };
  };
  takeOverPane: {
    params: { type: 'takeOverPane'; value: { paneID: string; cols: number; rows: number } };
    result: { type: 'ok' };
  };
  releasePane: {
    params: { type: 'releasePane'; value: { paneID: string } };
    result: { type: 'ok' };
  };
  terminalInput: {
    params: { type: 'terminalInput'; value: { paneID: string; bytes: string } };
    result: { type: 'ok' };
  };
  terminalResize: {
    params: { type: 'terminalResize'; value: { paneID: string; cols: number; rows: number } };
    result: { type: 'ok' };
  };
  terminalScroll: {
    params: {
      type: 'terminalScroll';
      value: { paneID: string; deltaX: number; deltaY: number; precise: boolean };
    };
    result: { type: 'ok' };
  };
  getTerminalContent: {
    params: { type: 'getTerminalContent'; value: { paneID: string } };
    result: { type: 'terminalCells'; value: TerminalCells };
  };
  getProjectLogo: {
    params: { type: 'getProjectLogo'; value: { projectID: string } };
    result: { type: 'projectLogo'; value: { projectID: string; pngData: string } };
  };
  subscribe: {
    params: { type: 'subscribe'; value: { events: string[] } };
    result: { type: 'ok' };
  };
  unsubscribe: {
    params: { type: 'unsubscribe'; value: { events: string[] } };
    result: { type: 'ok' };
  };
  getVCSStatus: {
    params: { type: 'getVCSStatus'; value: { projectID: string } };
    result: { type: 'vcsStatus'; value: VCSStatus };
  };
  vcsRefresh: {
    params: { type: 'vcsRefresh'; value: { projectID: string } };
    result: { type: 'vcsStatus'; value: VCSStatus };
  };
  vcsCommit: {
    params: { type: 'vcsCommit'; value: { projectID: string; message: string; stageAll: boolean } };
    result: { type: 'ok' };
  };
  vcsPush: {
    params: { type: 'vcsPush'; value: { projectID: string } };
    result: { type: 'ok' };
  };
  vcsPull: {
    params: { type: 'vcsPull'; value: { projectID: string } };
    result: { type: 'ok' };
  };
  vcsStageFiles: {
    params: { type: 'vcsStageFiles'; value: { projectID: string; paths: string[] } };
    result: { type: 'ok' };
  };
  vcsUnstageFiles: {
    params: { type: 'vcsUnstageFiles'; value: { projectID: string; paths: string[] } };
    result: { type: 'ok' };
  };
  vcsDiscardFiles: {
    params: {
      type: 'vcsDiscardFiles';
      value: { projectID: string; paths: string[]; untrackedPaths: string[] };
    };
    result: { type: 'ok' };
  };
  vcsListBranches: {
    params: { type: 'vcsListBranches'; value: { projectID: string } };
    result: { type: 'vcsBranches'; value: VCSBranches };
  };
  vcsSwitchBranch: {
    params: { type: 'vcsSwitchBranch'; value: { projectID: string; branch: string } };
    result: { type: 'ok' };
  };
  vcsCreateBranch: {
    params: { type: 'vcsCreateBranch'; value: { projectID: string; name: string } };
    result: { type: 'ok' };
  };
  vcsCreatePR: {
    params: {
      type: 'vcsCreatePR';
      value: { projectID: string; title: string; body: string; baseBranch?: string; draft: boolean };
    };
    result: { type: 'vcsPRCreated'; value: VCSPRCreated };
  };
  vcsMergePullRequest: {
    params: {
      type: 'vcsMergePullRequest';
      value: { projectID: string; number: number; method: VCSMergeMethod; deleteBranch: boolean };
    };
    result: { type: 'ok' };
  };
  vcsAddWorktree: {
    params: {
      type: 'vcsAddWorktree';
      value: { projectID: string; name: string; branch: string; createBranch: boolean };
    };
    result: { type: 'worktrees'; value: Worktree[] };
  };
  vcsRemoveWorktree: {
    params: { type: 'vcsRemoveWorktree'; value: { projectID: string; worktreeID: string } };
    result: { type: 'ok' };
  };
  vcsGetDiff: {
    params: {
      type: 'vcsGetDiff';
      value: { projectID: string; filePath: string; forceFull: boolean };
    };
    result: { type: 'vcsDiff'; value: VCSDiff };
  };
};

export type MethodName = keyof MethodMap;
export type MethodParams<M extends MethodName> = MethodMap[M]['params'];
export type MethodResult<M extends MethodName> = MethodMap[M]['result'];

export type EventDataMap = {
  workspaceChanged: { type: 'workspace'; value: Workspace };
  terminalOutput: { type: 'terminalOutput'; value: TerminalOutput };
  terminalSnapshot: { type: 'terminalCells'; value: TerminalSnapshot };
  notificationReceived: { type: 'notification'; value: unknown };
  projectsChanged: { type: 'projects'; value: Project[] };
  paneOwnershipChanged: { type: 'paneOwnership'; value: PaneOwnership };
  themeChanged: { type: 'deviceTheme'; value: ThemeChange };
};

export type EventName = keyof EventDataMap;
export type EventData<E extends EventName> = EventDataMap[E];

export const ALL_EVENT_NAMES: readonly EventName[] = [
  'workspaceChanged',
  'terminalOutput',
  'terminalSnapshot',
  'notificationReceived',
  'projectsChanged',
  'paneOwnershipChanged',
  'themeChanged',
] as const;
