import { Platform } from 'react-native';

import { isSSHAvailable } from '../../modules/muxy-ssh';

export type SSHSupport = 'available' | 'disabled-expo-go' | 'hidden';

export function getSSHSupport(): SSHSupport {
  if (Platform.OS === 'android') return 'hidden';
  if (Platform.OS !== 'ios') return 'hidden';
  return isSSHAvailable() ? 'available' : 'disabled-expo-go';
}
