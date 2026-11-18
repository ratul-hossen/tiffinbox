/**
 * A small Firestore REST client for Workers (firebase-admin needs Node APIs
 * Workers don't have). Authenticates as the service account with a signed
 * JWT, or talks to the local emulator in tests.
 *
 * IMPORTANT: a service account bypasses firestore.rules. Every caller must do
 * its own checks - above all, take the user's uid from the OAuth token, never
 * from tool input.
 */
import type { Env } from "./env";

type FsValue = Record<string, unknown>;
export interface FsDoc {
  id: string;
  path: string;
  updateTime?: string;
  data: Record<string, unknown>;
}

export class FirestoreError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

// ---------- auth ----------

let cachedToken: { token: string; expiresAt: number } | null = null;

function b64url(bytes: ArrayBuffer | Uint8Array | string): string {
  const arr = typeof bytes === "string" ? new TextEncoder().encode(bytes) : new Uint8Array(bytes);
  let s = "";
  for (const b of arr) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function accessToken(env: Env): Promise<string> {
  if (env.FIRESTORE_EMULATOR_HOST) return "owner";
  if (cachedToken && cachedToken.expiresAt - 60_000 > Date.now()) return cachedToken.token;
  if (!env.FIREBASE_SERVICE_ACCOUNT) throw new Error("FIREBASE_SERVICE_ACCOUNT secret is not set");
  const sa = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT) as { client_email: string; private_key: string };
  const now = Math.floor(Date.now() / 1000);
  const unsigned =
    b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) +
    "." +
    b64url(
      JSON.stringify({
        iss: sa.client_email,
        scope: "https://www.googleapis.com/auth/datastore",
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
      }),
    );
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${unsigned}.${b64url(sig)}`,
    }),
  });
  if (!res.ok) throw new FirestoreError(res.status, "Could not get a Google access token");
  const json = (await res.json()) as { access_token: string; expires_in: number };
  cachedToken = { token: json.access_token, expiresAt: Date.now() + json.expires_in * 1000 };
  return json.access_token;
}

// ---------- value encoding ----------

export function decodeValue(v: FsValue): unknown {
  if ("nullValue" in v) return null;
  if ("booleanValue" in v) return v.booleanValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return Number(v.doubleValue);
  if ("stringValue" in v) return v.stringValue;
  if ("timestampValue" in v) return Date.parse(String(v.timestampValue));
  if ("arrayValue" in v) {
    const values = ((v.arrayValue as { values?: FsValue[] }).values ?? []) as FsValue[];
    return values.map(decodeValue);
  }
  if ("mapValue" in v) return decodeFields(((v.mapValue as { fields?: Record<string, FsValue> }).fields ?? {}));
  if ("geoPointValue" in v) return v.geoPointValue;
  if ("referenceValue" in v) return v.referenceValue;
  return undefined;
}

function decodeFields(fields: Record<string, FsValue>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(fields)) out[k] = decodeValue(v);
  return out;
}

export function encodeValue(v: unknown): FsValue {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === "string") return { stringValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(encodeValue) } };
  if (typeof v === "object") return { mapValue: { fields: encodeFields(v as Record<string, unknown>) } };
  throw new Error(`Cannot encode ${typeof v}`);
}

function encodeFields(o: Record<string, unknown>): Record<string, FsValue> {
  const out: Record<string, FsValue> = {};
  for (const [k, v] of Object.entries(o)) out[k] = encodeValue(v);
  return out;
}

function toDoc(raw: { name: string; fields?: Record<string, FsValue>; updateTime?: string }): FsDoc {
  const path = raw.name.split("/documents/")[1] ?? "";
  return { id: path.split("/").pop() ?? "", path, updateTime: raw.updateTime, data: decodeFields(raw.fields ?? {}) };
}

// ---------- client ----------

export type Filter = { field: string; op: "EQUAL" | "GREATER_THAN" | "LESS_THAN"; value: unknown };

/**
 * One client per incoming request. [reads] counts the documents this request
 * read, so the server can charge them against the daily read budget.
 */
export class Firestore {
  reads = 0;
  constructor(private env: Env) {}

  private get base(): string {
    const host = this.env.FIRESTORE_EMULATOR_HOST
      ? `http://${this.env.FIRESTORE_EMULATOR_HOST}`
      : "https://firestore.googleapis.com";
    return `${host}/v1/projects/${this.env.FIREBASE_PROJECT_ID}/databases/(default)/documents`;
  }

  private async call(url: string, init: RequestInit = {}): Promise<Response> {
    const token = await accessToken(this.env);
    const res = await fetch(url, {
      ...init,
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json", ...(init.headers ?? {}) },
      signal: AbortSignal.timeout(10_000),
    });
    return res;
  }

  /** One document, or null. [fields] limits what is downloaded (e.g. skip photos). */
  async get(path: string, fields?: string[]): Promise<FsDoc | null> {
    const qs = fields?.map((f) => `mask.fieldPaths=${encodeURIComponent(f)}`).join("&");
    const res = await this.call(`${this.base}/${path}${qs ? `?${qs}` : ""}`);
    this.reads += 1;
    if (res.status === 404) return null;
    if (!res.ok) throw new FirestoreError(res.status, `get ${path} failed`);
    return toDoc(await res.json());
  }

  /** A query over one collection (optionally under [parentPath]). */
  async query(
    collection: string,
    opts: { filters?: Filter[]; select?: string[]; limit?: number; parentPath?: string } = {},
  ): Promise<FsDoc[]> {
    const structuredQuery: Record<string, unknown> = { from: [{ collectionId: collection }] };
    if (opts.select) structuredQuery.select = { fields: opts.select.map((f) => ({ fieldPath: f })) };
    if (opts.filters?.length) {
      const fieldFilters = opts.filters.map((f) => ({
        fieldFilter: { field: { fieldPath: f.field }, op: f.op, value: encodeValue(f.value) },
      }));
      structuredQuery.where =
        fieldFilters.length === 1 ? fieldFilters[0] : { compositeFilter: { op: "AND", filters: fieldFilters } };
    }
    if (opts.limit) structuredQuery.limit = opts.limit;
    const parent = opts.parentPath ? `${this.base}/${opts.parentPath}` : this.base;
    const res = await this.call(`${parent}:runQuery`, { method: "POST", body: JSON.stringify({ structuredQuery }) });
    if (!res.ok) throw new FirestoreError(res.status, `query ${collection} failed`);
    const rows = (await res.json()) as Array<{ document?: { name: string; fields?: Record<string, FsValue> } }>;
    const docs = rows.filter((r) => r.document).map((r) => toDoc(r.document!));
    // Firestore bills an empty result as one read.
    this.reads += Math.max(1, docs.length);
    return docs;
  }

  /** Creates a document; fails (409) if it already exists. */
  async create(collectionPath: string, id: string, data: Record<string, unknown>): Promise<void> {
    const res = await this.call(`${this.base}/${collectionPath}?documentId=${encodeURIComponent(id)}`, {
      method: "POST",
      body: JSON.stringify({ fields: encodeFields(data) }),
    });
    if (!res.ok) throw new FirestoreError(res.status, `create ${collectionPath}/${id} failed`);
  }

  /** Sets only [data]'s fields on an existing document. */
  async update(path: string, data: Record<string, unknown>): Promise<void> {
    const mask = Object.keys(data).map((f) => `updateMask.fieldPaths=${encodeURIComponent(f)}`).join("&");
    const res = await this.call(`${this.base}/${path}?${mask}&currentDocument.exists=true`, {
      method: "PATCH",
      body: JSON.stringify({ fields: encodeFields(data) }),
    });
    if (!res.ok) throw new FirestoreError(res.status, `update ${path} failed`);
  }

  /**
   * Deletes a document. With [updateTime] the delete only succeeds if nobody
   * changed or deleted it since it was read - used to make a pairing code
   * single-use even when two requests race.
   */
  async delete(path: string, updateTime?: string): Promise<boolean> {
    // A commit with a precondition (rather than DELETE ?currentDocument.updateTime=,
    // which the emulator mis-handles) - same behaviour in production and in tests.
    const name = `projects/${this.env.FIREBASE_PROJECT_ID}/databases/(default)/documents/${path}`;
    const write: Record<string, unknown> = { delete: name };
    if (updateTime) write.currentDocument = { updateTime };
    const res = await this.call(`${this.base}:commit`, { method: "POST", body: JSON.stringify({ writes: [write] }) });
    if (res.status === 400 || res.status === 404 || res.status === 409 || res.status === 412) return false;
    if (!res.ok) throw new FirestoreError(res.status, `delete ${path} failed`);
    return true;
  }
}
