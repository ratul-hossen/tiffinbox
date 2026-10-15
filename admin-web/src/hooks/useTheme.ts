import { useEffect, useState } from "react";

export type Theme = "dark" | "light";

const STORAGE_KEY = "tiffinbox-admin-theme";

function readStored(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "light" || stored === "dark") return stored;
  } catch {
    // localStorage can throw in private-browsing/locked-down contexts -
    // fall through to the default below rather than crash the admin panel.
  }
  return "dark"; // matches the portfolio-admin reference this redesign follows
}

/** Applies/persists the admin panel's light/dark theme via `data-theme` on <html>. */
export function useTheme() {
  const [theme, setTheme] = useState<Theme>(readStored);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Ignore - theme just won't persist across reloads in this context.
    }
  }, [theme]);

  function toggleTheme() {
    setTheme((t) => (t === "dark" ? "light" : "dark"));
  }

  return { theme, toggleTheme };
}
