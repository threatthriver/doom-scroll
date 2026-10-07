import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { sendToToken } from "../src/fcm.ts";
import { Firestore } from "../src/firestore.ts";
import { TokenProvider } from "../src/googleAuth.ts";

async function serviceAccount() {
  const pair = (await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  )) as CryptoKeyPair;
  const der = Buffer.from(await crypto.subtle.exportKey("pkcs8", pair.privateKey)).toString("base64");
  const pem = `-----BEGIN PRIVATE KEY-----\n${der.match(/.{1,64}/g)!.join("\n")}\n-----END PRIVATE KEY-----\n`;
  return { sa: { client_email: "hush@p.iam.gserviceaccount.com", private_key: pem }, pub: pair.publicKey };
}

describe("TokenProvider", () => {
  it("signs a valid RS256 assertion, caches the token and shares concurrent mints", async () => {
    const { sa, pub } = await serviceAccount();
    let calls = 0;
    let assertion = "";
    const f = (async (_u: string, init: RequestInit) => {
      calls++;
      assertion = (init.body as URLSearchParams).get("assertion")!;
      return new Response(JSON.stringify({ access_token: "AT", expires_in: 3600 }));
    }) as unknown as typeof fetch;
    let now = 1_000;
    const tp = new TokenProvider(sa, f, () => now);

    const [a, b] = await Promise.all([tp.get(), tp.get()]);
    assert.equal(a, "AT");
    assert.equal(b, "AT");
    assert.equal(calls, 1);

    const [h, p, sig] = assertion.split(".");
    const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", pub, Buffer.from(sig, "base64url"), new TextEncoder().encode(`${h}.${p}`));
    assert.ok(ok, "assertion must verify with the matching public key");
    const claims = JSON.parse(Buffer.from(p, "base64url").toString());
    assert.equal(claims.iss, sa.client_email);
    assert.match(claims.scope, /firebase\.messaging/);
    assert.match(claims.scope, /datastore/);

    await tp.get();
    assert.equal(calls, 1, "cached");
    now += 3600;
    await tp.get();
    assert.equal(calls, 2, "expired tokens are re-minted");
  });

  it("fails loudly when the exchange is rejected", async () => {
    const { sa } = await serviceAccount();
    const tp = new TokenProvider(sa, (async () => new Response("no", { status: 400 })) as unknown as typeof fetch);
    await assert.rejects(tp.get(), /token exchange failed/);
  });
});

function provider(): TokenProvider {
  let n = 0;
  return { get: async () => `tok${++n}`, invalidate: () => {} } as unknown as TokenProvider;
}
const noWait = async () => {};
const res = (status: number, body: unknown = {}) => new Response(JSON.stringify(body), { status });

describe("sendToToken", () => {
  it("returns ok on success", async () => {
    const f = (async () => res(200, { name: "m" })) as unknown as typeof fetch;
    assert.equal(await sendToToken("p", provider(), "t", {}, "c", f, noWait), "ok");
  });
  it("retries transient errors then succeeds", async () => {
    const seq = [res(503), res(429), res(200)];
    const f = (async () => seq.shift()!) as unknown as typeof fetch;
    assert.equal(await sendToToken("p", provider(), "t", {}, "c", f, noWait), "ok");
  });
  it("gives up after 3 transient failures", async () => {
    let calls = 0;
    const f = (async () => (calls++, res(500))) as unknown as typeof fetch;
    assert.equal(await sendToToken("p", provider(), "t", {}, "c", f, noWait), "retry");
    assert.equal(calls, 3);
  });
  it("treats network errors as retryable", async () => {
    let calls = 0;
    const f = (async () => {
      if (calls++ < 2) throw new Error("net");
      return res(200);
    }) as unknown as typeof fetch;
    assert.equal(await sendToToken("p", provider(), "t", {}, "c", f, noWait), "ok");
  });
  it("reports dead tokens without retrying", async () => {
    let calls = 0;
    const f = (async () => (calls++, res(404, { error: { details: [{ errorCode: "UNREGISTERED" }] } }))) as unknown as typeof fetch;
    assert.equal(await sendToToken("p", provider(), "t", {}, "c", f, noWait), "unregistered");
    assert.equal(calls, 1);
  });
  it("refreshes the access token once on 401", async () => {
    const auths: string[] = [];
    const seq = [res(401), res(200)];
    const f = (async (_u: string, init: RequestInit) => {
      auths.push((init.headers as Record<string, string>).Authorization);
      return seq.shift()!;
    }) as unknown as typeof fetch;
    assert.equal(await sendToToken("p", provider(), "t", {}, "c", f, noWait), "ok");
    assert.deepEqual(auths, ["Bearer tok1", "Bearer tok2"]);
  });
  it("doesn't loop forever on persistent auth failure", async () => {
    let calls = 0;
    const f = (async () => (calls++, res(403))) as unknown as typeof fetch;
    assert.equal(await sendToToken("p", provider(), "t", {}, "c", f, noWait), "auth");
    assert.equal(calls, 2);
  });
});

describe("Firestore REST client", () => {
  const tp = { get: async () => "AT", invalidate: () => {} } as unknown as TokenProvider;
  it("returns null for a missing doc and decodes fields", async () => {
    const f = (async (u: string) =>
      u.endsWith("/missing") ? res(404) : res(200, { name: "x", fields: { a: { stringValue: "b" } } })) as unknown as typeof fetch;
    const fs = new Firestore("p", tp, f);
    assert.equal(await fs.get("chats/missing"), null);
    assert.deepEqual((await fs.get("chats/x"))!.fields, { a: { stringValue: "b" } });
  });
  it("lists token ids from document names, decoding escapes", async () => {
    const f = (async () =>
      res(200, { documents: [{ name: "projects/p/databases/(default)/documents/users/u/fcmTokens/abc%3Adef" }] })) as unknown as typeof fetch;
    assert.deepEqual(await new Firestore("p", tp, f).listIds("users/u/fcmTokens", 10), ["abc:def"]);
  });
  it("treats an empty or absent collection as no ids", async () => {
    assert.deepEqual(await new Firestore("p", tp, (async () => res(200, {})) as unknown as typeof fetch).listIds("x", 5), []);
  });
  it("retries once after a 401 with a fresh token", async () => {
    let n = 0;
    const f = (async () => (n++ === 0 ? res(401) : res(200, { name: "x", fields: {} }))) as unknown as typeof fetch;
    assert.ok(await new Firestore("p", tp, f).get("chats/x"));
    assert.equal(n, 2);
  });
  it("ignores 404 on delete but surfaces real errors", async () => {
    await new Firestore("p", tp, (async () => res(404)) as unknown as typeof fetch).delete("a/b");
    await assert.rejects(new Firestore("p", tp, (async () => res(500)) as unknown as typeof fetch).delete("a/b"));
  });
});

describe("default fetch binding", () => {
  it("works when the global fetch is only callable unbound (as on Cloudflare Workers)", async () => {
    const original = globalThis.fetch;
    // Mimics the Workers runtime: invoking fetch with any `this` other than the global throws.
    globalThis.fetch = function (this: unknown, ..._a: unknown[]) {
      if (this !== undefined && this !== globalThis) throw new TypeError("Illegal invocation");
      return Promise.resolve(new Response(JSON.stringify({ name: "x", fields: {} })));
    } as unknown as typeof fetch;
    try {
      const tp = { get: async () => "AT", invalidate: () => {} } as unknown as TokenProvider;
      assert.ok(await new Firestore("p", tp).get("chats/x"));
    } finally {
      globalThis.fetch = original;
    }
  });
});
