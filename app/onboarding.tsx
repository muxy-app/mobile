import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { Stack, useRouter } from 'expo-router';
import { useRef, useState } from 'react';
import {
  Dimensions,
  FlatList,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useSettingsStore } from '@/state';
import { useTokens } from '@/theme';

type Slide = {
  badge: keyof typeof Ionicons.glyphMap | 'app-icon';
  title: string;
  body: string;
  rows?: { icon: keyof typeof Ionicons.glyphMap; title: string; body: string }[];
};

const SLIDES: Slide[] = [
  {
    badge: 'app-icon',
    title: 'Welcome to Muxy',
    body: 'The remote control for your Mac terminal. Drive sessions, switch projects, and ship changes from your phone.',
  },
  {
    badge: 'sparkles-outline',
    title: 'How it works',
    body: '',
    rows: [
      {
        icon: 'wifi-outline',
        title: 'Same network',
        body: 'Your phone and Mac talk directly over your local network.',
      },
      {
        icon: 'toggle-outline',
        title: 'Enable the Mobile server',
        body: 'On your Mac: Muxy → Settings → Mobile, then toggle the server on.',
      },
      {
        icon: 'flash-outline',
        title: 'Stay in sync',
        body: 'Open projects, run commands, and review changes in real time.',
      },
    ],
  },
  {
    badge: 'keypad-outline',
    title: 'Pair your Mac',
    body: 'Enter your Mac’s IP address and the port shown in Muxy’s Mobile settings. Default port is 4865.',
  },
];

export default function OnboardingScreen() {
  const tokens = useTokens();
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const setOnboarded = useSettingsStore((s) => s.setOnboarded);

  const listRef = useRef<FlatList<Slide>>(null);
  const [index, setIndex] = useState(0);
  const { width } = Dimensions.get('window');

  const isLast = index === SLIDES.length - 1;

  const goNext = () => {
    if (isLast) {
      setOnboarded(true);
      router.replace('/');
      router.push('/add-device');
      return;
    }
    listRef.current?.scrollToIndex({ index: index + 1, animated: true });
  };

  const skip = () => {
    setOnboarded(true);
    router.replace('/');
  };

  const onMomentumEnd = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const next = Math.round(e.nativeEvent.contentOffset.x / width);
    if (next !== index) setIndex(next);
  };

  return (
    <View style={[styles.root, { backgroundColor: tokens.surface.primary }]}>
      <Stack.Screen options={{ headerShown: false }} />

      <View style={[styles.topBar, { paddingTop: insets.top + 8 }]}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Skip onboarding"
          onPress={skip}
          hitSlop={10}
          style={({ pressed }) => [styles.skipBtn, { opacity: pressed ? 0.5 : 1 }]}>
          <Text style={[styles.skipLabel, { color: tokens.text.muted }]}>Skip</Text>
        </Pressable>
      </View>

      <FlatList
        ref={listRef}
        data={SLIDES}
        keyExtractor={(_, i) => String(i)}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onMomentumScrollEnd={onMomentumEnd}
        getItemLayout={(_, i) => ({ length: width, offset: width * i, index: i })}
        renderItem={({ item }) => (
          <View style={[styles.page, { width }]}>
            <SlideContent slide={item} />
          </View>
        )}
      />

      <View style={[styles.footer, { paddingBottom: insets.bottom + 16 }]}>
        <View style={styles.dots}>
          {SLIDES.map((_, i) => (
            <View
              key={i}
              style={[
                styles.dot,
                {
                  backgroundColor: i === index ? tokens.accent.primary : tokens.border.strong,
                  width: i === index ? 22 : 6,
                },
              ]}
            />
          ))}
        </View>
        <Pressable
          accessibilityRole="button"
          onPress={goNext}
          style={({ pressed }) => [
            styles.primary,
            { backgroundColor: tokens.accent.primary, opacity: pressed ? 0.85 : 1 },
          ]}>
          <Text style={[styles.primaryLabel, { color: tokens.accent.contrast }]}>
            {isLast ? 'Pair your Mac' : 'Continue'}
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

function SlideContent({ slide }: { slide: Slide }) {
  const tokens = useTokens();

  if (slide.rows) {
    return (
      <View style={styles.listSlide}>
        <Text style={[styles.title, { color: tokens.text.primary }]}>{slide.title}</Text>
        <View style={styles.rows}>
          {slide.rows.map((r) => (
            <View key={r.title} style={styles.row}>
              <View
                style={[
                  styles.rowIconWrap,
                  { backgroundColor: tokens.surface.secondary, borderColor: tokens.border.subtle },
                ]}>
                <Ionicons name={r.icon} size={22} color={tokens.accent.primary} />
              </View>
              <View style={styles.rowBody}>
                <Text style={[styles.rowTitle, { color: tokens.text.primary }]}>{r.title}</Text>
                <Text style={[styles.rowText, { color: tokens.text.muted }]}>{r.body}</Text>
              </View>
            </View>
          ))}
        </View>
      </View>
    );
  }

  return (
    <View style={styles.heroSlide}>
      {slide.badge === 'app-icon' ? (
        <Image source={require('@/../assets/images/icon.png')} style={styles.appIcon} contentFit="contain" />
      ) : (
        <View style={[styles.badge, { backgroundColor: tokens.accent.primary }]}>
          <Ionicons name={slide.badge} size={48} color={tokens.accent.contrast} />
        </View>
      )}
      <Text style={[styles.title, { color: tokens.text.primary }]}>{slide.title}</Text>
      <Text style={[styles.body, { color: tokens.text.muted }]}>{slide.body}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  topBar: { flexDirection: 'row', justifyContent: 'flex-end', paddingHorizontal: 16 },
  skipBtn: { paddingHorizontal: 12, paddingVertical: 8 },
  skipLabel: { fontSize: 15, fontWeight: '500' },
  page: { flex: 1, justifyContent: 'center', paddingHorizontal: 32 },
  heroSlide: { alignItems: 'center', gap: 18 },
  badge: { width: 96, height: 96, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  appIcon: { width: 120, height: 120, borderRadius: 28 },
  title: { fontSize: 28, fontWeight: '700', textAlign: 'center' },
  body: { fontSize: 16, lineHeight: 22, textAlign: 'center' },
  listSlide: { gap: 24 },
  rows: { gap: 18 },
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: 14 },
  rowIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowBody: { flex: 1, gap: 4 },
  rowTitle: { fontSize: 16, fontWeight: '600' },
  rowText: { fontSize: 14, lineHeight: 20 },
  footer: { paddingHorizontal: 24, gap: 20 },
  dots: { flexDirection: 'row', justifyContent: 'center', gap: 8 },
  dot: { height: 6, borderRadius: 3 },
  primary: { height: 52, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  primaryLabel: { fontSize: 16, fontWeight: '600' },
});
