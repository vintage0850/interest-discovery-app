/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        background: '#FBF9F5',
        surface: '#FFFFFF',
        surfaceSecondary: '#F3EFEA',
        surfaceElevated: '#FFFFFF',
        accent: '#2D6A4F',
        accentDark: '#1B4332',
        accentSoft: '#E8F5E9',
        accentText: '#FFFFFF',
        textPrimary: '#1A1A1A',
        textSecondary: '#4A4A4A',
        textTertiary: '#767676',
        borderSubtle: '#E8E4DE',
        borderStrong: '#D3CEBE',
        badgeBg: '#EFECE6',
        badgeText: '#3D3A36',
      },
      fontFamily: {
        sans: ['-apple-system', 'BlinkMacSystemFont', '"Hiragino Sans"', '"Hiragino Kaku Gothic ProN"', 'Meiryo', 'sans-serif'],
      },
      borderRadius: {
        'card': '24px',
        'button': '16px',
        'insight': '20px',
        'selector': '12px',
        'badge': '8px',
      }
    },
  },
  plugins: [],
}
