import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import fs from "fs";
import path from "path";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    https: {
      key: fs.readFileSync(path.resolve(__dirname, ".cert/localhost-key.pem")),
      cert: fs.readFileSync(path.resolve(__dirname, ".cert/localhost.pem")),
    },
    port: 5173,
    host: "localhost",
    proxy: {
      '/api': {
        target: 'https://localhost:8080',
        changeOrigin: true,
        secure: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
      }
    }
  },
});
