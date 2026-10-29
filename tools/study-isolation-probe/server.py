from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
class Handler(BaseHTTPRequestHandler):
 def do_GET(self):
  if self.path.endswith('/sw.js'):
   body=b'''self.addEventListener('install',()=>self.skipWaiting());self.addEventListener('activate',e=>e.waitUntil(self.clients.claim()));self.addEventListener('fetch',e=>e.respondWith(fetch(e.request).then(r=>{const h=new Headers(r.headers);h.set('Cross-Origin-Opener-Policy','same-origin');h.set('Cross-Origin-Embedder-Policy','require-corp');return new Response(r.body,{status:r.status,statusText:r.statusText,headers:h});})));'''; mime='text/javascript'
  else:
   body=Path(__file__).with_name('probe.html').read_bytes();mime='text/html'
  self.send_response(200);self.send_header('Content-Type',mime)
  if self.path.startswith('/headers/'):
   self.send_header('Cross-Origin-Opener-Policy','same-origin');self.send_header('Cross-Origin-Embedder-Policy','require-corp')
  self.end_headers();self.wfile.write(body)
HTTPServer(('127.0.0.1',4178),Handler).serve_forever()
