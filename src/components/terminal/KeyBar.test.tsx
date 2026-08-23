import { act, fireEvent, render, screen, waitFor } from '@testing-library/react-native';

import { stringToBase64 } from '@/lib/base64';

import { KeyBar, transformWithModifiers } from './KeyBar';

const mockGetStringAsync = jest.fn<Promise<string>, []>();

jest.mock('expo-clipboard', () => ({
  getStringAsync: () => mockGetStringAsync(),
}));

jest.mock('@expo/vector-icons', () => {
  const React = jest.requireActual('react');
  const { Text } = jest.requireActual('react-native');

  function MockIcon({ name, ...props }: { name: string }) {
    return React.createElement(Text, props, name);
  }

  return { Ionicons: MockIcon };
});

jest.mock('expo-glass-effect', () => {
  const React = jest.requireActual('react');
  const { View } = jest.requireActual('react-native');

  function MockGlassView(props: Record<string, unknown>) {
    return React.createElement(View, props);
  }

  return {
    GlassView: MockGlassView,
    isGlassEffectAPIAvailable: () => false,
    isLiquidGlassAvailable: () => false,
  };
});

jest.mock('@/theme', () => ({
  useTokens: () => ({
    mode: 'dark',
    surface: {
      primary: '#0B0B0F',
      secondary: '#15151B',
      tertiary: '#1F1F27',
    },
    text: {
      primary: '#F5F5F7',
      secondary: '#C7C7D1',
      muted: '#7A7A85',
      inverse: '#0B0B0F',
    },
    border: {
      subtle: '#23232C',
      strong: '#34343F',
    },
    accent: {
      primary: '#A74BA7',
      contrast: '#FFFFFF',
    },
    status: {
      success: '#34D399',
      warning: '#FBBF24',
      danger: '#F87171',
    },
  }),
}));

jest.mock('./Joystick', () => ({
  Joystick: () => null,
}));

describe('KeyBar', () => {
  beforeEach(() => {
    mockGetStringAsync.mockReset();
    transformWithModifiers(stringToBase64('a'));
  });

  it('exposes every terminal key with an accessible label', () => {
    render(<KeyBar onBytes={jest.fn()} />);

    for (const label of ['Escape', 'ctrl modifier', 'Tab', 'Tilde', 'Slash', 'Paste', 'Pipe', 'Dash']) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }
  });

  it('sends escape and tab bytes', () => {
    const onBytes = jest.fn();
    render(<KeyBar onBytes={onBytes} />);

    fireEvent.press(screen.getByLabelText('Escape'));
    fireEvent.press(screen.getByLabelText('Tab'));

    expect(onBytes).toHaveBeenNthCalledWith(1, 'Gw==');
    expect(onBytes).toHaveBeenNthCalledWith(2, 'CQ==');
  });

  it('arms and consumes the selected modifier', () => {
    render(<KeyBar onBytes={jest.fn()} />);

    fireEvent.press(screen.getByLabelText('ctrl modifier'));

    expect(screen.getByLabelText('ctrl modifier').props.accessibilityState).toEqual({ selected: true });

    let modified = '';
    act(() => {
      modified = transformWithModifiers(stringToBase64('c'));
    });

    expect(modified).toBe('Aw==');
    expect(transformWithModifiers(stringToBase64('c'))).toBe(stringToBase64('c'));
  });

  it('sends clipboard text through the terminal', async () => {
    const onBytes = jest.fn();
    mockGetStringAsync.mockResolvedValue('pwd\n');
    render(<KeyBar onBytes={onBytes} />);

    fireEvent.press(screen.getByLabelText('Paste'));

    await waitFor(() => expect(onBytes).toHaveBeenCalledWith(stringToBase64('pwd\n')));
  });
});
