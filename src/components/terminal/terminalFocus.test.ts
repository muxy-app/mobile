import { scheduleTerminalInputFocus } from './terminalFocus';

describe('scheduleTerminalInputFocus', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('focuses terminal input after activation settles', () => {
    const input = { focus: jest.fn() };

    scheduleTerminalInputFocus(input);

    expect(input.focus).not.toHaveBeenCalled();
    jest.advanceTimersByTime(50);
    expect(input.focus).toHaveBeenCalledTimes(1);
  });

  it('cancels a pending activation focus', () => {
    const input = { focus: jest.fn() };

    const cancel = scheduleTerminalInputFocus(input);
    cancel();
    jest.advanceTimersByTime(50);

    expect(input.focus).not.toHaveBeenCalled();
  });
});
