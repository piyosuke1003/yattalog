const cacheName = 'yattalog-ui-1';
const assets = ['./','./index.html','./calendar.js','./manifest.webmanifest','./icon-192.png','./icon-512.png'];
const paths = new Set(assets.map(path => new URL(path, self.registration.scope).pathname));
self.addEventListener('install', e => e.waitUntil(caches.open(cacheName).then(cache => cache.addAll(assets)).then(()=>self.skipWaiting())));
self.addEventListener('activate', e => e.waitUntil(caches.keys().then(names=>Promise.all(names.filter(name=>name.startsWith('yattalog-ui-') && name!==cacheName).map(name=>caches.delete(name)))).then(()=>self.clients.claim())));
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if(e.request.method !== 'GET' || url.origin !== self.location.origin || !paths.has(url.pathname) || url.search) return;
  e.respondWith(fetch(e.request).catch(async () => (await caches.match(e.request)) || Response.error()));
});
