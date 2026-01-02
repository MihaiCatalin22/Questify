import { defineConfig, type UserConfig } from "vite";
import react from "@vitejs/plugin-react";
import fs from "fs";
import path from "path";
import type { ServerOptions } from "https";

const certDir = path.resolve(__dirname, ".cert");
const keyPath = path.join(certDir, "localhost-key.pem");
const certPath = path.join(certDir, "localhost.pem");

const hasCerts = fs.existsSync(keyPath) && fs.existsSync(certPath);

const httpsOptions: ServerOptions | undefined = hasCerts
  ? {
      key: fs.readFileSync(keyPath),
      cert: fs.readFileSync(certPath),
    }
  : undefined;

export default defineConfig({
  plugins: [react()],
  server: {
    host: "localhost",
    port: 5173,
    https: httpsOptions,
    proxy: {
      "/api": {
        target: hasCerts ? "https://localhost:8080" : "http://localhost:8080",
        changeOrigin: true,
        secure: false,
        rewrite: (p) => p.replace(/^\/api/, ""),
      },
    },
  },
} satisfies UserConfig);
