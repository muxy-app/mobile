export class WSError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'WSError';
    this.code = code;
  }
}

export function isWSError(err: unknown): err is WSError {
  return err instanceof WSError;
}
