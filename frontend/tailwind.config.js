/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        brand: {
          dark: '#07090e',
          slate: '#0b101d',
          surface: '#111827',
          card: '#161f30',
          border: '#1f2d45',
          accent: '#06b6d4',
          indigo: '#6366f1',
          gold: '#f59e0b'
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace']
      },
      boxShadow: {
        'glow-cyan': '0 0 20px -3px rgba(6, 182, 212, 0.25)',
        'glow-indigo': '0 0 25px -3px rgba(99, 102, 241, 0.25)',
        'executive': '0 10px 30px -5px rgba(0, 0, 0, 0.5)',
      }
    },
  },
  plugins: [],
}
