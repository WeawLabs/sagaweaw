/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        canvas:  'rgb(var(--canvas)  / <alpha-value>)',
        surface: 'rgb(var(--surface) / <alpha-value>)',
        accent:  'rgb(var(--accent)  / <alpha-value>)',
        ink:     'rgb(var(--ink)     / <alpha-value>)',
        border:  'rgb(var(--border)  / <alpha-value>)',
        muted:   'rgb(var(--muted)   / <alpha-value>)',
        ok:      'rgb(var(--ok)      / <alpha-value>)',
        warn:    'rgb(var(--warn)    / <alpha-value>)',
        fail:    'rgb(var(--fail)    / <alpha-value>)',
        sidebar: 'rgb(var(--sidebar) / <alpha-value>)',
      },
      fontFamily: {
        sans: ['system-ui', 'sans-serif'],
      },
      maxWidth: {
        '8xl': '88rem',
      },
      keyframes: {
        'dropdown-in': {
          from: { opacity: '0', transform: 'translateY(-4px) scale(0.98)' },
          to:   { opacity: '1', transform: 'translateY(0)   scale(1)' },
        },
      },
      animation: {
        'in': 'dropdown-in 0.15s ease-out',
      },
    },
  },
  plugins: [],
}
