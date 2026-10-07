/** Minimal Firestore REST client: just the reads and the one delete the relay needs. */

import type { TokenProvider } from "./googleAuth.ts";

type FsValue = {
  stringValue?: string;
  booleanValue?: boolean;
  timestampValue?: string;
  arrayValue?: { values?: FsValue[] };
  mapValue?: { fields?: Record<string, FsValue> };
};
export type FsFields = Record<string, FsValue>;

export interface FsDoc {
  name: string;
  fields: FsFields;
}

export const str = (f: FsFields, k: string): string | undefined => f[k]?.stringValue;

export const strArray = (f: FsFields, k: string): string[] =>
  (f[k]?.arrayValue?.values ?? []).map((v) => v.stringValue).filter((s): s is string => typeof s === "string");

export const strMap = (f: FsFields, k: string): Record<string, string> => {
  const out: Record<string, string> = {};
  for (const [key, v] of Object.entries(f[k]?.mapValue?.fields ?? {})) {
    if (typeof v.stringValue === "string") out[key] = v.stringValue;
  }
  return out;
};

export const boolMap = (f: FsFields, k: string): Record<string, boolean> => {
  const out: Record<string, boolean> = {};
  for (const [key, v] of Object.entries(f[k]?.mapValue?.fields ?? {})) {
    if (typeof v.booleanValue === "boolean") out[key] = v.booleanValue;
  }
  return out;
};

export const timestampMs = (f: FsFields, k: string): number | undefined => {
  const t = f[k]?.timestampValue;
  if (!t) return undefined;
  const ms = Date.parse(t);
  return Number.isNaN(ms) ? undefined : ms;
};

export class FirestoreError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export class Firestore {
  private readonly base: string;

  constructor(
    projectId: string,
    private readonly tokens: TokenProvider,
    private readonly fetchFn: typeof fetch = (...a) => fetch(...a),
  ) {
    this.base = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents`;
  }

  private async call(method: string, path: string, query = ""): Promise<Response> {
    const doFetch = async () =>
      this.fetchFn(`${this.base}/${path}${query}`, {
        method,
        headers: { Authorization: `Bearer ${await this.tokens.get()}` },
      });
    let res = await doFetch();
    if (res.status === 401) {
      this.tokens.invalidate();
      res = await doFetch();
    }
    return res;
  }

  /** Returns null when the document doesn't exist. */
  async get(path: string): Promise<FsDoc | null> {
    const res = await this.call("GET", path);
    if (res.status === 404) return null;
    if (!res.ok) throw new FirestoreError(res.status, `get failed`);
    const d = (await res.json()) as { name: string; fields?: FsFields };
    return { name: d.name, fields: d.fields ?? {} };
  }

  /** Document ids (last path segment) under a collection, up to `limit`. */
  async listIds(collectionPath: string, limit: number): Promise<string[]> {
    const res = await this.call("GET", collectionPath, `?pageSize=${limit}&mask.fieldPaths=platform`);
    if (res.status === 404) return [];
    if (!res.ok) throw new FirestoreError(res.status, `list failed`);
    const body = (await res.json()) as { documents?: Array<{ name: string }> };
    return (body.documents ?? []).map((d) => decodeURIComponent(d.name.slice(d.name.lastIndexOf("/") + 1)));
  }

  async delete(path: string): Promise<void> {
    const res = await this.call("DELETE", path);
    if (!res.ok && res.status !== 404) throw new FirestoreError(res.status, `delete failed`);
  }
}
