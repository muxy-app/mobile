import { useCallback } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import type { ProjectWorkspace } from '@/state/projectWorkspaces';
import { useTokens } from '@/theme';

type Props = {
  workspaces: ProjectWorkspace[];
  selectedWorkspaceID: string | null;
  onSelect: (workspaceID: string | null) => void;
};

type ChipProps = {
  id: string | null;
  title: string;
  selected: boolean;
  onSelect: (workspaceID: string | null) => void;
};

export function WorkspaceFilterBar({ workspaces, selectedWorkspaceID, onSelect }: Props) {
  const tokens = useTokens();

  return (
    <View style={{ backgroundColor: tokens.surface.primary }}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.content}>
        <WorkspaceFilterChip
          id={null}
          title="All"
          selected={selectedWorkspaceID === null}
          onSelect={onSelect}
        />
        {workspaces.map((workspace) => (
          <WorkspaceFilterChip
            key={workspace.id}
            id={workspace.id}
            title={workspace.name}
            selected={selectedWorkspaceID === workspace.id}
            onSelect={onSelect}
          />
        ))}
      </ScrollView>
    </View>
  );
}

function WorkspaceFilterChip({ id, title, selected, onSelect }: ChipProps) {
  const tokens = useTokens();
  const handlePress = useCallback(() => onSelect(id), [id, onSelect]);

  return (
    <Pressable
      onPress={handlePress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      style={({ pressed }) => [
        styles.chip,
        {
          backgroundColor: selected ? tokens.accent.primary : tokens.surface.secondary,
          opacity: pressed ? 0.8 : 1,
        },
      ]}>
      <Text
        numberOfLines={1}
        style={[
          styles.label,
          { color: selected ? tokens.accent.contrast : tokens.text.primary },
        ]}>
        {title}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  content: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    gap: 8,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 999,
    borderCurve: 'continuous',
  },
  label: {
    fontSize: 14,
    fontWeight: '500',
  },
});
