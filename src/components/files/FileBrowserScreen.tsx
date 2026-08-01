import { Ionicons } from '@expo/vector-icons';
import { FlashList, type ListRenderItemInfo } from '@shopify/flash-list';
import { memo, useCallback, useDeferredValue, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Pressable,
  Platform,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { ProjectAvatar } from '@/components/ProjectAvatar';
import { useTokens } from '@/theme';
import type { FileEntry, Project } from '@/transport';

import { breadcrumbsForPath, fileName, fileTypeLabel } from './fileManager';
import { NamePrompt } from './NamePrompt';
import type { FileManager } from './useFileManager';

const MONO_FONT = Platform.select({ ios: 'Menlo', android: 'monospace', default: 'monospace' });

type Props = {
  manager: FileManager;
  project: Project;
  visible: boolean;
  bottomInset: number;
};

type PromptState =
  | { kind: 'file'; initialValue: string }
  | { kind: 'folder'; initialValue: string }
  | { kind: 'rename'; initialValue: string; path: string }
  | null;

export function FileBrowserScreen({ manager, project, visible, bottomInset }: Props) {
  const tokens = useTokens();
  const { entries, openEntry, toggleSelection } = manager;
  const [query, setQuery] = useState('');
  const deferredQuery = useDeferredValue(query.trim().toLocaleLowerCase());
  const [createMenuOpen, setCreateMenuOpen] = useState(false);
  const [prompt, setPrompt] = useState<PromptState>(null);
  const crumbs = useMemo(
    () => breadcrumbsForPath(manager.currentPath, project.name),
    [manager.currentPath, project.name],
  );
  const visibleEntries = useMemo(() => {
    if (!deferredQuery) return manager.entries;
    return manager.entries.filter((entry) => entry.name.toLocaleLowerCase().includes(deferredQuery));
  }, [deferredQuery, manager.entries]);

  useEffect(() => {
    setQuery('');
  }, [manager.currentPath, visible]);

  const handlePress = useCallback(
    (path: string) => {
      const entry = entries.find((candidate) => candidate.path === path);
      if (entry) openEntry(entry);
    },
    [entries, openEntry],
  );

  const handleLongPress = useCallback(
    (path: string) => {
      toggleSelection(path);
    },
    [toggleSelection],
  );

  const renderItem = useCallback(
    ({ item }: ListRenderItemInfo<FileEntry>) => (
      <FileRow
        path={item.path}
        name={item.name}
        isDirectory={item.isDirectory}
        isIgnored={item.isIgnored}
        selected={manager.selectedPaths.has(item.path)}
        selectionMode={manager.selectionMode}
        onPress={handlePress}
        onLongPress={handleLongPress}
      />
    ),
    [manager.selectedPaths, manager.selectionMode, handlePress, handleLongPress],
  );

  const selected = [...manager.selectedPaths];
  const selectedEntry = selected.length === 1
    ? manager.entries.find((entry) => entry.path === selected[0])
    : undefined;

  const confirmDelete = () => {
    if (selected.length === 0) return;
    const isSSH = project.workspaceKind === 'ssh';
    const title = isSSH ? 'Delete permanently?' : 'Move to Trash?';
    const detail = selected.length === 1
      ? `“${fileName(selected[0]!)}”`
      : `${selected.length} selected items`;
    const message = isSSH
      ? `${detail} will be permanently deleted from the remote host.`
      : `${detail} will be moved to Trash on the Mac.`;
    Alert.alert(title, message, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: isSSH ? 'Delete' : 'Move to Trash',
        style: 'destructive',
        onPress: () => manager.deletePaths(selected),
      },
    ]);
  };

  const submitPrompt = (name: string) => {
    const currentPrompt = prompt;
    setPrompt(null);
    if (!currentPrompt) return;
    if (currentPrompt.kind === 'file') {
      manager.createFile(name).catch(() => {});
      return;
    }
    if (currentPrompt.kind === 'folder') {
      manager.createFolder(name).catch(() => {});
      return;
    }
    manager.rename(currentPrompt.path, name).catch(() => {});
  };

  const promptTitle = prompt?.kind === 'file'
    ? 'New file'
    : prompt?.kind === 'folder'
      ? 'New folder'
      : 'Rename item';

  const listHeader = (
    <>
      <View style={styles.projectContext}>
        <ProjectAvatar
          projectId={project.id}
          name={project.name}
          icon={project.icon}
          iconColor={project.iconColor}
          hasCustomLogo={Boolean(project.logo)}
          size={30}
        />
        <View style={styles.projectText}>
          <Text style={[styles.projectName, { color: tokens.text.primary }]} numberOfLines={1}>
            {project.name}
          </Text>
          <Text style={[styles.projectPath, { color: tokens.text.muted }]} numberOfLines={1}>
            {project.workspaceKind === 'ssh' ? 'SSH' : 'Mac'} · {project.path}
          </Text>
        </View>
      </View>
      <View style={[styles.search, { backgroundColor: tokens.surface.secondary }]}>
        <Ionicons name="search" size={16} color={tokens.text.muted} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="Filter this folder"
          placeholderTextColor={tokens.text.muted}
          autoCapitalize="none"
          autoCorrect={false}
          accessibilityLabel="Filter this folder"
          style={[styles.searchInput, { color: tokens.text.primary }]}
        />
      </View>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.breadcrumbs}>
        {crumbs.map((crumb, index) => (
          <View key={crumb.path || 'root'} style={styles.crumbGroup}>
            {index > 0 ? (
              <Ionicons name="chevron-forward" size={13} color={tokens.text.muted} />
            ) : null}
            <Pressable
              accessibilityRole="button"
              onPress={() => manager.goToDirectory(crumb.path)}
              style={({ pressed }) => [
                styles.crumb,
                {
                  backgroundColor: index === crumbs.length - 1 ? tokens.surface.secondary : 'transparent',
                  opacity: pressed ? 0.6 : 1,
                },
              ]}>
              <Text
                style={[
                  styles.crumbLabel,
                  { color: index === crumbs.length - 1 ? tokens.text.primary : tokens.text.muted },
                ]}
                numberOfLines={1}>
                {crumb.label}
              </Text>
            </Pressable>
          </View>
        ))}
      </ScrollView>
      {manager.error ? (
        <View style={[styles.errorBanner, { backgroundColor: tokens.surface.secondary }]}>
          <Text style={[styles.errorText, { color: tokens.status.danger }]} numberOfLines={2}>
            {manager.error}
          </Text>
          <Pressable accessibilityRole="button" onPress={manager.refresh}>
            <Text style={[styles.retryLabel, { color: tokens.accent.primary }]}>Retry</Text>
          </Pressable>
        </View>
      ) : null}
    </>
  );

  return (
    <View style={styles.root}>
      <FlashList
        data={visibleEntries}
        keyExtractor={(entry) => entry.path}
        renderItem={renderItem}
        ListHeaderComponent={listHeader}
        ListEmptyComponent={
          manager.listLoading ? (
            <View style={styles.center}>
              <ActivityIndicator color={tokens.accent.primary} />
            </View>
          ) : (
            <View style={styles.center}>
              <Ionicons name="folder-open-outline" size={30} color={tokens.text.muted} />
              <Text style={[styles.emptyTitle, { color: tokens.text.primary }]}>
                {deferredQuery ? 'No matches' : 'This folder is empty'}
              </Text>
              <Text style={[styles.emptyBody, { color: tokens.text.muted }]}>
                {deferredQuery ? 'Try a different filter.' : 'Create a file or folder to get started.'}
              </Text>
            </View>
          )
        }
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl
            refreshing={manager.refreshing}
            onRefresh={manager.refresh}
            tintColor={tokens.text.muted}
            colors={[tokens.accent.primary]}
          />
        }
        keyboardShouldPersistTaps="handled"
      />

      {manager.selectionMode ? (
        <View
          style={[
            styles.selectionBar,
            {
              bottom: -bottomInset,
              minHeight: 74 + bottomInset,
              paddingBottom: bottomInset,
              backgroundColor: tokens.surface.secondary,
              borderTopColor: tokens.border.subtle,
            },
          ]}>
          <SelectionAction
            icon="folder-open-outline"
            label="Move"
            disabled={selected.length === 0 || manager.busy}
            onPress={() => manager.startMove(selected)}
          />
          <SelectionAction
            icon="pencil-outline"
            label="Rename"
            disabled={!selectedEntry || manager.busy}
            onPress={() => selectedEntry && setPrompt({
              kind: 'rename',
              initialValue: selectedEntry.name,
              path: selectedEntry.path,
            })}
          />
          <SelectionAction
            icon="trash-outline"
            label={project.workspaceKind === 'ssh' ? 'Delete' : 'Trash'}
            danger
            disabled={selected.length === 0 || manager.busy}
            onPress={confirmDelete}
          />
        </View>
      ) : (
        <>
          {createMenuOpen ? (
            <View
              style={[
                styles.createMenu,
                { backgroundColor: tokens.surface.tertiary, borderColor: tokens.border.strong },
              ]}>
              <CreateAction
                icon="document-outline"
                label="New file"
                onPress={() => {
                  setCreateMenuOpen(false);
                  setPrompt({ kind: 'file', initialValue: '' });
                }}
              />
              <CreateAction
                icon="folder-outline"
                label="New folder"
                onPress={() => {
                  setCreateMenuOpen(false);
                  setPrompt({ kind: 'folder', initialValue: '' });
                }}
              />
            </View>
          ) : null}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Create file or folder"
            disabled={manager.busy}
            onPress={() => setCreateMenuOpen((open) => !open)}
            style={({ pressed }) => [
              styles.createButton,
              {
                backgroundColor: tokens.accent.primary,
                opacity: manager.busy ? 0.45 : pressed ? 0.8 : 1,
              },
            ]}>
            {manager.busy ? (
              <ActivityIndicator color={tokens.accent.contrast} />
            ) : (
              <Ionicons
                name={createMenuOpen ? 'close' : 'add'}
                size={25}
                color={tokens.accent.contrast}
              />
            )}
          </Pressable>
        </>
      )}

      <NamePrompt
        visible={Boolean(prompt)}
        title={promptTitle}
        actionLabel={prompt?.kind === 'rename' ? 'Rename' : 'Create'}
        initialValue={prompt?.initialValue}
        onCancel={() => setPrompt(null)}
        onSubmit={submitPrompt}
      />
    </View>
  );
}

const FileRow = memo(function FileRow({
  path,
  name,
  isDirectory,
  isIgnored,
  selected,
  selectionMode,
  onPress,
  onLongPress,
}: {
  path: string;
  name: string;
  isDirectory: boolean;
  isIgnored: boolean;
  selected: boolean;
  selectionMode: boolean;
  onPress: (path: string) => void;
  onLongPress: (path: string) => void;
}) {
  const tokens = useTokens();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${isDirectory ? 'Folder' : 'File'} ${name}`}
      accessibilityState={{ selected }}
      onPress={() => onPress(path)}
      onLongPress={() => onLongPress(path)}
      style={({ pressed }) => [
        styles.row,
        {
          borderBottomColor: tokens.border.subtle,
          backgroundColor: selected ? tokens.surface.tertiary : 'transparent',
          opacity: pressed ? 0.7 : isIgnored ? 0.58 : 1,
        },
      ]}>
      {selectionMode ? (
        <Ionicons
          name={selected ? 'checkmark-circle' : 'ellipse-outline'}
          size={23}
          color={selected ? tokens.accent.primary : tokens.text.muted}
        />
      ) : (
        <View
          style={[
            styles.fileIcon,
            { backgroundColor: isDirectory ? tokens.surface.tertiary : tokens.surface.secondary },
          ]}>
          {isDirectory ? (
            <Ionicons name="folder" size={18} color={tokens.accent.primary} />
          ) : (
            <Text style={[styles.fileType, { color: tokens.text.secondary }]}>
              {fileTypeLabel(path)}
            </Text>
          )}
        </View>
      )}
      <View style={styles.rowBody}>
        <Text style={[styles.rowName, { color: isIgnored ? tokens.text.muted : tokens.text.primary }]} numberOfLines={1}>
          {name}
        </Text>
        <Text style={[styles.rowMeta, { color: tokens.text.muted }]} numberOfLines={1}>
          {isIgnored ? 'Ignored' : isDirectory ? 'Folder' : fileTypeLabel(path)}
        </Text>
      </View>
      {!selectionMode ? (
        <Ionicons name="chevron-forward" size={18} color={tokens.text.muted} />
      ) : null}
    </Pressable>
  );
});

function SelectionAction({
  icon,
  label,
  danger = false,
  disabled,
  onPress,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  danger?: boolean;
  disabled: boolean;
  onPress: () => void;
}) {
  const tokens = useTokens();
  const color = danger ? tokens.status.danger : tokens.text.secondary;
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [styles.selectionAction, { opacity: disabled ? 0.35 : pressed ? 0.6 : 1 }]}>
      <Ionicons name={icon} size={20} color={color} />
      <Text style={[styles.selectionLabel, { color }]}>{label}</Text>
    </Pressable>
  );
}

function CreateAction({
  icon,
  label,
  onPress,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  onPress: () => void;
}) {
  const tokens = useTokens();
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.createAction, { opacity: pressed ? 0.6 : 1 }]}>
      <Ionicons name={icon} size={19} color={tokens.text.secondary} />
      <Text style={[styles.createActionLabel, { color: tokens.text.primary }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  listContent: { paddingBottom: 92 },
  projectContext: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 14, paddingBottom: 10 },
  projectText: { flex: 1, gap: 2 },
  projectName: { fontSize: 14, fontWeight: '600' },
  projectPath: { fontSize: 11, fontFamily: MONO_FONT },
  search: { flexDirection: 'row', alignItems: 'center', gap: 8, height: 38, marginHorizontal: 14, paddingHorizontal: 11, borderRadius: 10, borderCurve: 'continuous' },
  searchInput: { flex: 1, height: 38, padding: 0, fontSize: 14 },
  breadcrumbs: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10, paddingVertical: 9 },
  crumbGroup: { flexDirection: 'row', alignItems: 'center', flexShrink: 1 },
  crumb: { maxWidth: 150, paddingHorizontal: 7, paddingVertical: 5, borderRadius: 7, borderCurve: 'continuous' },
  crumbLabel: { fontSize: 12 },
  errorBanner: { flexDirection: 'row', alignItems: 'center', gap: 10, marginHorizontal: 14, marginBottom: 6, padding: 10, borderRadius: 10, borderCurve: 'continuous' },
  errorText: { flex: 1, fontSize: 12 },
  retryLabel: { fontSize: 12, fontWeight: '600' },
  row: { minHeight: 58, flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 14, borderBottomWidth: StyleSheet.hairlineWidth },
  fileIcon: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center', borderRadius: 9, borderCurve: 'continuous' },
  fileType: { fontSize: 9, fontWeight: '700', fontFamily: MONO_FONT },
  rowBody: { flex: 1, gap: 3 },
  rowName: { fontSize: 14, fontWeight: '500' },
  rowMeta: { fontSize: 11 },
  center: { minHeight: 240, alignItems: 'center', justifyContent: 'center', gap: 8, padding: 28 },
  emptyTitle: { fontSize: 16, fontWeight: '600' },
  emptyBody: { fontSize: 13, textAlign: 'center' },
  createButton: { position: 'absolute', right: 18, bottom: 20, width: 52, height: 52, alignItems: 'center', justifyContent: 'center', borderRadius: 17, borderCurve: 'continuous', boxShadow: '0 10px 28px rgba(0,0,0,0.35)' },
  createMenu: { position: 'absolute', right: 18, bottom: 82, width: 180, padding: 6, borderWidth: StyleSheet.hairlineWidth, borderRadius: 14, borderCurve: 'continuous', boxShadow: '0 12px 36px rgba(0,0,0,0.4)' },
  createAction: { minHeight: 42, flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 10 },
  createActionLabel: { fontSize: 14, fontWeight: '500' },
  selectionBar: { position: 'absolute', left: 0, right: 0, bottom: 0, minHeight: 74, flexDirection: 'row', justifyContent: 'space-around', paddingTop: 8, borderTopWidth: StyleSheet.hairlineWidth },
  selectionAction: { minWidth: 72, alignItems: 'center', gap: 4, padding: 6 },
  selectionLabel: { fontSize: 11, fontWeight: '500' },
});
