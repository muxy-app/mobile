import { parsePairUri } from './pairUri';

describe('parsePairUri', () => {
  it('parses a minimal valid URI', () => {
    expect(parsePairUri('muxy://pair?host=example.local&port=4865')).toEqual({
      host: 'example.local',
      port: 4865,
      serviceName: undefined,
      label: undefined,
    });
  });

  it('parses a URI with service and label', () => {
    const uri = 'muxy://pair?host=10.0.0.5&port=4865&service=Saeeds-Mac&label=Saeed%27s%20Mac';
    expect(parsePairUri(uri)).toEqual({
      host: '10.0.0.5',
      port: 4865,
      serviceName: 'Saeeds-Mac',
      label: "Saeed's Mac",
    });
  });

  it('accepts uppercase scheme', () => {
    expect(parsePairUri('MUXY://pair?host=h&port=1')).not.toBeNull();
  });

  it('trims surrounding whitespace', () => {
    expect(parsePairUri('  muxy://pair?host=h&port=1  ')).toEqual({
      host: 'h',
      port: 1,
      serviceName: undefined,
      label: undefined,
    });
  });

  it('rejects empty input', () => {
    expect(parsePairUri('')).toBeNull();
  });

  it('rejects wrong scheme', () => {
    expect(parsePairUri('https://pair?host=h&port=1')).toBeNull();
  });

  it('rejects missing host', () => {
    expect(parsePairUri('muxy://pair?port=4865')).toBeNull();
  });

  it('rejects missing port', () => {
    expect(parsePairUri('muxy://pair?host=h')).toBeNull();
  });

  it('rejects empty query', () => {
    expect(parsePairUri('muxy://pair')).toBeNull();
  });

  it('rejects non-integer port', () => {
    expect(parsePairUri('muxy://pair?host=h&port=abc')).toBeNull();
    expect(parsePairUri('muxy://pair?host=h&port=12.5')).toBeNull();
  });

  it('rejects port out of range', () => {
    expect(parsePairUri('muxy://pair?host=h&port=0')).toBeNull();
    expect(parsePairUri('muxy://pair?host=h&port=65536')).toBeNull();
    expect(parsePairUri('muxy://pair?host=h&port=-1')).toBeNull();
  });

  it('drops empty optional params', () => {
    const out = parsePairUri('muxy://pair?host=h&port=1&service=&label=');
    expect(out?.serviceName).toBeUndefined();
    expect(out?.label).toBeUndefined();
  });
});
