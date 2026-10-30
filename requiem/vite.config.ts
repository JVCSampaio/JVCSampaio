import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath } from "node:url";

// Tauri expects a fixed port in development and serves the built files in production.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  clearScreen: false,
  server: { port: 1420, strictPort: true, watch: { ignored: ["**/src-tauri/**", "**/core/**", "**/target/**"] } },
  envPrefix: ["VITE_", "TAURI_ENV_"],
  build: { target: "es2021", sourcemap: false, chunkSizeWarningLimit: 900 },
});
