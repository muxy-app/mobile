const NAMED_COLORS: Record<string, string> = {
  red: '#E5484D',
  orange: '#F76B15',
  amber: '#F5A623',
  yellow: '#EBCB00',
  lime: '#9BCD1E',
  green: '#30A46C',
  teal: '#12A594',
  cyan: '#05A2C2',
  blue: '#3E63DD',
  indigo: '#5B5BD6',
  violet: '#8E4EC6',
  pink: '#D6409F',
};

export function resolveColor(value: string | undefined): string | null {
  if (!value) return null;
  const trimmed = value.trim();
  if (/^#[0-9a-fA-F]{3,8}$/.test(trimmed)) return trimmed;
  const named = NAMED_COLORS[trimmed.toLowerCase()];
  return named ?? null;
}
