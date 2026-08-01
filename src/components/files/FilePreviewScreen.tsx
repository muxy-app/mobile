import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { useTokens } from '@/theme';
import type { FileEntry, Project } from '@/transport';

import { fileName, formatFileSize, imageMimeType } from './fileManager';
import { NamePrompt } from './NamePrompt';
import type { FileManager } from './useFileManager';

const PREVIEW_CHARACTER_LIMIT = 200_000;
const MONO_FONT = Platform.select({ ios: 'Menlo', android: 'monospace', default: 'monospace' });

type Props = {
  entry: FileEntry;
  manager: FileManager;
  project: Project;
};

export function FilePreviewScreen({ entry, manager, project }: Props) {
  const tokens = useTokens();
  const [renameOpen, setRenameOpen] = useState(false);
  const content = manager.content;
  const size = manager.fileStat?.size ?? content?.size;

  const confirmDelete = () => {
    const isSSH = project.workspaceKind === 'ssh';
    Alert.alert(
      isSSH ? 'Delete permanently?' : 'Move to Trash?',
      isSSH
        ? `“${entry.name}” will be permanently deleted from the remote host.`
        : `“${entry.name}” will be moved to Trash on the Mac.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: isSSH ? 'Delete' : 'Move to Trash',
          style: 'destructive',
          onPress: () => manager.deletePaths([entry.path]),
        },
      ],
    );
  };

  if (manager.fileLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={tokens.accent.primary} />
        <Text style={[styles.hint, { color: tokens.text.muted }]}>Opening {entry.name}…</Text>
      </View>
    );
  }

  if (manager.error && !content && manager.previewKind !== 'unsupported') {
    return (
      <View style={styles.center}>
        <Ionicons name="alert-circle-outline" size={32} color={tokens.status.danger} />
        <Text style={[styles.errorTitle, { color: tokens.text.primary }]}>Couldn’t open this file</Text>
        <Text style={[styles.hint, { color: tokens.text.muted }]}>{manager.error}</Text>
        <Pressable
          accessibilityRole="button"
          onPress={manager.reloadOpenFile}
          style={({ pressed }) => [
            styles.retry,
            { backgroundColor: tokens.surface.secondary, opacity: pressed ? 0.7 : 1 },
          ]}>
          <Text style={[styles.retryLabel, { color: tokens.text.primary }]}>Try again</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      style={styles.root}
      behavior={Platform.OS === 'ios' && manager.editing ? 'padding' : undefined}>
      {manager.externalChanged ? (
        <View style={[styles.changeBanner, { backgroundColor: tokens.surface.tertiary }]}>
          <View style={styles.changeCopy}>
            <Ionicons name="alert-circle-outline" size={18} color={tokens.status.warning} />
            <Text style={[styles.changeText, { color: tokens.text.secondary }]}>
              This file changed on the Mac.
            </Text>
          </View>
          <View style={styles.changeActions}>
            <Pressable accessibilityRole="button" onPress={manager.reloadOpenFile}>
              <Text style={[styles.changeAction, { color: tokens.accent.primary }]}>Reload</Text>
            </Pressable>
            <Pressable accessibilityRole="button" onPress={manager.keepEditingAfterExternalChange}>
              <Text style={[styles.changeAction, { color: tokens.text.secondary }]}>Keep editing</Text>
            </Pressable>
          </View>
        </View>
      ) : null}
      {manager.error && content ? (
        <View style={[styles.errorBanner, { backgroundColor: tokens.surface.secondary }]}>
          <Ionicons name="alert-circle-outline" size={17} color={tokens.status.danger} />
          <Text style={[styles.errorBannerText, { color: tokens.status.danger }]} numberOfLines={2}>
            {manager.error}
          </Text>
        </View>
      ) : null}

      {manager.previewKind === 'image' && content ? (
        <View style={styles.imageRoot}>
          <Image
            source={{ uri: `data:${imageMimeType(entry.path)};base64,${content.content}` }}
            contentFit="contain"
            cachePolicy="none"
            recyclingKey={entry.path}
            style={styles.image}
          />
        </View>
      ) : manager.previewKind === 'unsupported' ? (
        <View style={styles.center}>
          <View style={[styles.binaryIcon, { backgroundColor: tokens.surface.secondary }]}>
            <Ionicons name="document-outline" size={34} color={tokens.text.muted} />
          </View>
          <Text style={[styles.errorTitle, { color: tokens.text.primary }]}>Preview unavailable</Text>
          <Text style={[styles.hint, { color: tokens.text.muted }]}>
            This file isn’t UTF-8 text or a supported image. You can still rename, move, or delete it.
          </Text>
        </View>
      ) : manager.editing && content ? (
        <TextInput
          key={`${entry.path}-${content.size}`}
          defaultValue={content.content}
          onChangeText={manager.updateDraft}
          multiline
          autoCapitalize="none"
          autoCorrect={false}
          textAlignVertical="top"
          accessibilityLabel={`Edit ${entry.name}`}
          style={[
            styles.editor,
            {
              color: tokens.text.primary,
              backgroundColor: tokens.surface.primary,
            },
          ]}
        />
      ) : content ? (
        <ScrollView
          style={styles.textPreview}
          contentContainerStyle={styles.textPreviewContent}
          contentInsetAdjustmentBehavior="automatic"
          showsHorizontalScrollIndicator>
          <Text selectable style={[styles.code, { color: tokens.text.primary }]}>
            {content.content.slice(0, PREVIEW_CHARACTER_LIMIT)}
          </Text>
          {content.content.length > PREVIEW_CHARACTER_LIMIT ? (
            <View style={[styles.truncated, { backgroundColor: tokens.surface.secondary }]}>
              <Text style={[styles.truncatedText, { color: tokens.text.muted }]}>
                Preview shortened for performance. Tap Edit to load the complete file in the editor.
              </Text>
            </View>
          ) : null}
        </ScrollView>
      ) : null}

      <View
        style={[
          styles.footer,
          { backgroundColor: tokens.surface.secondary, borderTopColor: tokens.border.subtle },
        ]}>
        <View style={styles.fileInfo}>
          <Text style={[styles.fileInfoName, { color: tokens.text.primary }]} numberOfLines={1}>
            {fileName(entry.path)}
          </Text>
          <Text style={[styles.fileInfoMeta, { color: tokens.text.muted }]}>
            {content?.encoding?.toUpperCase() ?? 'FILE'}{size === undefined ? '' : ` · ${formatFileSize(size)}`}
            {manager.dirty ? ' · Unsaved' : ''}
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Rename file"
          disabled={manager.busy || manager.editing}
          onPress={() => setRenameOpen(true)}
          style={({ pressed }) => [styles.footerAction, { opacity: manager.editing ? 0.35 : pressed ? 0.6 : 1 }]}>
          <Ionicons name="pencil-outline" size={19} color={tokens.text.secondary} />
        </Pressable>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={project.workspaceKind === 'ssh' ? 'Delete file' : 'Move file to Trash'}
          disabled={manager.busy}
          onPress={confirmDelete}
          style={({ pressed }) => [styles.footerAction, { opacity: pressed ? 0.6 : 1 }]}>
          <Ionicons name="trash-outline" size={19} color={tokens.status.danger} />
        </Pressable>
      </View>

      <NamePrompt
        visible={renameOpen}
        title="Rename file"
        actionLabel="Rename"
        initialValue={entry.name}
        onCancel={() => setRenameOpen(false)}
        onSubmit={(name) => {
          setRenameOpen(false);
          manager.rename(entry.path, name).then((renamed) => {
            if (renamed) manager.returnToBrowser();
          }).catch(() => {});
        }}
      />
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 9, padding: 30 },
  hint: { maxWidth: 310, fontSize: 13, lineHeight: 19, textAlign: 'center' },
  errorTitle: { fontSize: 16, fontWeight: '600' },
  retry: { marginTop: 4, paddingHorizontal: 14, paddingVertical: 9, borderRadius: 10, borderCurve: 'continuous' },
  retryLabel: { fontSize: 13, fontWeight: '600' },
  changeBanner: { paddingHorizontal: 12, paddingVertical: 9, gap: 8 },
  changeCopy: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  changeText: { flex: 1, fontSize: 12 },
  changeActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 18 },
  changeAction: { fontSize: 12, fontWeight: '600' },
  errorBanner: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 12, paddingVertical: 9 },
  errorBannerText: { flex: 1, fontSize: 12 },
  imageRoot: { flex: 1, padding: 14 },
  image: { flex: 1 },
  binaryIcon: { width: 68, height: 68, alignItems: 'center', justifyContent: 'center', borderRadius: 18, borderCurve: 'continuous' },
  textPreview: { flex: 1 },
  textPreviewContent: { padding: 15, paddingBottom: 32 },
  code: { fontFamily: MONO_FONT, fontSize: 12, lineHeight: 19 },
  truncated: { marginTop: 18, padding: 12, borderRadius: 10, borderCurve: 'continuous' },
  truncatedText: { fontSize: 12, lineHeight: 17 },
  editor: { flex: 1, padding: 15, fontFamily: MONO_FONT, fontSize: 13, lineHeight: 20 },
  footer: { minHeight: 62, flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 12, paddingVertical: 8, borderTopWidth: StyleSheet.hairlineWidth },
  fileInfo: { flex: 1, gap: 3 },
  fileInfoName: { fontSize: 12, fontWeight: '600' },
  fileInfoMeta: { fontSize: 10 },
  footerAction: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
});
