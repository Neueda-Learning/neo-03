import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server proxies API calls to the backend so the frontend uses same-origin
// paths (/api, /health, /info) in both `npm run dev` and the built nginx image.
const BACKEND = process.env.VITE_BACKEND_URL || 'http://localhost:8080';

// Where this app is served from. Empty (the default) means the site root, which is what
// `npm run dev` and a standalone `docker compose up` use. In the deployed stack every UI
// shares ONE port and is told apart by a path prefix, so the image is built with
// APP_BASE_PATH=/neo-03 and Vite writes /neo-03/assets/... into index.html.
// Asset URLs are baked at BUILD time and an ALB cannot rewrite paths, so this cannot be a
// runtime setting. It must match the slot's PathPrefix in infra/env/*.params.
const BASE = process.env.APP_BASE_PATH || '';

export default defineConfig({
  base: BASE ? `${BASE}/` : '/',
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: BACKEND, changeOrigin: true },
      '/health': { target: BACKEND, changeOrigin: true },
      '/info': { target: BACKEND, changeOrigin: true },
    },
  },
});
