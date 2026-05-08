import { Ionicons } from '@expo/vector-icons';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { ProjectAvatar } from '@/components/ProjectAvatar';
import { useTokens } from '@/theme';
import type { Project } from '@/transport';

type Props = {
  project: Project;
  onPress: () => void;
};

export function ProjectRow({ project, onPress }: Props) {
  const tokens = useTokens();
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.row,
        {
          backgroundColor: tokens.surface.secondary,
          borderColor: tokens.border.subtle,
          opacity: pressed ? 0.85 : 1,
        },
      ]}>
      <ProjectAvatar
        projectId={project.id}
        name={project.name}
        icon={project.icon}
        iconColor={project.iconColor}
        hasCustomLogo={Boolean(project.logo)}
      />
      <View style={styles.body}>
        <Text style={[styles.name, { color: tokens.text.primary }]} numberOfLines={1}>
          {project.name}
        </Text>
        <Text style={[styles.path, { color: tokens.text.muted }]} numberOfLines={1}>
          {project.path}
        </Text>
      </View>
      <Ionicons name="chevron-forward" size={18} color={tokens.text.muted} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
  },
  body: { flex: 1, gap: 2 },
  name: { fontSize: 16, fontWeight: '600' },
  path: { fontSize: 13 },
});
