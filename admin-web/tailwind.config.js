/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        // Apple-premium design tokens. Use these instead of hardcoding hex
        // values inline — see admin-web/README.md "Design system" note.
        apple: {
          bg: "#f5f5f7", // page background
          surface: "#ffffff", // card/surface background
          text: "#1d1d1f", // primary text
          secondary: "#6e6e73", // secondary/muted text
          blue: "#0071e3", // primary accent (buttons, links, active states)
          "blue-dark": "#0058b0", // hover/active shade of the accent
          green: "#34c759", // success / verified / available / delivered
          amber: "#ff9500", // warning / in-progress
          red: "#ff3b30", // danger / cancelled / destructive
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
        card: "0 1px 2px rgba(0,0,0,0.04), 0 1px 1px rgba(0,0,0,0.03)",
        "card-hover": "0 8px 24px rgba(0,0,0,0.08), 0 2px 6px rgba(0,0,0,0.05)",
      },
      backdropBlur: {
        xl2: "24px",
      },
    },
  },
  plugins: [],
};
