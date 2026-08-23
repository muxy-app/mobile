import type { PropsWithChildren, ReactNode } from 'react';
import { StyleSheet, type StyleProp, View, type ViewStyle } from 'react-native';
import { GestureDetector, type GestureType } from 'react-native-gesture-handler';

type Props = PropsWithChildren<{
  tabSwipeGesture: GestureType;
  keyBar: ReactNode;
  style?: StyleProp<ViewStyle>;
}>;

export function TerminalInteractionLayout({
  tabSwipeGesture,
  keyBar,
  style,
  children,
}: Props) {
  return (
    <View style={[styles.root, style]}>
      <GestureDetector gesture={tabSwipeGesture}>
        <View style={styles.terminalArea}>{children}</View>
      </GestureDetector>
      {keyBar}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  terminalArea: { flex: 1 },
});
