export type ThemeMode = 'light' | 'dark';

export type ThemeTokens = {
  mode: ThemeMode;
  surface: {
    primary: string;
    secondary: string;
    tertiary: string;
  };
  text: {
    primary: string;
    secondary: string;
    muted: string;
    inverse: string;
  };
  border: {
    subtle: string;
    strong: string;
  };
  accent: {
    primary: string;
    contrast: string;
  };
  status: {
    success: string;
    warning: string;
    danger: string;
  };
};

const darkTokens: ThemeTokens = {
  mode: 'dark',
  surface: {
    primary: '#0B0B0F',
    secondary: '#15151B',
    tertiary: '#1F1F27',
  },
  text: {
    primary: '#F5F5F7',
    secondary: '#C7C7D1',
    muted: '#7A7A85',
    inverse: '#0B0B0F',
  },
  border: {
    subtle: '#23232C',
    strong: '#34343F',
  },
  accent: {
    primary: '#A74BA7',
    contrast: '#FFFFFF',
  },
  status: {
    success: '#34D399',
    warning: '#FBBF24',
    danger: '#F87171',
  },
};

const lightTokens: ThemeTokens = {
  mode: 'light',
  surface: {
    primary: '#FFFFFF',
    secondary: '#F5F5F7',
    tertiary: '#EBEBF0',
  },
  text: {
    primary: '#0B0B0F',
    secondary: '#3A3A45',
    muted: '#7A7A85',
    inverse: '#FFFFFF',
  },
  border: {
    subtle: '#E5E5EA',
    strong: '#D1D1D6',
  },
  accent: {
    primary: '#A74BA7',
    contrast: '#FFFFFF',
  },
  status: {
    success: '#16A34A',
    warning: '#D97706',
    danger: '#DC2626',
  },
};

export const defaultTokens = { dark: darkTokens, light: lightTokens };
