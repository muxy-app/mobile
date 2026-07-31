import type { Project } from '@/transport';

import { filterProjectsByWorkspace, getProjectWorkspaces } from './projectWorkspaces';

function project(
  id: string,
  workspaceID?: string,
  workspaceName?: string,
): Project {
  return {
    id,
    name: id,
    path: `/projects/${id}`,
    sortOrder: 0,
    createdAt: '2026-07-31T10:00:00Z',
    workspaceID,
    workspaceName,
  };
}

describe('project workspaces', () => {
  const workWorkspaceID = '11111111-1111-1111-1111-111111111111';
  const personalWorkspaceID = '22222222-2222-2222-2222-222222222222';

  it('derives distinct workspaces in project order', () => {
    const projects = [
      project('work-1', workWorkspaceID, 'Work'),
      project('personal-1', personalWorkspaceID, 'Personal'),
      project('work-2', workWorkspaceID, 'Work'),
    ];

    expect(getProjectWorkspaces(projects)).toEqual([
      { id: workWorkspaceID, name: 'Work' },
      { id: personalWorkspaceID, name: 'Personal' },
    ]);
  });

  it('ignores projects with incomplete workspace metadata', () => {
    const projects = [
      project('without-workspace'),
      project('without-name', workWorkspaceID),
      project('without-id', undefined, 'Work'),
    ];

    expect(getProjectWorkspaces(projects)).toEqual([]);
  });

  it('filters projects for a valid selected workspace', () => {
    const workProject = project('work', workWorkspaceID, 'Work');
    const personalProject = project('personal', personalWorkspaceID, 'Personal');
    const projects = [workProject, personalProject];
    const workspaces = getProjectWorkspaces(projects);

    expect(filterProjectsByWorkspace(projects, workspaces, personalWorkspaceID)).toEqual([
      personalProject,
    ]);
  });

  it('returns every project for All or a missing workspace', () => {
    const projects = [
      project('work', workWorkspaceID, 'Work'),
      project('personal', personalWorkspaceID, 'Personal'),
    ];
    const workspaces = getProjectWorkspaces(projects);

    expect(filterProjectsByWorkspace(projects, workspaces, null)).toBe(projects);
    expect(
      filterProjectsByWorkspace(
        projects,
        workspaces,
        '33333333-3333-3333-3333-333333333333',
      ),
    ).toBe(projects);
  });
});
