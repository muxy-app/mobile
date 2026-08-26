import { requireNativeViewManager, requireOptionalNativeModule } from 'expo-modules-core';

import { resolveTerminalInput } from './TerminalInputCapture.ios';
import { TerminalTextInput } from './TerminalTextInput';

jest.mock('expo-modules-core', () => ({
  requireNativeViewManager: jest.fn(() => function NativeTerminalInput() {
    return null;
  }),
  requireOptionalNativeModule: jest.fn(() => ({})),
}));

const optionalModule = requireOptionalNativeModule as jest.Mock;
const viewManager = requireNativeViewManager as jest.Mock;

describe('resolveTerminalInput', () => {
  let warn: jest.SpyInstance;

  beforeEach(() => {
    jest.clearAllMocks();
    warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    warn.mockRestore();
  });

  it('falls back to the managed text input when the native module is unavailable', () => {
    optionalModule.mockReturnValue(null);

    expect(resolveTerminalInput()).toBe(TerminalTextInput);
    expect(viewManager).not.toHaveBeenCalled();
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('MuxyTerminalInput'));
  });

  it('uses the native input view when the native module is available', () => {
    optionalModule.mockReturnValue({});

    expect(resolveTerminalInput()).not.toBe(TerminalTextInput);
    expect(viewManager).toHaveBeenCalledWith('MuxyTerminalInput');
    expect(warn).not.toHaveBeenCalled();
  });
});
