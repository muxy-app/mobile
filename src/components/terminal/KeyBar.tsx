import { Ionicons } from '@expo/vector-icons';
import * as Clipboard from 'expo-clipboard';
import {
  GlassView,
  isGlassEffectAPIAvailable,
  isLiquidGlassAvailable,
} from 'expo-glass-effect';
import { useRef, useState } from 'react';
import { Animated, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { create } from 'zustand';

import { bytesToBase64, stringToBase64 } from '@/lib/base64';
import { useTokens } from '@/theme';

import { Joystick, type JoystickDirection } from './Joystick';

export type Modifier = 'ctrl' | 'shift' | 'alt' | 'meta';

export const KEY_BAR_HEIGHT = 64;

const JOYSTICK_SIZE = 48;
const JOYSTICK_GAP = 10;
const KEY_BAR_SIDE_PADDING = 16;

type ModifierState = {
  active: Modifier | null;
  slot: Modifier;
  set: (m: Modifier | null) => void;
  setSlot: (m: Modifier) => void;
};

const useModifierStore = create<ModifierState>((set) => ({
  active: null,
  slot: 'ctrl',
  set: (m) => set({ active: m }),
  setSlot: (m) => set({ slot: m }),
}));

const MODIFIER_OPTIONS: { id: Modifier; label: string; symbol: string }[] = [
  { id: 'ctrl', label: 'ctrl', symbol: '⌃' },
  { id: 'shift', label: 'shift', symbol: '⇧' },
  { id: 'alt', label: 'alt', symbol: '⌥' },
  { id: 'meta', label: 'cmd', symbol: '⌘' },
];

const ESC = new Uint8Array([0x1b]);
const TAB = new Uint8Array([0x09]);
const TILDE = new Uint8Array([0x7e]);
const SLASH = new Uint8Array([0x2f]);
const PIPE = new Uint8Array([0x7c]);
const DASH = new Uint8Array([0x2d]);
const ARROW_UP = new Uint8Array([0x1b, 0x5b, 0x41]);
const ARROW_DOWN = new Uint8Array([0x1b, 0x5b, 0x42]);
const ARROW_RIGHT = new Uint8Array([0x1b, 0x5b, 0x43]);
const ARROW_LEFT = new Uint8Array([0x1b, 0x5b, 0x44]);

export function KeyBar({
  onBytes,
}: {
  onBytes: (base64: string) => void;
}) {
  const glassAvailable = isGlassEffectAPIAvailable() && isLiquidGlassAvailable();
  const active = useModifierStore((s) => s.active);
  const slot = useModifierStore((s) => s.slot);
  const setActive = useModifierStore((s) => s.set);
  const setSlot = useModifierStore((s) => s.setSlot);
  const railOffset = useRef(new Animated.Value(0)).current;
  const leadingEdgeOpacity = railOffset.interpolate({
    inputRange: [0, 8],
    outputRange: [0, 1],
    extrapolate: 'clamp',
  });

  const send = (bytes: Uint8Array) => onBytes(bytesToBase64(bytes));

  const onJoystick = (dir: JoystickDirection) => {
    switch (dir) {
      case 'up':
        return send(ARROW_UP);
      case 'down':
        return send(ARROW_DOWN);
      case 'left':
        return send(ARROW_LEFT);
      case 'right':
        return send(ARROW_RIGHT);
    }
  };

  const onPaste = async () => {
    try {
      const text = await Clipboard.getStringAsync();
      if (text) onBytes(stringToBase64(text));
    } catch {
      void 0;
    }
  };

  return (
    <View style={styles.row}>
      <Animated.ScrollView
        horizontal
        style={styles.rail}
        onScroll={Animated.event(
          [{ nativeEvent: { contentOffset: { x: railOffset } } }],
          { useNativeDriver: true },
        )}
        scrollEventThrottle={16}
        showsHorizontalScrollIndicator={false}
        showsVerticalScrollIndicator={false}
        alwaysBounceVertical={false}
        directionalLockEnabled
        keyboardShouldPersistTaps="always"
        contentContainerStyle={styles.railContent}>
        <GlassKeyButton
          label="esc"
          accessibilityLabel="Escape"
          glassAvailable={glassAvailable}
          onPress={() => send(ESC)}
        />
        <ModifierKey
          slot={slot}
          active={active}
          glassAvailable={glassAvailable}
          onTap={() => setActive(active === slot ? null : slot)}
          onPickFromMenu={(m) => {
            setSlot(m);
            setActive(m);
          }}
        />
        <GlassKeyButton
          label="tab"
          accessibilityLabel="Tab"
          glassAvailable={glassAvailable}
          onPress={() => send(TAB)}
        />
        <GlassKeyButton
          label="~"
          accessibilityLabel="Tilde"
          glassAvailable={glassAvailable}
          onPress={() => send(TILDE)}
        />
        <GlassKeyButton
          label="/"
          accessibilityLabel="Slash"
          glassAvailable={glassAvailable}
          onPress={() => send(SLASH)}
        />
        <GlassIconButton
          icon="clipboard-outline"
          accessibilityLabel="Paste"
          glassAvailable={glassAvailable}
          onPress={onPaste}
        />
        <GlassKeyButton
          label="|"
          accessibilityLabel="Pipe"
          glassAvailable={glassAvailable}
          onPress={() => send(PIPE)}
        />
        <GlassKeyButton
          label="-"
          accessibilityLabel="Dash"
          glassAvailable={glassAvailable}
          onPress={() => send(DASH)}
        />
      </Animated.ScrollView>

      <LeadingEdgeShadow opacity={leadingEdgeOpacity} />
      <JoystickDock onDirection={onJoystick} />
    </View>
  );
}

function LeadingEdgeShadow({
  opacity,
}: {
  opacity: Animated.AnimatedInterpolation<number>;
}) {
  const tokens = useTokens();

  return (
    <Animated.View
      pointerEvents="none"
      style={[
        styles.leadingEdgeShadow,
        {
          backgroundColor: tokens.surface.primary,
          shadowColor: tokens.surface.primary,
          opacity,
        },
      ]}
    />
  );
}

function JoystickDock({
  onDirection,
}: {
  onDirection: (direction: JoystickDirection) => void;
}) {
  const tokens = useTokens();

  return (
    <View
      style={[
        styles.joystickDock,
        {
          backgroundColor: tokens.surface.primary,
          shadowColor: tokens.surface.primary,
        },
      ]}>
      <Joystick size={JOYSTICK_SIZE} onDirection={onDirection} />
    </View>
  );
}

export function transformWithModifiers(base64: string): string {
  const { active, set } = useModifierStore.getState();
  if (!active) return base64;

  let bytes: Uint8Array;
  try {
    const bin = globalThis.atob(base64);
    bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  } catch {
    return base64;
  }
  if (bytes.length === 0) return base64;

  const ch = bytes[0]!;
  let result = bytes;

  if (active === 'ctrl') {
    let mapped: number | null = null;
    if (ch >= 0x40 && ch <= 0x5f) mapped = ch - 0x40;
    else if (ch >= 0x60 && ch <= 0x7e) mapped = ch - 0x60;
    else if (ch === 0x20) mapped = 0x00;
    else if (ch === 0x3f) mapped = 0x7f;
    if (mapped !== null) result = new Uint8Array([mapped]);
  } else if (active === 'alt' || active === 'meta') {
    const prefixed = new Uint8Array(result.length + 1);
    prefixed[0] = 0x1b;
    prefixed.set(result, 1);
    result = prefixed;
  }

  set(null);
  return bytesToBase64(result);
}

function GlassKeyButton({
  label,
  accessibilityLabel,
  glassAvailable,
  onPress,
}: {
  label: string;
  accessibilityLabel: string;
  glassAvailable: boolean;
  onPress: () => void;
}) {
  const tokens = useTokens();

  return (
    <Pressable
      onPress={onPress}
      hitSlop={4}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      style={({ pressed }) => [styles.glassKey, pressed && styles.keyPressed]}>
      <KeyGlassSurface glassAvailable={glassAvailable}>
        <Text style={[styles.keyLabel, { color: tokens.text.primary }]}>{label}</Text>
      </KeyGlassSurface>
    </Pressable>
  );
}

function GlassIconButton({
  icon,
  accessibilityLabel,
  glassAvailable,
  onPress,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  accessibilityLabel: string;
  glassAvailable: boolean;
  onPress: () => void;
}) {
  const tokens = useTokens();

  return (
    <Pressable
      onPress={onPress}
      hitSlop={4}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      style={({ pressed }) => [styles.glassKey, pressed && styles.keyPressed]}>
      <KeyGlassSurface glassAvailable={glassAvailable}>
        <Ionicons name={icon} size={18} color={tokens.text.primary} />
      </KeyGlassSurface>
    </Pressable>
  );
}

function KeyGlassSurface({
  children,
  glassAvailable,
  active = false,
}: {
  children: React.ReactNode;
  glassAvailable: boolean;
  active?: boolean;
}) {
  const tokens = useTokens();

  return (
    <GlassView
      isInteractive
      glassEffectStyle="regular"
      colorScheme={tokens.mode}
      tintColor={active ? tokens.accent.primary : undefined}
      style={[
        styles.keyGlass,
        !glassAvailable && {
          backgroundColor: active ? tokens.accent.primary : tokens.surface.secondary,
          borderColor: active ? tokens.accent.primary : tokens.border.subtle,
          borderWidth: StyleSheet.hairlineWidth,
        },
      ]}>
      {children}
    </GlassView>
  );
}

function ModifierKey({
  slot,
  active,
  glassAvailable,
  onTap,
  onPickFromMenu,
}: {
  slot: Modifier;
  active: Modifier | null;
  glassAvailable: boolean;
  onTap: () => void;
  onPickFromMenu: (m: Modifier) => void;
}) {
  const tokens = useTokens();
  const [menuOpen, setMenuOpen] = useState(false);
  const [anchor, setAnchor] = useState<{ x: number; y: number; width: number } | null>(null);
  const buttonRef = useRef<View>(null);

  const slotOption = MODIFIER_OPTIONS.find((o) => o.id === slot) ?? MODIFIER_OPTIONS[0]!;
  const isArmed = active === slot;

  const openMenu = () => {
    buttonRef.current?.measureInWindow((x, y, width) => {
      setAnchor({ x, y, width });
      setMenuOpen(true);
    });
  };

  return (
    <>
      <Pressable
        ref={buttonRef}
        onPress={onTap}
        onLongPress={openMenu}
        delayLongPress={300}
        hitSlop={4}
        accessibilityRole="button"
        accessibilityLabel={`${slotOption.label} modifier`}
        accessibilityState={{ selected: isArmed }}
        style={({ pressed }) => [
          styles.glassKey,
          styles.modifierKey,
          pressed && styles.keyPressed,
        ]}>
        <KeyGlassSurface glassAvailable={glassAvailable} active={isArmed}>
          <View style={styles.modifierLabelRow}>
            <Text
              style={[
                styles.keyLabel,
                { color: isArmed ? tokens.accent.contrast : tokens.text.primary },
              ]}>
              {slotOption.label}
            </Text>
            <Ionicons
              name="chevron-up"
              size={11}
              color={isArmed ? tokens.accent.contrast : tokens.text.muted}
            />
          </View>
        </KeyGlassSurface>
      </Pressable>

      <Modal
        visible={menuOpen}
        transparent
        animationType="fade"
        onRequestClose={() => setMenuOpen(false)}>
        <Pressable style={StyleSheet.absoluteFill} onPress={() => setMenuOpen(false)} />
        {anchor ? (
          <GlassView
            glassEffectStyle="regular"
            colorScheme={tokens.mode}
            style={[
              styles.menu,
              {
                left: Math.max(8, anchor.x - 20),
                bottom: undefined,
                top: anchor.y - MENU_HEIGHT - 8,
              },
              !glassAvailable && {
                backgroundColor: tokens.surface.secondary,
                borderColor: tokens.border.subtle,
                borderWidth: StyleSheet.hairlineWidth,
              },
            ]}>
            {MODIFIER_OPTIONS.map((opt) => {
              const isCurrent = slot === opt.id;
              return (
                <Pressable
                  key={opt.id}
                  onPress={() => {
                    onPickFromMenu(opt.id);
                    setMenuOpen(false);
                  }}
                  style={({ pressed }) => [
                    styles.menuItem,
                    {
                      backgroundColor: pressed ? tokens.surface.tertiary : 'transparent',
                    },
                  ]}>
                  <Text
                    style={[
                      styles.menuSymbol,
                      {
                        color: isCurrent ? tokens.accent.primary : tokens.text.muted,
                      },
                    ]}>
                    {opt.symbol}
                  </Text>
                  <Text
                    style={[
                      styles.menuLabel,
                      {
                        color: isCurrent ? tokens.accent.primary : tokens.text.primary,
                      },
                    ]}>
                    {opt.label}
                  </Text>
                </Pressable>
              );
            })}
          </GlassView>
        ) : null}
      </Modal>
    </>
  );
}

const MENU_HEIGHT = 4 * 44 + 12;

const styles = StyleSheet.create({
  row: {
    position: 'relative',
    height: KEY_BAR_HEIGHT,
    flexDirection: 'row',
    overflow: 'hidden',
    alignItems: 'center',
    paddingHorizontal: KEY_BAR_SIDE_PADDING,
    paddingVertical: 8,
  },
  rail: {
    flex: 1,
  },
  railContent: {
    alignItems: 'center',
    gap: 8,
    paddingLeft: 1,
    paddingRight: JOYSTICK_SIZE + JOYSTICK_GAP,
    paddingVertical: 6,
  },
  leadingEdgeShadow: {
    position: 'absolute',
    top: 0,
    left: 0,
    zIndex: 1,
    width: KEY_BAR_SIDE_PADDING,
    height: KEY_BAR_HEIGHT,
    shadowOffset: { width: 8, height: 0 },
    shadowOpacity: 1,
    shadowRadius: 8,
  },
  joystickDock: {
    position: 'absolute',
    top: 0,
    right: 0,
    zIndex: 1,
    width: JOYSTICK_SIZE + KEY_BAR_SIDE_PADDING,
    height: KEY_BAR_HEIGHT,
    alignItems: 'flex-end',
    justifyContent: 'center',
    paddingRight: KEY_BAR_SIDE_PADDING,
    shadowOffset: { width: -8, height: 0 },
    shadowOpacity: 1,
    shadowRadius: 8,
  },
  glassKey: {
    minWidth: 44,
    height: 36,
  },
  modifierKey: {
    minWidth: 62,
  },
  keyGlass: {
    flex: 1,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 10,
    overflow: 'hidden',
  },
  keyPressed: {
    transform: [{ scale: 0.96 }],
  },
  keyLabel: { fontSize: 13, fontWeight: '600' },
  modifierLabelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
  },
  menu: {
    position: 'absolute',
    minWidth: 160,
    paddingVertical: 6,
    borderRadius: 18,
    overflow: 'hidden',
  },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  menuSymbol: { fontSize: 14, fontWeight: '500', width: 18, textAlign: 'center' },
  menuLabel: { fontSize: 14, fontWeight: '500' },
});
