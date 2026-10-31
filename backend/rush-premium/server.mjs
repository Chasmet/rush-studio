import http from 'node:http';
import crypto from 'node:crypto';
import { Pool } from 'pg';

const PACKAGE = 'com.chk.rushstudio';
const PRODUCT = 'rush_premium';
const PORT = Number(process.env.PORT || 10000);
const BILLING_ENABLED = process.env.BILLING_ENABLED === 'true';
let serviceAccount;
try {
  serviceAccount = JSON.parse(process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || 'null');
} catch { serviceAccount = null; }
const configured = BILLING_ENABLED && serviceAccount?.client_email &&
  serviceAccount?.private_key && Boolean(process.env.DATABASE_URL);
const db = configured ? new Pool({ connectionString: process.env.DATABASE_URL,
  max: 4, connectionTimeoutMillis: 5000 }) : null;
let cachedToken = { value: '', expiresAt: 0 };
const limiter = new Map();

function json(res, status, data) {
  res.writeHead(status, { 'Content-Type':'application/json; charset=utf-8',
    'Cache-Control':'no-store', 'X-Content-Type-Options':'nosniff',
    'Access-Control-Allow-Origin':'null' });
  res.end(JSON.stringify(data));
}
function base64url(obj) { return Buffer.from(JSON.stringify(obj)).toString('base64url'); }
async function oauthToken() {
  if (cachedToken.value && Date.now() < cachedToken.expiresAt) return cachedToken.value;
  const now = Math.floor(Date.now() / 1000);
  const head = base64url({ alg:'RS256', typ:'JWT' });
  const payload = base64url({
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3500
  });
  const data = head + '.' + payload;
  const sig = crypto.createSign('RSA-SHA256').update(data).sign(serviceAccount.private_key).toString('base64url');
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'},
    body: new URLSearchParams({ grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion:data + '.' + sig }), signal: AbortSignal.timeout(12000)
  });
  if (!response.ok) throw new Error('OAuth Google indisponible');
  const result = await response.json();
  if (typeof result.access_token !== 'string') throw new Error('Jeton OAuth invalide');
  cachedToken = { value:result.access_token, expiresAt:Date.now()+Math.min(3200, Number(result.expires_in)||3200)*1000 };
  return cachedToken.value;
}
async function verifyWithGoogle(purchaseToken) {
  const bearer = await oauthToken();
  const path = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' +
    PACKAGE + '/purchases/productsv2/tokens/' + encodeURIComponent(purchaseToken);
  const response = await fetch(path, { headers:{ Authorization:'Bearer '+bearer },
    signal:AbortSignal.timeout(12000) });
  if (!response.ok) throw new Error('Vérification Google Play refusée: '+response.status);
  const result = await response.json();
  const purchased = result.purchaseStateContext?.purchaseState === 'PURCHASED';
  const correctProduct = Array.isArray(result.productLineItem) &&
    result.productLineItem.some(item => item.productId === PRODUCT);
  return purchased && correctProduct;
}
async function readBody(req) {
  if (!req.headers['content-type']?.startsWith('application/json')) throw new Error('Content-Type');
  let size = 0, chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 16384) throw new Error('Taille maximale dépassée');
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}
function limit(req) {
  const ip = req.headers['x-forwarded-for']?.toString().split(',')[0] ||
    req.socket.remoteAddress || 'unknown';
  const now = Date.now(), key = crypto.createHash('sha256').update(ip).digest('hex');
  const item = limiter.get(key);
  if (!item || item.until < now) { limiter.set(key, { until:now+60000,count:1 }); return false; }
  item.count++;
  if (limiter.size > 2500) for (const [k,v] of limiter) if (v.until < now) limiter.delete(k);
  return item.count > 20;
}
const server = http.createServer(async (req,res) => {
  try {
    if (req.method === 'GET' && req.url === '/health') {
      // Éviter de permettre le paiement si les secrets sont invalides.
      if (!configured) return json(res,200,{ok:true,billingReady:false});
      try {
        await oauthToken();
        await db.query('select 1');
        return json(res,200,{ok:true,billingReady:true});
      } catch { return json(res,200,{ok:true,billingReady:false}); }
    }
    if (req.method !== 'POST' || req.url !== '/verify')
      return json(res,404,{verified:false,error:'not_found'});
    if (limit(req)) return json(res,429,{verified:false,error:'rate_limited'});
    if (!configured) return json(res,503,{verified:false,error:'billing_not_configured'});
    const {packageName,productId,purchaseToken} = await readBody(req);
    if (packageName !== PACKAGE || productId !== PRODUCT ||
      typeof purchaseToken !== 'string' || purchaseToken.length < 20 ||
      purchaseToken.length > 10000 || !/^[A-Za-z0-9._:=-]+$/.test(purchaseToken))
      return json(res,400,{verified:false,error:'invalid_purchase'});
    const verified = await verifyWithGoogle(purchaseToken);
    if (!verified) return json(res,403,{verified:false,error:'not_purchased'});
    const fingerprint = crypto.createHash('sha256').update(purchaseToken,'utf8').digest('hex');
    await db.query(
      "insert into rush_premium_purchases(purchase_token_sha256,product_id) values($1,$2) "+
      "on conflict(purchase_token_sha256) do update set last_verified_at=now()",
      [fingerprint,PRODUCT]);
    return json(res,200,{verified:true,packageName:PACKAGE,productId:PRODUCT});
  } catch(e) {
    // Ne jamais mettre les tokens d'achats ou les credentials dans les logs.
    console.error('rush verify failed', String(e?.message||'error').slice(0,90));
    return json(res,503,{verified:false,error:'verification_unavailable'});
  }
});
server.listen(PORT,'0.0.0.0',()=>console.log('Rush Premium verifier listening'));
