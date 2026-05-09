import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { PanResponder, StyleSheet, View } from 'react-native';

import { useTokens } from '@/theme';

export type JoystickDirection = 'up' | 'down' | 'left' | 'right';

type Props = {
  size?: number;
  onDirection: (dir: JoystickDirection) => void;
};

const KNOB_RATIO = 0.4;
const DEAD_ZONE_RATIO = 0.45;
const REPEAT_MS = 160;

export function Joystick({ size = 56, onDirection }: Props) {
  const tokens = useTokens();
  const radius = size / 2;
  const knobSize = size * KNOB_RATIO;
  const deadZone = size * DEAD_ZONE_RATIO;

  const [knob, setKnob] = useState({ x: 0, y: 0 });
  const directionRef = useRef<JoystickDirection | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(
    () => () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    },
    [],
  );

  const stopRepeat = () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    intervalRef.current = null;
    directionRef.current = null;
  };

  const startRepeat = (dir: JoystickDirection) => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    Haptics.selectionAsync();
    onDirection(dir);
    intervalRef.current = setInterval(() => {
      Haptics.selectionAsync();
      onDirection(dir);
    }, REPEAT_MS);
    directionRef.current = dir;
  };

  const directionFor = (dx: number, dy: number): JoystickDirection | null => {
    if (Math.abs(dx) < deadZone && Math.abs(dy) < deadZone) return null;
    if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? 'right' : 'left';
    return dy > 0 ? 'down' : 'up';
  };

  const responder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderMove: (_e, g) => {
        const limit = radius - knobSize / 2;
        const dist = Math.sqrt(g.dx * g.dx + g.dy * g.dy);
        const scale = dist > limit ? limit / dist : 1;
        const x = g.dx * scale;
        const y = g.dy * scale;
        setKnob({ x, y });

        const dir = directionFor(g.dx, g.dy);
        if (dir !== directionRef.current) {
          if (dir) startRepeat(dir);
          else stopRepeat();
        }
      },
      onPanResponderRelease: () => {
        setKnob({ x: 0, y: 0 });
        stopRepeat();
      },
      onPanResponderTerminate: () => {
        setKnob({ x: 0, y: 0 });
        stopRepeat();
      },
    }),
  ).current;

  return (
    <View
      {...responder.panHandlers}
      style={[
        styles.pad,
        {
          width: size,
          height: size,
          borderRadius: radius,
          backgroundColor: tokens.surface.tertiary,
          borderColor: tokens.border.subtle,
        },
      ]}>
      <View
        style={[
          styles.knob,
          {
            width: knobSize,
            height: knobSize,
            borderRadius: knobSize / 2,
            backgroundColor: tokens.text.primary,
            transform: [{ translateX: knob.x }, { translateY: knob.y }],
          },
        ]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  pad: {
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: StyleSheet.hairlineWidth,
  },
  knob: {
    opacity: 0.85,
  },
});
