/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  darkMode: ["selector", '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        // Apple-premium design tokens — see admin-web/README.md "Design
        // system" note. Every value here is a CSS variable set in index.css
        // under `:root` (dark, the default) and `:root[data-theme="light"]`
        // (the alternate, toggled from the sidebar), so the exact same
        // class names (bg-apple-surface, text-apple-secondary, etc.) render
        // correctly in both themes — components never branch on theme.
        apple: {
          bg: "rgb(var(--apple-bg) / <alpha-value>)",
          surface: "rgb(var(--apple-surface) / <alpha-value>)",
          surface2: "rgb(var(--apple-surface2) / <alpha-value>)",
          text: "rgb(var(--apple-text) / <alpha-value>)",
          secondary: "rgb(var(--apple-secondary) / <alpha-value>)",
          blue: "rgb(var(--apple-blue) / <alpha-value>)",
          "blue-dark": "rgb(var(--apple-blue-dark) / <alpha-value>)",
          green: "rgb(var(--apple-green) / <alpha-value>)",
          amber: "rgb(var(--apple-amber) / <alpha-value>)",
          red: "rgb(var(--apple-red) / <alpha-value>)",
          // Precomputed (not alpha-modifiable) — subtle fills/borders whose
          // "amount" of tint should flip between light and dark, not be
          // dialed from the class name.
          border: "var(--apple-border)",
          border2: "var(--apple-border2)",
          hover: "var(--apple-hover)",
          hover2: "var(--apple-hover2)",
          subtle: "var(--apple-subtle)",
        },
      },
      fontFamily: {
        sans: [
          "-apple-system",
          "BlinkMacSystemFont",
          "Inter",
          "Segoe UI",
          "sans-serif",
        ],
      },
      borderRadius: {
        xl2: "1.25rem",
      },
      boxShadow: {
        card: "0 1px 2px rgba(0,0,0,0.12), 0 1px 1px rgba(0,0,0,0.08)",
        "card-hover": "0 8px 24px rgba(0,0,0,0.24), 0 2px 6px rgba(0,0,0,0.12)",
      },
      backdropBlur: {
        xl2: "24px",
      },
    },
  },
  plugins: [],
};
