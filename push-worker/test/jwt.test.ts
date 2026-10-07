import assert from "node:assert/strict";
import { before, describe, it } from "node:test";
import { AuthError, GoogleKeyStore, verifyFirebaseIdToken } from "../src/jwt.ts";

const PROJECT = "demo-proj";
const NOW = 1_800_000_000;

const enc = new TextEncoder();
const b64url = (b: ArrayBuffer | Uint8Array) =>
  Buffer.from(b instanceof Uint8Array ? b : new Uint8Array(b)).toString("base64url");

let pair: CryptoKeyPair;
let other: CryptoKeyPair;

async function keypair(): Promise<CryptoKeyPair> {
  return (await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  )) as CryptoKeyPair;
}

async function makeToken(
  signer: CryptoKey,
  claims: Record<string, unknown> = {},
  header: Record<string, unknown> = {},
): Promise<string> {
  const h = b64url(enc.encode(JSON.stringify({ alg: "RS256", kid: "k1", typ: "JWT", ...header })));
  const p = b64url(
    enc.encode(
      JSON.stringify({
        aud: PROJECT,
        iss: `https://securetoken.google.com/${PROJECT}`,
        sub: "user123",
        iat: NOW - 10,
        exp: NOW + 3000,
        auth_time: NOW - 10,
        ...claims,
      }),
    ),
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", signer, enc.encode(`${h}.${p}`));
  return `${h}.${p}.${b64url(sig)}`;
}

before(async () => {
  pair = await keypair();
  other = await keypair();
});

const lookup = async (kid: string) => (kid === "k1" ? pair.publicKey : null);
const verify = (t: string) => verifyFirebaseIdToken(t, PROJECT, lookup, NOW);

describe("verifyFirebaseIdToken", () => {
  it("accepts a valid token and returns the uid", async () => {
    assert.deepEqual(await verify(await makeToken(pair.privateKey)), { uid: "user123" });
  });
  it("rejects a token signed by a different key", async () => {
    await assert.rejects(verify(await makeToken(other.privateKey)), AuthError);
  });
  it("rejects a tampered payload", async () => {
    const t = await makeToken(pair.privateKey);
    const [h, , s] = t.split(".");
    const forged = b64url(enc.encode(JSON.stringify({ aud: PROJECT, iss: `https://securetoken.google.com/${PROJECT}`, sub: "victim", iat: NOW - 10, exp: NOW + 3000 })));
    await assert.rejects(verify(`${h}.${forged}.${s}`), AuthError);
  });
  it("rejects wrong audience, issuer, expired, future iat, empty sub", async () => {
    for (const claims of [
      { aud: "other-project" },
      { iss: "https://securetoken.google.com/other" },
      { exp: NOW - 1 },
      { iat: NOW + 3600 },
      { sub: "" },
      { sub: undefined },
    ]) {
      await assert.rejects(verify(await makeToken(pair.privateKey, claims)), AuthError, JSON.stringify(claims));
    }
  });
  it("rejects alg none / HS256 and missing or unknown kid", async () => {
    await assert.rejects(verify(await makeToken(pair.privateKey, {}, { alg: "none" })), AuthError);
    await assert.rejects(verify(await makeToken(pair.privateKey, {}, { alg: "HS256" })), AuthError);
    await assert.rejects(verify(await makeToken(pair.privateKey, {}, { kid: undefined })), AuthError);
    await assert.rejects(verify(await makeToken(pair.privateKey, {}, { kid: "nope" })), AuthError);
  });
  it("rejects garbage", async () => {
    for (const t of ["", "a.b", "a.b.c.d", "not a token", "x".repeat(5000)]) {
      await assert.rejects(verify(t), AuthError, t.slice(0, 20));
    }
  });
});

describe("GoogleKeyStore", () => {
  it("fetches once, caches, and refetches on an unknown kid (at most once a minute)", async () => {
    const jwk = (await crypto.subtle.exportKey("jwk", pair.publicKey)) as JsonWebKey;
    let fetches = 0;
    let now = 1_000_000;
    const fakeFetch = (async () => {
      fetches++;
      return new Response(JSON.stringify({ keys: [{ kid: "k1", kty: "RSA", n: jwk.n, e: jwk.e }] }));
    }) as unknown as typeof fetch;
    const store = new GoogleKeyStore(fakeFetch, () => now);

    assert.ok(await store.lookup("k1"));
    assert.ok(await store.lookup("k1"));
    assert.equal(fetches, 1);

    assert.equal(await store.lookup("unknown"), null); // too soon to refetch
    assert.equal(fetches, 1);
    now += 61_000;
    assert.equal(await store.lookup("unknown"), null);
    assert.equal(fetches, 2);
  });
});
