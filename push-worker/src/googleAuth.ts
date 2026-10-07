/** Service-account OAuth for Google APIs (Firestore + FCM), using only WebCrypto. */

export interface ServiceAccount {
  client_email: string;
  private_key: string;
  token_uri?: string;
}

const SCOPES = [
  "https://www.googleapis.com/auth/firebase.messaging",
  "https://www.googleapis.com/auth/datastore",
].join(" ");
const DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
/** Refresh a little early so a token never expires mid-request. */
const EXPIRY_MARGIN_SEC = 120;

function b64url(bytes: ArrayBuffer | Uint8Array): string {
  const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = "";
  for (const b of arr) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): Uint8Array {
  const body = pem.replace(/-----[A-Z ]+-----/g, "").replace(/\s+/g, "");
  const bin = atob(body);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function parseServiceAccount(raw: string | undefined): ServiceAccount {
  if (!raw) throw new Error("SERVICE_ACCOUNT secret is not set");
  const sa = JSON.parse(raw) as Partial<ServiceAccount>;
  if (!sa.client_email || !sa.private_key) throw new Error("SERVICE_ACCOUNT is missing client_email/private_key");
  return sa as ServiceAccount;
}

/** Hands out a cached access token, minting a new one only when needed. */
export class TokenProvider {
  private token: string | null = null;
  private expiresAtSec = 0;
  private signingKey: CryptoKey | null = null;
  private inflight: Promise<string> | null = null;

  constructor(
    private readonly sa: ServiceAccount,
    private readonly fetchFn: typeof fetch = (...a) => fetch(...a),
    private readonly nowSec: () => number = () => Math.floor(Date.now() / 1000),
  ) {}

  async get(): Promise<string> {
    if (this.token && this.nowSec() < this.expiresAtSec - EXPIRY_MARGIN_SEC) return this.token;
    // Concurrent callers share one exchange instead of signing N JWTs.
    this.inflight ??= this.mint().finally(() => {
      this.inflight = null;
    });
    return this.inflight;
  }

  /** Call after a 401 so the next get() mints a fresh token. */
  invalidate() {
    this.token = null;
    this.expiresAtSec = 0;
  }

  private async key(): Promise<CryptoKey> {
    this.signingKey ??= await crypto.subtle.importKey(
      "pkcs8",
      pemToDer(this.sa.private_key),
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["sign"],
    );
    return this.signingKey;
  }

  private async mint(): Promise<string> {
    const uri = this.sa.token_uri ?? DEFAULT_TOKEN_URI;
    const now = this.nowSec();
    const enc = new TextEncoder();
    const header = b64url(enc.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
    const claims = b64url(
      enc.encode(JSON.stringify({ iss: this.sa.client_email, scope: SCOPES, aud: uri, iat: now, exp: now + 3600 })),
    );
    const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", await this.key(), enc.encode(`${header}.${claims}`));
    const assertion = `${header}.${claims}.${b64url(sig)}`;

    const res = await this.fetchFn(uri, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion,
      }),
    });
    if (!res.ok) throw new Error(`token exchange failed: ${res.status}`);
    const json = (await res.json()) as { access_token?: string; expires_in?: number };
    if (!json.access_token) throw new Error("token exchange returned no token");
    this.token = json.access_token;
    this.expiresAtSec = now + (json.expires_in ?? 3600);
    return this.token;
  }
}
