import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import fs from "fs";
import path from "path";

const keyPath = path.resolve(__dirname, ".cert/localhost-key.pem");
const certPath = path.resolve(__dirname, ".cert/localhost.pem");

function httpsIfCertsExist() {
  if (fs.existsSync(keyPath) && fs.existsSync(certPath)) {
    return {
      key: fs.readFileSync(keyPath),
      cert: fs.readFileSync(certPath),
    };
  }
  return undefined;
}

export default defineConfig(({ command }) => ({
  plugins: [react()],
  server: {
    https: command === "serve" ? httpsIfCertsExist() : undefined,
    port: 5173,
    host: "localhost",
    proxy: {
      "/api": {
        target: "https://localhost:8080",
        changeOrigin: true,
        secure: true,
        rewrite: (p) => p.replace(/^\/api/, ""),
      },
    },
  },
}));
