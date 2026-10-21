import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Em desenvolvimento, o site chama /api/... e o Vite repassa para o servidor Java.
    // Assim o navegador acha que é tudo o mesmo endereço e não bloqueia (CORS).
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        rewrite: (caminho) => caminho.replace(/^\/api/, ""),
      },
    },
  },
});
