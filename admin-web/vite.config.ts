import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          "vendor-react": ["react", "react-dom"],
          "vendor-firebase-app": ["firebase/app"],
          "vendor-firebase-auth": ["firebase/auth"],
          "vendor-firebase-firestore": ["firebase/firestore"],
          "vendor-icons": ["lucide-react"],
        },
      },
    },
  },
});
