import { useCallback, useEffect } from 'react';

import { client } from './connection';
import { useDevicesStore } from './devicesStore';
import { useWorkspaceStore } from './workspaceStore';

export function useWorkspace(projectId: string | undefined) {
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);
  const setWorkspace = useWorkspaceStore((s) => s.setWorkspace);
  const applyWorkspaceUpdate = useWorkspaceStore((s) => s.applyWorkspaceUpdate);
  const setFetchPhase = useWorkspaceStore((s) => s.setFetchPhase);
  const clear = useWorkspaceStore((s) => s.clear);

  const fetchWorkspace = useCallback(async () => {
    if (!projectId) return;
    setFetchPhase('loading');
    try {
      const result = await client.request('getWorkspace', {
        type: 'getWorkspace',
        value: { projectID: projectId },
      });
      setWorkspace(result.value);
      setFetchPhase('loaded');
    } catch (err) {
      setFetchPhase('error', err instanceof Error ? err.message : 'Failed to load workspace');
    }
  }, [projectId, setFetchPhase, setWorkspace]);

  useEffect(() => {
    clear();
    if (!projectId || connectionPhase !== 'connected') return;

    let cancelled = false;
    fetchWorkspace().catch(() => {});

    const offWorkspace = client.on('workspaceChanged', (event) => {
      if (cancelled) return;
      if (event.value.projectID !== projectId) return;
      applyWorkspaceUpdate(event.value);
    });

    return () => {
      cancelled = true;
      offWorkspace();
    };
  }, [projectId, connectionPhase, fetchWorkspace, applyWorkspaceUpdate, clear]);

  return { refresh: fetchWorkspace };
}
