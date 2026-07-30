import { Platform } from 'react-native';

import { isSSHAvailable } from '../../modules/muxy-ssh';
import { getSSHSupport } from './availability';

jest.mock('../../modules/muxy-ssh', () => ({
  isSSHAvailable: jest.fn(),
}));

const mockIsSSHAvailable = jest.mocked(isSSHAvailable);
const originalPlatform = Platform.OS;

function setPlatform(platform: typeof Platform.OS): void {
  Object.defineProperty(Platform, 'OS', {
    configurable: true,
    value: platform,
  });
}

describe('getSSHSupport', () => {
  afterEach(() => {
    setPlatform(originalPlatform);
    mockIsSSHAvailable.mockReset();
  });

  it('is available in an iOS build with the native module', () => {
    setPlatform('ios');
    mockIsSSHAvailable.mockReturnValue(true);

    expect(getSSHSupport()).toBe('available');
  });

  it('is disabled in Expo Go on iOS', () => {
    setPlatform('ios');
    mockIsSSHAvailable.mockReturnValue(false);

    expect(getSSHSupport()).toBe('disabled-expo-go');
  });

  it.each(['android', 'web'] as const)(
    'is hidden on %s',
    (platform) => {
      setPlatform(platform);
      mockIsSSHAvailable.mockReturnValue(true);

      expect(getSSHSupport()).toBe('hidden');
      expect(mockIsSSHAvailable).not.toHaveBeenCalled();
    },
  );
});
