/** Firebase ID token verification (RS256) using only WebCrypto, as Workers have no Node crypto. */

export class AuthError extends Error {}

export interface VerifiedToken {
  uid: string;
}

export type KeyLookup = (kid: string) => Promise<CryptoKey | null>;

const SKEW_SEC = 60;
const MAX_TOKEN_LEN = 4096;

function b64urlToBytes(s: string): Uint8Array {
  const pad = s.length % 4 === 0 ? "" : "=".repeat(4 - (s.length % 4));
  const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/") + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function parseJson(bytes: Uint8Array): Record<string, unknown> {
  const v = JSON.parse(new TextDecoder().decode(bytes));
  if (typeof v !== "object" || v === null || Array.isArray(v)) throw new AuthError("bad json");
  return v as Record<string, unknown>;
}

/**
 * Checks signature, algorithm, issuer, audience, expiry and subject the way Firebase documents it.
 * Throws AuthError on any failure, so callers can answer 401 without leaking the reason.
 */
export async function verifyFirebaseIdToken(
  token: string,
  projectId: string,
  lookupKey: KeyLookup,
  nowSec: number,
): Promise<VerifiedToken> {
  if (typeof token !== "string" || token.length === 0 || token.length > MAX_TOKEN_LEN) throw new AuthError("len");
  const parts = token.split(".");
  if (parts.length !== 3) throw new AuthError("shape");

  let header: Record<string, unknown>;
  let payload: Record<string, unknown>;
  try {
    header = parseJson(b64urlToBytes(parts[0]));
    payload = parseJson(b64urlToBytes(parts[1]));
  } catch {
    throw new AuthError("decode");
  }

  if (header.alg !== "RS256") throw new AuthError("alg");
  if (typeof header.kid !== "string" || header.kid.length === 0) throw new AuthError("kid");

  const key = await lookupKey(header.kid);
  if (!key) throw new AuthError("unknown key");

  const signed = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
  let sig: Uint8Array;
  try {
    sig = b64urlToBytes(parts[2]);
  } catch {
    throw new AuthError("sig decode");
  }
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, sig, signed);
  if (!ok) throw new AuthError("signature");

  const { aud, iss, sub, exp, iat, auth_time: authTime } = payload;
  if (aud !== projectId) throw new AuthError("aud");
  if (iss !== `https://securetoken.google.com/${projectId}`) throw new AuthError("iss");
  if (typeof sub !== "string" || sub.length === 0 || sub.length > 128) throw new AuthError("sub");
  if (typeof exp !== "number" || exp <= nowSec) throw new AuthError("expired");
  if (typeof iat !== "number" || iat > nowSec + SKEW_SEC) throw new AuthError("iat");
  if (typeof authTime === "number" && authTime > nowSec + SKEW_SEC) throw new AuthError("auth_time");
  return { uid: sub };
}

interface Jwk {
  kid: string;
  kty: string;
  n: string;
  e: string;
}

const JWK_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const KEY_TTL_MS = 60 * 60 * 1000;
const MIN_REFRESH_MS = 60 * 1000;

/** Caches Google's signing keys; refetches on an unknown kid, but at most once a minute. */
export class GoogleKeyStore {
  private keys = new Map<string, CryptoKey>();
  private fetchedAt = 0;

  constructor(
    private readonly fetchFn: typeof fetch = (...a) => fetch(...a),
    private readonly nowMs: () => number = Date.now,
  ) {}

  lookup: KeyLookup = async (kid) => {
    const now = this.nowMs();
    const stale = now - this.fetchedAt > KEY_TTL_MS;
    if (!stale && this.keys.has(kid)) return this.keys.get(kid)!;
    if (now - this.fetchedAt >= MIN_REFRESH_MS) await this.refresh(now);
    return this.keys.get(kid) ?? null;
  };

  private async refresh(now: number) {
    const res = await this.fetchFn(JWK_URL);
    if (!res.ok) throw new Error(`jwk fetch ${res.status}`);
    const body = (await res.json()) as { keys?: Jwk[] };
    const next = new Map<string, CryptoKey>();
    for (const k of body.keys ?? []) {
      if (k.kty !== "RSA" || !k.kid) continue;
      const key = await crypto.subtle.importKey(
        "jwk",
        { kty: "RSA", n: k.n, e: k.e, alg: "RS256", ext: true },
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
        false,
        ["verify"],
      );
      next.set(k.kid, key);
    }
    this.keys = next;
    this.fetchedAt = now;
  }
}
