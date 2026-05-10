import { base64ToString, stringToBase64 } from '@/lib/base64';

import type {
  EventDataMap,
  EventName,
  MethodMap,
  MethodName,
  MethodParams,
  MethodResult,
  Pairing,
  Project,
  TabArea,
  VCSBranches,
  VCSStatus,
  Workspace,
  Worktree,
} from '@/transport';

export const DEMO_DEVICE_ID = '00000000-0000-0000-0000-0000000000DE';
export const DEMO_DEVICE_NAME = 'Demo Mac';
export const DEMO_DEVICE_HOST = '192.168.1.42';
export const DEMO_DEVICE_PORT = 4865;
export const DEMO_CLIENT_ID = '00000000-0000-0000-0000-00000000C11D';

const DEMO_THEME = {
  themeFg: 0xc9c2d9,
  themeBg: 0x19171f,
  themePalette: [
    0x141219, 0xec4899, 0x34d399, 0xe0af68, 0xc370d3, 0x6366f1, 0x22d3ee, 0xa9b1d6,
    0x2e2b34, 0xf472b6, 0x6ee7b7, 0xfbbf24, 0xd99be5, 0x818cf8, 0x67e8f9, 0xc9c2d9,
  ],
};

export const DEMO_PAIRING: Pairing = {
  clientID: DEMO_CLIENT_ID,
  deviceName: DEMO_DEVICE_NAME,
  ...DEMO_THEME,
};

const MUXY_ID = '11111111-1111-1111-1111-111111111111';
const WEB_ID = '22222222-2222-2222-2222-222222222222';
const MUXY_WT_MAIN = 'aaaa0001-0000-0000-0000-000000000001';
const MUXY_WT_FEATURE = 'aaaa0001-0000-0000-0000-000000000002';
const WEB_WT_MAIN = 'bbbb0001-0000-0000-0000-000000000001';

const MUXY_AREA_ID = 'aaaaaaaa-0000-0000-0000-000000000aaa';
const MUXY_TAB1_ID = 'aaaaaaaa-0000-0000-0000-000000000ab1';
const MUXY_TAB2_ID = 'aaaaaaaa-0000-0000-0000-000000000ab2';
const MUXY_PANE1_ID = 'aaaaaaaa-0000-0000-0000-000000000ac1';
const MUXY_PANE2_ID = 'aaaaaaaa-0000-0000-0000-000000000ac2';

const WEB_AREA_ID = 'bbbbbbbb-0000-0000-0000-000000000bbb';
const WEB_TAB1_ID = 'bbbbbbbb-0000-0000-0000-000000000bb1';
const WEB_TAB2_ID = 'bbbbbbbb-0000-0000-0000-000000000bb2';
const WEB_PANE1_ID = 'bbbbbbbb-0000-0000-0000-000000000bc1';
const WEB_PANE2_ID = 'bbbbbbbb-0000-0000-0000-000000000bc2';

const NOW = '2026-01-01T00:00:00.000Z';

const GREETING_TEXT =
  '[1;32mDemo Mode[0m — this terminal is simulated.\r\n' +
  'Type any command and press Enter to see the demo response.\r\n' +
  'demo@muxy ~ % ';
const PROMPT_TEXT = 'demo@muxy ~ % ';
const NOTICE_TEXT = '[33m[Demo Mode][0m Commands are not executed in demo mode.\r\n';

function delay(ms: number): Promise<void> {
  if (ms <= 0) return Promise.resolve();
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function simulatedDelayMs(method: MethodName): number {
  switch (method) {
    case 'vcsPush':
    case 'vcsPull':
    case 'vcsCommit':
    case 'vcsCreatePR':
    case 'vcsAddWorktree':
    case 'vcsRemoveWorktree':
    case 'vcsSwitchBranch':
      return 700;
    case 'vcsCreateBranch':
    case 'vcsStageFiles':
    case 'vcsUnstageFiles':
    case 'vcsDiscardFiles':
      return 250;
    default:
      return 0;
  }
}

const utf8ToBase64 = stringToBase64;
const base64ToUtf8 = base64ToString;

function makeWorkspace(
  projectID: string,
  worktreeID: string,
  projectPath: string,
  area: TabArea,
): Workspace {
  return { projectID, worktreeID, focusedAreaID: area.id, root: { type: 'tabArea', tabArea: area } };
}

function buildProjects(): Project[] {
  return [
    {
      id: MUXY_ID,
      name: 'muxy',
      path: '/Users/demo/Projects/muxy',
      sortOrder: 0,
      createdAt: NOW,
      icon: 'terminal',
      iconColor: 'blue',
    },
    {
      id: WEB_ID,
      name: 'web-app',
      path: '/Users/demo/Projects/web-app',
      sortOrder: 1,
      createdAt: NOW,
      icon: 'globe',
      iconColor: 'green',
    },
  ];
}

function buildWorktrees(): Record<string, Worktree[]> {
  return {
    [MUXY_ID]: [
      {
        id: MUXY_WT_MAIN,
        name: 'main',
        path: '/Users/demo/Projects/muxy',
        branch: 'main',
        isPrimary: true,
        canBeRemoved: false,
        createdAt: NOW,
      },
      {
        id: MUXY_WT_FEATURE,
        name: 'feature-search',
        path: '/Users/demo/Projects/muxy-worktrees/feature-search',
        branch: 'feature/search',
        isPrimary: false,
        canBeRemoved: true,
        createdAt: NOW,
      },
    ],
    [WEB_ID]: [
      {
        id: WEB_WT_MAIN,
        name: 'main',
        path: '/Users/demo/Projects/web-app',
        branch: 'main',
        isPrimary: true,
        canBeRemoved: false,
        createdAt: NOW,
      },
    ],
  };
}

function buildWorkspaces(): Record<string, Workspace> {
  const muxyArea: TabArea = {
    id: MUXY_AREA_ID,
    projectPath: '/Users/demo/Projects/muxy',
    activeTabID: MUXY_TAB1_ID,
    tabs: [
      { id: MUXY_TAB1_ID, kind: 'terminal', title: 'zsh', isPinned: false, paneID: MUXY_PANE1_ID },
      { id: MUXY_TAB2_ID, kind: 'terminal', title: 'server', isPinned: false, paneID: MUXY_PANE2_ID },
    ],
  };
  const webArea: TabArea = {
    id: WEB_AREA_ID,
    projectPath: '/Users/demo/Projects/web-app',
    activeTabID: WEB_TAB1_ID,
    tabs: [
      { id: WEB_TAB1_ID, kind: 'terminal', title: 'zsh', isPinned: false, paneID: WEB_PANE1_ID },
      { id: WEB_TAB2_ID, kind: 'terminal', title: 'dev', isPinned: false, paneID: WEB_PANE2_ID },
    ],
  };
  return {
    [MUXY_ID]: makeWorkspace(MUXY_ID, MUXY_WT_MAIN, '/Users/demo/Projects/muxy', muxyArea),
    [WEB_ID]: makeWorkspace(WEB_ID, WEB_WT_MAIN, '/Users/demo/Projects/web-app', webArea),
  };
}

function buildStatus(): Record<string, VCSStatus> {
  return {
    [MUXY_ID]: {
      branch: 'main',
      aheadCount: 1,
      behindCount: 1,
      hasUpstream: true,
      stagedFiles: [
        { path: 'app/settings.tsx', status: 'modified', isUntracked: false },
      ],
      changedFiles: [
        { path: 'src/transport/WSClient.ts', status: 'modified', isUntracked: false },
        { path: 'README.md', status: 'modified', isUntracked: false },
        { path: 'docs/demo.md', status: 'untracked', isUntracked: true },
      ],
      defaultBranch: 'main',
    },
    [WEB_ID]: {
      branch: 'main',
      aheadCount: 0,
      behindCount: 0,
      hasUpstream: true,
      stagedFiles: [],
      changedFiles: [],
      defaultBranch: 'main',
    },
  };
}

function buildBranches(): Record<string, VCSBranches> {
  return {
    [MUXY_ID]: { current: 'main', locals: ['main', 'feature/search', 'fix/scrolling'], defaultBranch: 'main' },
    [WEB_ID]: { current: 'main', locals: ['main'], defaultBranch: 'main' },
  };
}

export type DemoEmitter = <E extends EventName>(event: E, data: EventDataMap[E]) => void;

export class DemoBackend {
  private projects = buildProjects();
  private worktreesByProject = buildWorktrees();
  private workspaces = buildWorkspaces();
  private statusByProject = buildStatus();
  private branchesByProject = buildBranches();
  private greetedPanes = new Set<string>();

  constructor(private readonly emit: DemoEmitter) {}

  static get pairing(): Pairing {
    return DEMO_PAIRING;
  }

  async handle<M extends MethodName>(method: M, params: MethodParams<M>): Promise<MethodResult<M>> {
    const ms = simulatedDelayMs(method);
    if (ms > 0) await delay(ms);
    const result = this.dispatch(method, params);
    return result as MethodResult<M>;
  }

  handleTerminalInput(paneID: string, base64Bytes: string): void {
    const text = base64ToUtf8(base64Bytes);
    const containsEnter = text.includes('\r') || text.includes('\n');

    if (containsEnter) {
      const echo = text.replace(/[\r\n]+/g, '');
      const response = `${echo}\r\n${NOTICE_TEXT}${PROMPT_TEXT}`;
      this.emitOutput(paneID, response);
      return;
    }
    this.emitOutput(paneID, text);
  }

  private emitOutput(paneID: string, text: string): void {
    this.emit('terminalOutput', {
      type: 'terminalOutput',
      value: { paneID, bytes: utf8ToBase64(text) },
    });
  }

  private scheduleGreeting(paneID: string): void {
    if (this.greetedPanes.has(paneID)) {
      this.emitOutput(paneID, PROMPT_TEXT);
      return;
    }
    this.greetedPanes.add(paneID);
    setTimeout(() => this.emitOutput(paneID, GREETING_TEXT), 150);
  }

  private dispatch<M extends MethodName>(method: M, params: MethodParams<M>): MethodMap[M]['result'] {
    switch (method) {
      case 'authenticateDevice':
      case 'pairDevice':
        return { type: 'pairing', value: DEMO_PAIRING } as MethodMap[M]['result'];

      case 'registerDevice':
        return { type: 'deviceInfo', value: DEMO_PAIRING } as MethodMap[M]['result'];

      case 'subscribe':
      case 'unsubscribe':
        return { type: 'ok' } as MethodMap[M]['result'];

      case 'listProjects':
        return { type: 'projects', value: this.projects } as MethodMap[M]['result'];

      case 'selectProject':
        return { type: 'ok' } as MethodMap[M]['result'];

      case 'listWorktrees': {
        const p = (params as MethodParams<'listWorktrees'>)!.value;
        const wts = this.worktreesByProject[p.projectID] ?? [];
        return { type: 'worktrees', value: wts } as MethodMap[M]['result'];
      }

      case 'selectWorktree':
        return { type: 'ok' } as MethodMap[M]['result'];

      case 'getWorkspace': {
        const p = (params as MethodParams<'getWorkspace'>)!.value;
        const ws = this.workspaces[p.projectID];
        if (!ws) throw demoError(404, 'Project not found');
        return { type: 'workspace', value: ws } as MethodMap[M]['result'];
      }

      case 'createTab':
      case 'closeTab':
      case 'selectTab':
      case 'splitArea':
      case 'closeArea':
      case 'focusArea':
        return { type: 'ok' } as MethodMap[M]['result'];

      case 'takeOverPane': {
        const p = (params as MethodParams<'takeOverPane'>)!.value;
        this.emit('paneOwnershipChanged', {
          type: 'paneOwnership',
          value: {
            paneID: p.paneID,
            owner: { remote: { deviceID: DEMO_CLIENT_ID, deviceName: 'iPhone (Demo)' } },
          },
        });
        this.scheduleGreeting(p.paneID);
        return { type: 'ok' } as MethodMap[M]['result'];
      }

      case 'releasePane': {
        const p = (params as MethodParams<'releasePane'>)!.value;
        this.emit('paneOwnershipChanged', {
          type: 'paneOwnership',
          value: {
            paneID: p.paneID,
            owner: { mac: { deviceName: DEMO_DEVICE_NAME } },
          },
        });
        return { type: 'ok' } as MethodMap[M]['result'];
      }

      case 'terminalResize':
      case 'terminalScroll':
        return { type: 'ok' } as MethodMap[M]['result'];

      case 'terminalInput':
        return { type: 'ok' } as MethodMap[M]['result'];

      case 'getTerminalContent':
        throw demoError(404, 'Not available in demo mode');

      case 'getProjectLogo':
        throw demoError(404, 'Not available in demo mode');

      case 'getVCSStatus':
      case 'vcsRefresh': {
        const p = (params as MethodParams<'getVCSStatus'>)!.value;
        const status = this.statusByProject[p.projectID];
        if (!status) throw demoError(404, 'Project not found');
        return { type: 'vcsStatus', value: status } as MethodMap[M]['result'];
      }

      case 'vcsListBranches': {
        const p = (params as MethodParams<'vcsListBranches'>)!.value;
        const branches = this.branchesByProject[p.projectID];
        if (!branches) throw demoError(404, 'Project not found');
        return { type: 'vcsBranches', value: branches } as MethodMap[M]['result'];
      }

      case 'vcsSwitchBranch': {
        const p = (params as MethodParams<'vcsSwitchBranch'>)!.value;
        const current = this.branchesByProject[p.projectID];
        if (!current) throw demoError(404, 'Project not found');
        this.branchesByProject[p.projectID] = { ...current, current: p.branch };
        const status = this.statusByProject[p.projectID];
        if (status) {
          this.statusByProject[p.projectID] = {
            ...status,
            branch: p.branch,
            aheadCount: 0,
            behindCount: 0,
            pullRequest: undefined,
          };
        }
        return { type: 'ok' } as MethodMap[M]['result'];
      }

      case 'vcsCreateBranch': {
        const p = (params as MethodParams<'vcsCreateBranch'>)!.value;
        const current = this.branchesByProject[p.projectID];
        if (!current) throw demoError(404, 'Project not found');
        const locals = current.locals.includes(p.name) ? current.locals : [...current.locals, p.name];
        this.branchesByProject[p.projectID] = { ...current, current: p.name, locals };
        return { type: 'ok' } as MethodMap[M]['result'];
      }

      case 'vcsStageFiles': {
        const p = (params as MethodParams<'vcsStageFiles'>)!.value;
        const status = this.statusByProject[p.projectID];
        if (!status) throw demoError(404, 'Project not found');
        const moving = status.changedFiles.filter((f) => p.paths.includes(f.path));
        this.statusByProject[p.projectID] = {
          ...status,
          stagedFiles: [...status.stagedFiles, ...moving],
          changedFiles: status.changedFiles.filter((f) => !p.paths.includes(f.path)),
        };
        return { type: 'ok' } as MethodMap[M]['result'];
      }

      case 'vcsUnstageFiles': {
        const p = (params as MethodParams<'vcsUnstageFiles'>)!.value;
        const status = this.statusByProject[p.projectID];
        if (!status) throw demoError(404, 'Project not found');
        const moving = status.stagedFiles.filter((f) => p.paths.includes(f.path));
        this.statusByProject[p.projectID] = {
          ...status,
          stagedFiles: status.stagedFiles.filter((f) => !p.paths.includes(f.path)),
          changedFiles: [...status.changedFiles, ...moving],
        };
        return { type: 'ok' } as MethodMap[M]['result'];
      }

      case 'vcsDiscardFiles': {
        const p = (params as MethodParams<'vcsDiscardFiles'>)!.value;
        const status = this.statusByProject[p.projectID];
        if (!status) throw demoError(404, 'Project not found');
        const drop = new Set([...p.paths, ...p.untrackedPaths]);
        this.statusByProject[p.projectID] = {
          ...status,
          changedFiles: status.changedFiles.filter((f) => !drop.has(f.path)),
        };
        return { type: 'ok' } as MethodMap[M]['result'];
      }

      case 'vcsCommit': {
        const p = (params as MethodParams<'vcsCommit'>)!.value;
        const status = this.statusByProject[p.projectID];
        if (!status) throw demoError(404, 'Project not found');
        this.statusByProject[p.projectID] = {
          ...status,
          aheadCount: status.aheadCount + 1,
          stagedFiles: [],
          changedFiles: p.stageAll ? [] : status.changedFiles,
        };
        return { type: 'ok' } as MethodMap[M]['result'];
      }

      case 'vcsPush': {
        const p = (params as MethodParams<'vcsPush'>)!.value;
        const status = this.statusByProject[p.projectID];
        if (!status) throw demoError(404, 'Project not found');
        this.statusByProject[p.projectID] = { ...status, aheadCount: 0, hasUpstream: true };
        return { type: 'ok' } as MethodMap[M]['result'];
      }

      case 'vcsPull': {
        const p = (params as MethodParams<'vcsPull'>)!.value;
        const status = this.statusByProject[p.projectID];
        if (!status) throw demoError(404, 'Project not found');
        this.statusByProject[p.projectID] = { ...status, behindCount: 0 };
        return { type: 'ok' } as MethodMap[M]['result'];
      }

      case 'vcsCreatePR': {
        const p = (params as MethodParams<'vcsCreatePR'>)!.value;
        const status = this.statusByProject[p.projectID];
        if (!status) throw demoError(404, 'Project not found');
        const pr = {
          url: 'https://github.com/muxy-app/demo/pull/42',
          number: 42,
          state: 'open',
          isDraft: p.draft,
          baseBranch: p.baseBranch ?? 'main',
        };
        this.statusByProject[p.projectID] = { ...status, pullRequest: pr };
        return { type: 'vcsPRCreated', value: { url: pr.url, number: pr.number } } as MethodMap[M]['result'];
      }

      case 'vcsMergePullRequest':
        return { type: 'ok' } as MethodMap[M]['result'];

      case 'vcsAddWorktree': {
        const p = (params as MethodParams<'vcsAddWorktree'>)!.value;
        const list = this.worktreesByProject[p.projectID] ?? [];
        const wt: Worktree = {
          id: `${p.projectID}-${Date.now().toString(16)}`,
          name: p.name,
          path: `/Users/demo/Projects/${p.name}`,
          branch: p.branch,
          isPrimary: false,
          canBeRemoved: true,
          createdAt: new Date().toISOString(),
        };
        const next = [...list, wt];
        this.worktreesByProject[p.projectID] = next;

        if (p.createBranch) {
          const branches = this.branchesByProject[p.projectID];
          if (branches && !branches.locals.includes(p.branch)) {
            this.branchesByProject[p.projectID] = {
              ...branches,
              locals: [...branches.locals, p.branch],
            };
          }
        }
        return { type: 'worktrees', value: next } as MethodMap[M]['result'];
      }

      case 'vcsRemoveWorktree': {
        const p = (params as MethodParams<'vcsRemoveWorktree'>)!.value;
        const list = this.worktreesByProject[p.projectID] ?? [];
        this.worktreesByProject[p.projectID] = list.filter((w) => w.id !== p.worktreeID);
        return { type: 'ok' } as MethodMap[M]['result'];
      }

      default:
        throw demoError(400, `Demo mode does not implement "${String(method)}"`);
    }
  }
}

function demoError(code: number, message: string): Error & { code: number } {
  const err = new Error(message) as Error & { code: number };
  err.name = 'DemoError';
  err.code = code;
  return err;
}
