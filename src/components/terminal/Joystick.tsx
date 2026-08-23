import { Ionicons } from '@expo/vector-icons';
import {
  GlassView,
  isGlassEffectAPIAvailable,
  isLiquidGlassAvailable,
} from 'expo-glass-effect';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { PanResponder, StyleSheet, View } from 'react-native';

import { useTokens } from '@/theme';

export type JoystickDirection = 'up' | 'down' | 'left' | 'right';

type Props = {
  size?: number;
  onDirection: (dir: JoystickDirection) => void;
};

const KNOB_RATIO = 0.34;
const DEAD_ZONE_RATIO = 0.45;
const REPEAT_MS = 160;

export function Joystick({ size = 56, onDirection }: Props) {
  const tokens = useTokens();
  const glassAvailable = isGlassEffectAPIAvailable() && isLiquidGlassAvailable();
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
    <GlassView
      {...responder.panHandlers}
      glassEffectStyle="regular"
      colorScheme={tokens.mode}
      accessibilityLabel="Arrow navigation joystick"
      accessibilityHint="Drag toward a direction to send repeated arrow keys"
      style={[
        styles.pad,
        {
          width: size,
          height: size,
          borderRadius: radius,
        },
        !glassAvailable && {
          backgroundColor: tokens.surface.tertiary,
          borderColor: tokens.border.subtle,
          borderWidth: StyleSheet.hairlineWidth,
        },
      ]}>
      <Ionicons
        pointerEvents="none"
        name="chevron-up"
        size={10}
        color={tokens.text.muted}
        style={[styles.directionIcon, styles.up]}
      />
      <Ionicons
        pointerEvents="none"
        name="chevron-down"
        size={10}
        color={tokens.text.muted}
        style={[styles.directionIcon, styles.down]}
      />
      <Ionicons
        pointerEvents="none"
        name="chevron-back"
        size={10}
        color={tokens.text.muted}
        style={[styles.directionIcon, styles.left]}
      />
      <Ionicons
        pointerEvents="none"
        name="chevron-forward"
        size={10}
        color={tokens.text.muted}
        style={[styles.directionIcon, styles.right]}
      />
      <View
        pointerEvents="none"
        style={[
          {
            width: knobSize,
            height: knobSize,
            borderRadius: knobSize / 2,
            backgroundColor: tokens.text.primary,
            transform: [{ translateX: knob.x }, { translateY: knob.y }],
          },
        ]}
      />
    </GlassView>
  );
}

const styles = StyleSheet.create({
  pad: {
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  directionIcon: {
    position: 'absolute',
  },
  up: {
    top: 4,
    left: '50%',
    marginLeft: -5,
  },
  down: {
    bottom: 4,
    left: '50%',
    marginLeft: -5,
  },
  left: {
    left: 5,
    top: '50%',
    marginTop: -5,
  },
  right: {
    right: 5,
    top: '50%',
    marginTop: -5,
  },
});
