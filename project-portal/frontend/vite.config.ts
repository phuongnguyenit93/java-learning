import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  publicDir: '../src/main/resources/portal-data',
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
});
