/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
  content: ["./index.html","./src/**/*.{ts,tsx,js,jsx}"],
  theme: {
    extend: {
      colors: {
        surface: { 50:"#fcfcfd",100:"#f7f7f8",200:"#f2f3f5" },
        ink: { 400:"#6b7280", 500:"#4b5563", 700:"#1f2937" },
        brand: { 50:"#eef2ff", 100:"#e0e7ff", 400:"#6366f1", 500:"#4f46e5", 600:"#4338ca" }, // indigo accent
        success: { 500:"#10b981" },
        warn: { 500:"#f59e0b" },
        danger: { 500:"#ef4444" },
      },
      boxShadow: {
        card: "0 6px 18px rgba(0,0,0,.06)",
        subtle: "0 1px 2px rgba(0,0,0,.06)",
      },
      borderRadius: { xl2: "1.25rem" }
    }
  },
  plugins: [],
};
