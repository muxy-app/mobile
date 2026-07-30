import { useLocalSearchParams } from 'expo-router';

import { SSHConnectionForm } from '@/components/ssh/SSHConnectionForm';

export default function SSHConnectionFormRoute() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  return <SSHConnectionForm connectionId={id} />;
}
