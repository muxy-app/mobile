import { Ionicons } from '@expo/vector-icons';
import { FlashList, type ListRenderItemInfo } from '@shopify/flash-list';
import { useCallback, useMemo } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import { useTokens } from '@/theme';
import type { FileEntry, Project } from '@/transport';

import { breadcrumbsForPath, fileName } from './fileManager';
import type { FileManager } from './useFileManager';

type Props = {
  manager: FileManager;
  paths: string[];
  project: Project;
};

export function MoveDestinationScreen({ manager, paths, project }: Props) {
  const tokens = useTokens();
  const crumbs = useMemo(
    () => breadcrumbsForPath(manager.movePath, project.name),
    [manager.movePath, project.name],
  );
  const invalidDestination = paths.some(
    (path) => manager.movePath === path || manager.movePath.startsWith(`${path}/`),
  );
  const movableHere = !invalidDestination && !paths.every((path) => parentPath(path) === manager.movePath);

  const openFolder = useCallback(
    (path: string) => {
      if (paths.some((source) => path === source || path.startsWith(`${source}/`))) return;
      manager.goToMoveDirectory(path);
    },
    [manager, paths],
  );

  const renderItem = useCallback(
    ({ item }: ListRenderItemInfo<FileEntry>) => {
      const disabled = paths.some((source) => item.path === source || item.path.startsWith(`${source}/`));
      return (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`Folder ${item.name}`}
          disabled={disabled}
          onPress={() => openFolder(item.path)}
          style={({ pressed }) => [
            styles.row,
            {
              borderBottomColor: tokens.border.subtle,
              opacity: disabled ? 0.3 : pressed ? 0.65 : 1,
            },
          ]}>
          <View style={[styles.icon, { backgroundColor: tokens.surface.secondary }]}>
            <Ionicons name="folder" size={18} color={tokens.accent.primary} />
          </View>
          <Text style={[styles.name, { color: tokens.text.primary }]} numberOfLines={1}>
            {item.name}
          </Text>
          <Ionicons name="chevron-forward" size={18} color={tokens.text.muted} />
        </Pressable>
      );
    },
    [openFolder, paths, tokens],
  );

  return (
    <View style={styles.root}>
      <View style={[styles.summary, { borderBottomColor: tokens.border.subtle }]}>
        <Text style={[styles.summaryTitle, { color: tokens.text.primary }]}>
          Move {paths.length === 1 ? `“${fileName(paths[0]!)}”` : `${paths.length} items`}
        </Text>
        <Text style={[styles.summaryBody, { color: tokens.text.muted }]}>Choose a destination folder.</Text>
      </View>
      <View style={styles.breadcrumbs}>
        {crumbs.map((crumb, index) => (
          <View key={crumb.path || 'root'} style={styles.crumbGroup}>
            {index > 0 ? <Ionicons name="chevron-forward" size={13} color={tokens.text.muted} /> : null}
            <Pressable
              accessibilityRole="button"
              onPress={() => manager.goToMoveDirectory(crumb.path)}
              style={({ pressed }) => [
                styles.crumb,
                {
                  backgroundColor: index === crumbs.length - 1 ? tokens.surface.secondary : 'transparent',
                  opacity: pressed ? 0.6 : 1,
                },
              ]}>
              <Text
                numberOfLines={1}
                style={[
                  styles.crumbLabel,
                  { color: index === crumbs.length - 1 ? tokens.text.primary : tokens.text.muted },
                ]}>
                {crumb.label}
              </Text>
            </Pressable>
          </View>
        ))}
      </View>

      {manager.error ? (
        <Text style={[styles.error, { color: tokens.status.danger }]}>{manager.error}</Text>
      ) : null}

      <FlashList
        data={manager.moveEntries}
        keyExtractor={(entry) => entry.path}
        renderItem={renderItem}
        ListEmptyComponent={
          manager.moveLoading ? (
            <View style={styles.center}>
              <ActivityIndicator color={tokens.accent.primary} />
            </View>
          ) : (
            <View style={styles.center}>
              <Ionicons name="folder-open-outline" size={30} color={tokens.text.muted} />
              <Text style={[styles.empty, { color: tokens.text.muted }]}>No folders here</Text>
            </View>
          )
        }
        contentContainerStyle={styles.listContent}
      />

      <View
        style={[
          styles.footer,
          { backgroundColor: tokens.surface.secondary, borderTopColor: tokens.border.subtle },
        ]}>
        <View style={styles.destination}>
          <Text style={[styles.destinationLabel, { color: tokens.text.muted }]}>Destination</Text>
          <Text style={[styles.destinationPath, { color: tokens.text.primary }]} numberOfLines={1}>
            {manager.movePath || project.name}
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          disabled={!movableHere || manager.busy}
          onPress={manager.moveHere}
          style={({ pressed }) => [
            styles.moveButton,
            {
              backgroundColor: tokens.accent.primary,
              opacity: !movableHere || manager.busy ? 0.35 : pressed ? 0.8 : 1,
            },
          ]}>
          {manager.busy ? (
            <ActivityIndicator size="small" color={tokens.accent.contrast} />
          ) : (
            <Text style={[styles.moveLabel, { color: tokens.accent.contrast }]}>Move here</Text>
          )}
        </Pressable>
      </View>
    </View>
  );
}

function parentPath(path: string): string {
  const index = path.lastIndexOf('/');
  return index < 0 ? '' : path.slice(0, index);
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  summary: { gap: 4, paddingHorizontal: 15, paddingVertical: 12, borderBottomWidth: StyleSheet.hairlineWidth },
  summaryTitle: { fontSize: 14, fontWeight: '600' },
  summaryBody: { fontSize: 12 },
  breadcrumbs: { minHeight: 42, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10, overflow: 'hidden' },
  crumbGroup: { flexDirection: 'row', alignItems: 'center', flexShrink: 1 },
  crumb: { maxWidth: 150, paddingHorizontal: 7, paddingVertical: 5, borderRadius: 7, borderCurve: 'continuous' },
  crumbLabel: { fontSize: 12 },
  error: { paddingHorizontal: 15, paddingBottom: 8, fontSize: 12 },
  listContent: { paddingBottom: 80 },
  row: { minHeight: 56, flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 14, borderBottomWidth: StyleSheet.hairlineWidth },
  icon: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center', borderRadius: 9, borderCurve: 'continuous' },
  name: { flex: 1, fontSize: 14, fontWeight: '500' },
  center: { minHeight: 220, alignItems: 'center', justifyContent: 'center', gap: 8 },
  empty: { fontSize: 13 },
  footer: { position: 'absolute', left: 0, right: 0, bottom: 0, minHeight: 68, flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 14, paddingVertical: 9, borderTopWidth: StyleSheet.hairlineWidth },
  destination: { flex: 1, gap: 3 },
  destinationLabel: { fontSize: 10, textTransform: 'uppercase', letterSpacing: 0.5 },
  destinationPath: { fontSize: 12, fontWeight: '500' },
  moveButton: { minWidth: 96, height: 42, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 13, borderRadius: 11, borderCurve: 'continuous' },
  moveLabel: { fontSize: 13, fontWeight: '600' },
});
