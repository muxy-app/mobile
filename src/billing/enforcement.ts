import { Platform, type PlatformOSType } from 'react-native';

export function isBillingEnforced(platform: PlatformOSType = Platform.OS, dev: boolean = __DEV__): boolean {
  return platform !== 'ios' && !dev;
}
