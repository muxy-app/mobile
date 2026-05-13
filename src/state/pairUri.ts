export type PairUriPayload = {
  host: string;
  port: number;
  serviceName?: string;
  label?: string;
};

const SCHEME = 'muxy:';
const HOST = '//pair';

export function parsePairUri(input: string): PairUriPayload | null {
  if (!input) return null;
  const trimmed = input.trim();
  if (!trimmed.toLowerCase().startsWith(`${SCHEME}${HOST}`)) return null;

  const query = trimmed.slice(`${SCHEME}${HOST}`.length).replace(/^\?/, '');
  if (!query) return null;

  const params = new URLSearchParams(query);
  const host = params.get('host')?.trim();
  const portRaw = params.get('port')?.trim();
  if (!host || !portRaw) return null;

  const port = Number(portRaw);
  if (!Number.isInteger(port) || port < 1 || port > 65_535) return null;

  const serviceName = params.get('service')?.trim() || undefined;
  const label = params.get('label')?.trim() || undefined;

  return { host, port, serviceName, label };
}
