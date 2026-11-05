import { defineConfig, type ViteDevServer } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import crypto from 'crypto';

// Firma determinística del workspace (sin exponer rutas absolutas ni secretos en HTTP)
const workspaceRoot = path.resolve(__dirname, '..');
export const WORKSPACE_SIGNATURE = crypto
  .createHash('sha256')
  .update(workspaceRoot.toLowerCase().replace(/\\/g, '/'))
  .digest('hex')
  .slice(0, 16);

function lysandriDevServerPlugin() {
  return {
    name: 'lysandri-dev-server-id',
    configureServer(server: ViteDevServer) {
      server.middlewares.use((req, res, next) => {
        res.setHeader('X-Lysandri-Dev-Server', 'lysandri-executive-dev');
        res.setHeader('X-Lysandri-Workspace-Sig', WORKSPACE_SIGNATURE);
        if (req.url === '/__lysandri_dev_id') {
          res.writeHead(200, {
            'Content-Type': 'application/json',
            'Cache-Control': 'no-store',
          });
          res.end(
            JSON.stringify({
              app: 'lysandri-executive',
              role: 'frontend-dev',
              workspaceSignature: WORKSPACE_SIGNATURE,
            })
          );
          return;
        }
        next();
      });
    },
  };
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), lysandriDevServerPlugin()],
  resolve: {
    preserveSymlinks: true,
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '127.0.0.1',
    port: 3000,
    strictPort: true,
    open: false,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      '/internal': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
});
