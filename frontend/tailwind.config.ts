import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        navy: { 950: "#0a1830", 900: "#0f2240", 800: "#1b3358", 700: "#2c4a78", 600: "#44618f" },
        brand: { 50: "#eff5ff", 100: "#dbe8fe", 500: "#3b78f0", 600: "#2563eb", 700: "#1d4fd0" },
        surface: "#f5f7fa",
      },
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "Segoe UI", "Roboto", "sans-serif"],
        mono: ["ui-monospace", "SFMono-Regular", "Menlo", "Consolas", "monospace"],
      },
      boxShadow: { card: "0 1px 2px rgba(15, 34, 64, 0.05)" },
    },
  },
  plugins: [],
};
export default config;
