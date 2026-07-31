import type { Project } from '@/transport';

export type ProjectWorkspace = {
  id: string;
  name: string;
};

export function getProjectWorkspaces(projects: Project[]): ProjectWorkspace[] {
  const seen = new Set<string>();
  const workspaces: ProjectWorkspace[] = [];

  for (const project of projects) {
    if (project.workspaceID === undefined || project.workspaceName === undefined) continue;
    if (seen.has(project.workspaceID)) continue;

    seen.add(project.workspaceID);
    workspaces.push({ id: project.workspaceID, name: project.workspaceName });
  }

  return workspaces;
}

export function filterProjectsByWorkspace(
  projects: Project[],
  workspaces: ProjectWorkspace[],
  selectedWorkspaceID: string | null,
): Project[] {
  if (!selectedWorkspaceID) return projects;
  if (!workspaces.some((workspace) => workspace.id === selectedWorkspaceID)) return projects;
  return projects.filter((project) => project.workspaceID === selectedWorkspaceID);
}
