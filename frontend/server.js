// Dev static server for SPA with live-reload and optional API proxy
// - Serves index.html at '/'
// - Injects livereload script and auto-reloads on HTML/CSS/JS changes
// - Disables cache to ensure fresh assets during development
// - Proxies '/api' to backend by default (set DEV_PROXY=0 to disable)

const express = require('express');
const path = require('path');
const compression = require('compression');
const livereload = require('livereload');
const connectLivereload = require('connect-livereload');

const app = express();
const PORT = process.env.PORT || 3000;

// Enable gzip
app.use(compression());

// Static files from current folder (frontend/)
const staticDir = __dirname; // this file sits in frontend/

// Disable caching during dev so edits show up immediately
app.use((req, res, next) => {
  res.set('Cache-Control', 'no-store, no-cache, must-revalidate, proxy-revalidate');
  res.set('Pragma', 'no-cache');
  res.set('Expires', '0');
  res.set('Surrogate-Control', 'no-store');
  next();
});

// Inject livereload script into served HTML
app.use(connectLivereload());

app.use(express.static(staticDir, { index: 'index.html', extensions: ['html'] }));

// Optional API proxy (enabled by DEV_PROXY=1) to avoid CORS issues
const enableProxy = process.env.DEV_PROXY !== '0'; // default ON, set DEV_PROXY=0 to disable
if (enableProxy) {
  try {
    const { createProxyMiddleware } = require('http-proxy-middleware');
    app.use('/api', createProxyMiddleware({
      target: process.env.BACKEND_URL || 'http://localhost:8080',
      changeOrigin: true,
      xfwd: true,
      logLevel: 'warn'
    }));
    console.log('[proxy] /api ->', process.env.BACKEND_URL || 'http://localhost:8080');
  } catch (e) {
    console.warn('http-proxy-middleware not installed. Run: npm i -D http-proxy-middleware');
  }
}

// SPA fallback: any unknown path returns index.html
app.get('*', (req, res) => {
  res.sendFile(path.join(staticDir, 'index.html'));
});

// Start livereload server watching the frontend directory
try {
  const lrServer = livereload.createServer({
    exts: ['html', 'css', 'js'],
    delay: 100,
  });
  lrServer.watch(staticDir);
  console.log('[livereload] watching', staticDir);
} catch (e) {
  console.warn('livereload/connect-livereload not installed. Run: npm i -D livereload connect-livereload');
}

// Start server with port fallback if in use
function start(port, attempts = 5) {
  const server = app.listen(port, () => {
    console.log(`Frontend is running at http://localhost:${port}`);
    console.log(`Proxy: ${enableProxy ? 'ON' : 'OFF'} | Backend: ${process.env.BACKEND_URL || 'http://localhost:8080'}`);
  });
  server.on('error', (err) => {
    if (err && err.code === 'EADDRINUSE' && attempts > 0) {
      const next = port + 1;
      console.warn(`[server] Port ${port} in use, trying ${next}...`);
      setTimeout(() => start(next, attempts - 1), 150);
    } else {
      console.error('[server] Failed to start:', err);
      process.exit(1);
    }
  });
}

start(Number(PORT) || 3000);
