import { render, screen, within } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { Text } from 'react-native';
import type { GestureType } from 'react-native-gesture-handler';

import { TerminalInteractionLayout } from './TerminalInteractionLayout';

jest.mock('react-native-gesture-handler', () => {
  const React = jest.requireActual('react');
  const { View } = jest.requireActual('react-native');

  function MockGestureDetector({ children }: { children: ReactNode }) {
    return React.createElement(View, { testID: 'tab-swipe-detector' }, children);
  }

  return { GestureDetector: MockGestureDetector };
});

describe('TerminalInteractionLayout', () => {
  it('keeps the key bar outside the tab swipe detector', () => {
    render(
      <TerminalInteractionLayout
        tabSwipeGesture={{} as GestureType}
        keyBar={<Text>Key bar</Text>}>
        <Text>Terminal area</Text>
      </TerminalInteractionLayout>,
    );

    const swipeDetector = screen.getByTestId('tab-swipe-detector');

    expect(within(swipeDetector).getByText('Terminal area')).toBeTruthy();
    expect(within(swipeDetector).queryByText('Key bar')).toBeNull();
    expect(screen.getByText('Key bar')).toBeTruthy();
  });
});
