import { useCallback, useEffect, useState } from 'react';

import { client, useDevicesStore } from '@/state';
import type { VCSStatus } from '@/transport';

export type VCSStatusState = {
  status: VCSStatus | null;
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
};

export function useVCSStatus(projectId: string): VCSStatusState {
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);
  const [status, setStatus] = useState<VCSStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchStatus = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    setError(null);
    try {
      const result = await client.request('getVCSStatus', {
        type: 'getVCSStatus',
        value: { projectID: projectId },
      });
      setStatus(result.value);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load git status');
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    if (connectionPhase !== 'connected') return;
    fetchStatus();
  }, [connectionPhase, fetchStatus]);

  return { status, loading, error, reload: fetchStatus };
}
