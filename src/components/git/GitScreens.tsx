import { OverviewScreen } from './screens/OverviewScreen';
import { BranchesScreen } from './screens/BranchesScreen';
import { CommitScreen } from './screens/CommitScreen';
import { CreatePRScreen } from './screens/CreatePRScreen';
import { FileDiffScreen } from './screens/FileDiffScreen';
import { NewBranchScreen } from './screens/NewBranchScreen';
import { NewWorktreeScreen } from './screens/NewWorktreeScreen';
import { PullRequestScreen } from './screens/PullRequestScreen';
import { WorktreesScreen } from './screens/WorktreesScreen';

export type GitRoute =
  | { name: 'overview' }
  | { name: 'branches' }
  | { name: 'worktrees' }
  | { name: 'commit' }
  | { name: 'createPR' }
  | { name: 'pullRequest' }
  | { name: 'newBranch' }
  | { name: 'newWorktree' }
  | { name: 'fileDiff'; filePath: string };

type Props = {
  projectId: string;
  route: GitRoute;
  setRoute: (r: GitRoute) => void;
  onClose: () => void;
};

export function GitScreens({ projectId, route, setRoute, onClose }: Props) {
  switch (route.name) {
    case 'overview':
      return <OverviewScreen projectId={projectId} setRoute={setRoute} />;
    case 'branches':
      return <BranchesScreen projectId={projectId} setRoute={setRoute} />;
    case 'worktrees':
      return <WorktreesScreen projectId={projectId} setRoute={setRoute} onClose={onClose} />;
    case 'commit':
      return <CommitScreen projectId={projectId} setRoute={setRoute} />;
    case 'createPR':
      return <CreatePRScreen projectId={projectId} setRoute={setRoute} />;
    case 'pullRequest':
      return <PullRequestScreen projectId={projectId} setRoute={setRoute} />;
    case 'newBranch':
      return <NewBranchScreen projectId={projectId} setRoute={setRoute} />;
    case 'newWorktree':
      return <NewWorktreeScreen projectId={projectId} setRoute={setRoute} />;
    case 'fileDiff':
      return <FileDiffScreen projectId={projectId} filePath={route.filePath} />;
  }
}
