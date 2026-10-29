// Exposes only the SePay webhook to a development tunnel. Never proxies admin, wallet or other APIs.
import http from 'node:http';
const port = Number(process.env.SEPAY_PROXY_PORT || 8092);
const path = '/api/v1/finance/webhooks/sepay';
const server = http.createServer((request, response) => {
  if (request.method === 'GET' && request.url === '/health') {
    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end('{"service":"cleanmaster-sepay-webhook"}'); return;
  }
  if (request.method !== 'POST' || request.url !== path) { response.writeHead(404); response.end(); return; }
  if (!String(request.headers['content-type'] || '').toLowerCase().startsWith('application/json')) { response.writeHead(415); response.end(); return; }
  let size = 0; const chunks = [];
  request.on('data', chunk => {
    size += chunk.length;
    if (size > 65536) { response.writeHead(413); response.end(); request.destroy(); return; }
    chunks.push(chunk);
  });
  request.on('end', () => {
    if (response.writableEnded) return;
    const payload = Buffer.concat(chunks);
    const upstream = http.request({ hostname: '127.0.0.1', port: 8084, path, method: 'POST', timeout: 12000,
      headers: { 'Content-Type': 'application/json', 'Content-Length': payload.length,
        'X-SePay-Timestamp': String(request.headers['x-sepay-timestamp'] || ''),
        'X-SePay-Signature': String(request.headers['x-sepay-signature'] || '') } }, result => {
      response.writeHead(result.statusCode || 502, { 'Content-Type': result.headers['content-type'] || 'application/json' });
      result.pipe(response);
    });
    upstream.on('timeout', () => upstream.destroy());
    upstream.on('error', () => { if (!response.headersSent) response.writeHead(502); response.end('{"success":false}'); });
    upstream.end(payload);
  });
});
server.headersTimeout = 10000;
server.requestTimeout = 15000;
server.listen(port, '127.0.0.1', () => console.log(`SePay webhook-only proxy listening on 127.0.0.1:${port}`));
