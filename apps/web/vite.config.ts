import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5178,
    proxy: {
      "/api": "http://localhost:8090",
      "/outputs": "http://localhost:8090"
    }
  }
});
