import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHmac, timingSafeEqual } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const env = Object.fromEntries(fs.readFileSync(path.join(root, '.env'), 'utf8').split(/\r?\n/)
  .filter(line => /^[A-Z][A-Z0-9_]*=/.test(line)).map(line => {
    const index = line.indexOf('='); return [line.slice(0, index), line.slice(index + 1).trim().replace(/^(['"])(.*)\1$/, '$2')];
  }));
const normalize = value => String(value ?? '').normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toUpperCase().replace(/\s+/g, ' ').trim();
try {
  const response = await fetch('https://api-merchant.payos.vn/v1/payouts-account/balance', {
    headers: { 'x-client-id': env.PAYOS_PAYOUT_CLIENT_ID, 'x-api-key': env.PAYOS_PAYOUT_API_KEY }, signal: AbortSignal.timeout(15000),
  });
  const body = await response.json();
  if (!response.ok || body.code !== '00' || !body.data) {
    console.log(JSON.stringify({ verified: false, httpStatus: response.status, providerCode: body.code })); process.exitCode = 1;
  } else {
    const canonical = Object.keys(body.data).sort().map(key => `${encodeURIComponent(key)}=${encodeURIComponent(body.data[key] ?? '')}`).join('&');
    const expected = createHmac('sha256', env.PAYOS_PAYOUT_CHECKSUM_KEY).update(canonical).digest();
    const supplied = response.headers.get('x-signature') ?? '';
    const signatureValid = /^[\da-f]{64}$/i.test(supplied) && timingSafeEqual(Buffer.from(supplied, 'hex'), expected);
    const sourceNumberMatches = body.data.accountNumber === env.PAYOS_PAYOUT_SOURCE_ACCOUNT;
    const sourceNameMatches = normalize(body.data.accountName) === normalize(env.PAYOS_PAYOUT_SOURCE_NAME);
    const verified = signatureValid && sourceNumberMatches && sourceNameMatches && body.data.currency === 'VND';
    console.log(JSON.stringify({ verified, signatureValid, sourceNumberMatches, sourceNameMatches, currency: body.data.currency, balance: verified ? body.data.balance : undefined }));
    if (!verified) process.exitCode = 1;
  }
} catch { console.log(JSON.stringify({ verified: false, error: 'Connection failed; no payout was created.' })); process.exitCode = 1; }
