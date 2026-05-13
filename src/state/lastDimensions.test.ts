import type * as LastDimensions from './lastDimensions';

function freshModule(): typeof LastDimensions {
  let mod!: typeof LastDimensions;
  jest.isolateModules(() => {
    mod = jest.requireActual<typeof LastDimensions>('./lastDimensions');
  });
  return mod;
}

describe('lastDimensions', () => {
  it('returns the default 80x24 before anything is recorded', () => {
    const { getLastDimensions } = freshModule();
    expect(getLastDimensions()).toEqual({ cols: 80, rows: 24 });
  });

  it('records and returns the most recent positive dimensions', () => {
    const { getLastDimensions, recordDimensions } = freshModule();
    recordDimensions(120, 40);
    expect(getLastDimensions()).toEqual({ cols: 120, rows: 40 });
    recordDimensions(200, 50);
    expect(getLastDimensions()).toEqual({ cols: 200, rows: 50 });
  });

  it('ignores non-positive dimensions', () => {
    const { getLastDimensions, recordDimensions } = freshModule();
    recordDimensions(120, 40);
    recordDimensions(0, 50);
    recordDimensions(100, 0);
    recordDimensions(-5, -5);
    expect(getLastDimensions()).toEqual({ cols: 120, rows: 40 });
  });
});
