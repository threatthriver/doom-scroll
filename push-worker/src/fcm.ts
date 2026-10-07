/** FCM HTTP v1 sender for the relay. */

import { buildFcmMessage, classifyFcmResponse, type FcmOutcome } from "./logic.ts";
import type { TokenProvider } from "./googleAuth.ts";

const MAX_ATTEMPTS = 3;
const BACKOFF_MS = [0, 300, 900];

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

/**
 * Sends one data message. Retries transient failures (5xx/429) with a short backoff and refreshes
 * the access token once on 401/403. Returns the final outcome for the caller to act on.
 */
export async function sendToToken(
  projectId: string,
  tokens: TokenProvider,
  deviceToken: string,
  data: Record<string, string>,
  chatId: string,
  fetchFn: typeof fetch = (...a) => fetch(...a),
  wait: (ms: number) => Promise<void> = sleep,
): Promise<FcmOutcome> {
  const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
  const body = JSON.stringify(buildFcmMessage(deviceToken, data, chatId));
  let refreshedAuth = false;
  let outcome: FcmOutcome = "fatal";

  for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
    if (BACKOFF_MS[attempt]) await wait(BACKOFF_MS[attempt]);
    let res: Response;
    try {
      res = await fetchFn(url, {
        method: "POST",
        headers: { Authorization: `Bearer ${await tokens.get()}`, "Content-Type": "application/json" },
        body,
      });
    } catch {
      outcome = "retry"; // network error
      continue;
    }
    const json = await res.json().catch(() => null);
    outcome = classifyFcmResponse(res.status, json);
    if (outcome === "auth" && !refreshedAuth) {
      refreshedAuth = true;
      tokens.invalidate();
      attempt--; // the token refresh doesn't count against the retry budget
      continue;
    }
    if (outcome !== "retry") return outcome;
  }
  return outcome;
}
