import { useCallback, useEffect } from 'react';

import { client } from './connection';
import { useDevicesStore } from './devicesStore';
import { useProjectsStore } from './projectsStore';

export function useProjects() {
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);
  const activeDeviceId = useDevicesStore((s) => s.activeDeviceId);

  const setProjects = useProjectsStore((s) => s.setProjects);
  const setFetchPhase = useProjectsStore((s) => s.setFetchPhase);
  const clear = useProjectsStore((s) => s.clear);

  const fetchProjects = useCallback(async () => {
    setFetchPhase('loading');
    try {
      const result = await client.request('listProjects', null);
      setProjects(result.value);
      setFetchPhase('loaded');
    } catch (err) {
      setFetchPhase('error', err instanceof Error ? err.message : 'Failed to load projects');
    }
  }, [setFetchPhase, setProjects]);

  useEffect(() => {
    clear();
    if (connectionPhase !== 'connected' || !activeDeviceId) return;

    let cancelled = false;
    fetchProjects().catch(() => {});

    const off = client.on('projectsChanged', (event) => {
      if (cancelled) return;
      setProjects(event.value);
    });

    return () => {
      cancelled = true;
      off();
    };
  }, [connectionPhase, activeDeviceId, fetchProjects, setProjects, clear]);

  return { refresh: fetchProjects };
}

export function useProjectLogo(projectId: string, hasCustomLogo: boolean): string | undefined {
  const cached = useProjectsStore((s) => s.logos[projectId]);
  const setLogo = useProjectsStore((s) => s.setLogo);
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);

  useEffect(() => {
    if (!hasCustomLogo || cached || connectionPhase !== 'connected') return;
    let cancelled = false;
    client
      .request('getProjectLogo', { type: 'getProjectLogo', value: { projectID: projectId } })
      .then((result) => {
        if (cancelled) return;
        setLogo(projectId, `data:image/png;base64,${result.value.pngData}`);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [projectId, hasCustomLogo, cached, connectionPhase, setLogo]);

  return cached;
}
