import { CameraView, useCameraPermissions } from 'expo-camera';
import { Stack, useRouter } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { HeaderIconButton } from '@/components/HeaderIconButton';
import { parsePairUri } from '@/state';
import { useTokens } from '@/theme';

export default function ScanPairScreen() {
  const tokens = useTokens();
  const router = useRouter();
  const [permission, requestPermission] = useCameraPermissions();
  const [error, setError] = useState<string | null>(null);
  const handledRef = useRef(false);

  const onBarcodeScanned = useCallback(
    ({ data }: { data: string }) => {
      if (handledRef.current) return;
      const payload = parsePairUri(data);
      if (!payload) {
        setError('That QR code isn’t a Muxy pairing code.');
        return;
      }
      handledRef.current = true;
      router.replace({
        pathname: '/add-device',
        params: {
          host: payload.host,
          port: String(payload.port),
          auto: '1',
          ...(payload.serviceName ? { service: payload.serviceName } : {}),
          ...(payload.label ? { label: payload.label } : {}),
        },
      });
    },
    [router],
  );

  const headerOptions = {
    title: 'Scan pairing QR',
    headerLeft: () => (
      <HeaderIconButton icon="close" accessibilityLabel="Close" onPress={() => router.back()} />
    ),
  };

  if (!permission) {
    return (
      <View style={[styles.root, { backgroundColor: tokens.surface.primary }]}>
        <Stack.Screen options={headerOptions} />
      </View>
    );
  }

  if (!permission.granted) {
    return (
      <View style={[styles.root, styles.center, { backgroundColor: tokens.surface.primary }]}>
        <Stack.Screen options={headerOptions} />
        <Text style={[styles.message, { color: tokens.text.primary }]}>
          Muxy needs camera access to scan a pairing QR code.
        </Text>
        <Pressable
          accessibilityRole="button"
          onPress={requestPermission}
          style={({ pressed }) => [
            styles.cta,
            { backgroundColor: tokens.accent.primary, opacity: pressed ? 0.85 : 1 },
          ]}>
          <Text style={[styles.ctaLabel, { color: tokens.accent.contrast }]}>Allow camera</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View style={[styles.root, { backgroundColor: tokens.surface.primary }]}>
      <Stack.Screen options={headerOptions} />
      <CameraView
        style={StyleSheet.absoluteFill}
        facing="back"
        barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
        onBarcodeScanned={onBarcodeScanned}
      />
      <View style={styles.overlay} pointerEvents="none">
        <View style={[styles.reticle, { borderColor: tokens.accent.primary }]} />
      </View>
      <View style={styles.footer}>
        <Text style={[styles.hint, { color: tokens.text.primary }]}>
          Point your camera at the QR code shown in Muxy › Settings › Mobile.
        </Text>
        {error ? <Text style={[styles.error, { color: tokens.status.danger }]}>{error}</Text> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  center: { alignItems: 'center', justifyContent: 'center', padding: 24, gap: 16 },
  message: { fontSize: 16, textAlign: 'center' },
  cta: { paddingVertical: 14, paddingHorizontal: 24, borderRadius: 12 },
  ctaLabel: { fontSize: 16, fontWeight: '600' },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
  },
  reticle: {
    width: 240,
    height: 240,
    borderWidth: 2,
    borderRadius: 16,
  },
  footer: {
    position: 'absolute',
    left: 24,
    right: 24,
    bottom: 48,
    gap: 8,
  },
  hint: {
    fontSize: 14,
    textAlign: 'center',
    backgroundColor: 'rgba(0,0,0,0.5)',
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 10,
    overflow: 'hidden',
  },
  error: { fontSize: 14, textAlign: 'center' },
});
