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
const DEFAULT_LR_PORT = Number(process.env.LIVERELOAD_PORT) || 35729;
const https = require('https');
const fs = require('fs');

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

// Attempt to start livereload; if the port is in use or errors, disable gracefully
let lrEnabled = false;
let LR_PORT = DEFAULT_LR_PORT;
if (process.env.LIVERELOAD !== '0') {
  try {
    const lrServer = livereload.createServer({
      exts: ['html', 'css', 'js'],
      delay: 100,
      port: LR_PORT,
    });
    lrServer.server?.on('error', (err) => {
      if (err && err.code === 'EADDRINUSE') {
        console.warn(`[livereload] Port ${LR_PORT} in use. Live-reload disabled.`);
        try { lrServer.close(); } catch (_) { /* ignore */ }
      } else {
        console.warn('[livereload] error:', err?.message || err);
        try { lrServer.close(); } catch (_) { /* ignore */ }
      }
    });
    lrServer.watch(staticDir);
    lrEnabled = true;
    console.log(`[livereload] watching ${staticDir} on port ${LR_PORT}`);
  } catch (e) {
    if (e && e.code === 'EADDRINUSE') {
      console.warn(`[livereload] Port ${LR_PORT} in use. Live-reload disabled.`);
    } else {
      console.warn('livereload/connect-livereload not installed or failed to start. Run: npm i -D livereload connect-livereload');
    }
  }
}

// Inject livereload script into served HTML only if enabled
if (lrEnabled) {
  app.use(connectLivereload({ port: LR_PORT }));
}

// Serve OpenLayers JS through same-origin to avoid browser tracking prevention on CDNs
app.get('/vendor/ol.js', (req, res) => {
  res.set('Content-Type', 'application/javascript');
  // Prefer local node_modules if available to avoid CDN blocks
  try {
    const localPath = require.resolve('ol/ol.js');
    return fs.createReadStream(localPath)
      .on('error', () => streamFromCdn(res))
      .pipe(res);
  } catch (_) {
    // Fallback to CDN through server
    return streamFromCdn(res);
  }
});

function streamFromCdn(res){
  const cdn = process.env.OL_CDN || 'https://cdn.jsdelivr.net/npm/ol@9.1.0/dist/ol.js';
  try {
    https.get(cdn, (r) => {
      if ((r.statusCode || 500) >= 400) {
        res.status(r.statusCode || 502);
        return res.end('// Failed to fetch OpenLayers from CDN');
      }
      r.on('error', () => {
        try { res.status(502).end('// Error streaming OpenLayers'); } catch(_) {}
      });
      r.pipe(res);
    }).on('error', () => {
      res.status(502).end('// Error loading OpenLayers');
    });
  } catch(_){
    try { res.status(502).end('// Error loading OpenLayers'); } catch(__) {}
  }
}

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
    // Also proxy auth endpoints for login/validate/logout
    app.use('/auth', createProxyMiddleware({
      target: process.env.BACKEND_URL || 'http://localhost:8080',
      changeOrigin: true,
      xfwd: true,
      logLevel: 'warn'
    }));
    console.log('[proxy] /auth ->', process.env.BACKEND_URL || 'http://localhost:8080');

    // Proxy additional backend roots used by the app
    const extraProxies = ['/products', '/categories', '/users', '/drones'];
    extraProxies.forEach(p => {
      app.use(p, createProxyMiddleware({
        target: process.env.BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true,
        xfwd: true,
        logLevel: 'warn'
      }));
      console.log(`[proxy] ${p} ->`, process.env.BACKEND_URL || 'http://localhost:8080');
    });
  } catch (e) {
    console.warn('http-proxy-middleware not installed. Run: npm i -D http-proxy-middleware');
  }
}

// SPA fallback: any unknown path returns index.html
app.get('*', (req, res) => {
  res.sendFile(path.join(staticDir, 'index.html'));
});

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
