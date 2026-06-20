/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/renderer/src/**/*.{js,ts,jsx,tsx,html}'],
  theme: {
    extend: {
      colors: {
        charcoal: '#1A1A2E',
        slate: '#16213E',
        teal: '#00D4AA',
        coral: '#E94560',
        surface: '#0F0F23',
        'surface-light': '#1E1E3A',
      },
      fontFamily: {
        mono: ['JetBrains Mono', 'monospace'],
        sans: ['Plus Jakarta Sans', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
