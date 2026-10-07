import { sendToToken } from "./fcm.ts";
import { Firestore } from "./firestore.ts";
import { TokenProvider, parseServiceAccount } from "./googleAuth.ts";
import { handle, type Deps } from "./handler.ts";
import { GoogleKeyStore, verifyFirebaseIdToken } from "./jwt.ts";
import { RateLimiter, RecentSet } from "./logic.ts";

export interface Env {
  /** JSON key of the hush-push service account (secret). */
  SERVICE_ACCOUNT: string;
  FIREBASE_PROJECT_ID: string;
}

// Module-level state lives as long as the isolate does, which avoids re-fetching Google's signing
// keys and re-minting the OAuth token on every request (the free plan allows ~10 ms CPU each).
const keyStore = new GoogleKeyStore();
const limiter = new RateLimiter(60, 60_000); // 60 pushes/min per sender
const handled = new RecentSet(5 * 60_000);
let tokenProvider: TokenProvider | null = null;

function depsFor(env: Env): Deps {
  tokenProvider ??= new TokenProvider(parseServiceAccount(env.SERVICE_ACCOUNT));
  const tokens = tokenProvider;
  const projectId = env.FIREBASE_PROJECT_ID;
  return {
    verifyToken: (t) => verifyFirebaseIdToken(t, projectId, keyStore.lookup, Math.floor(Date.now() / 1000)),
    firestore: new Firestore(projectId, tokens),
    send: (t, data, chatId) => sendToToken(projectId, tokens, t, data, chatId),
    now: Date.now,
    limiter,
    handled,
    log: (e) => console.log(JSON.stringify(e)),
  };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (new URL(request.url).pathname === "/healthz") {
      return new Response(JSON.stringify({ ok: true }), { headers: { "Content-Type": "application/json" } });
    }
    try {
      return await handle(request, depsFor(env));
    } catch (e) {
      console.error(JSON.stringify({ evt: "unhandled", name: e instanceof Error ? e.name : "unknown" }));
      return new Response(JSON.stringify({ error: "server_error" }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }
  },
};
