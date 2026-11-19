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

/** A document path with a segment that isn't a plain id (thrown before anything is fetched). */
export class UnsafePathError extends Error {}

/** Every collection name and document id the server uses: letters, digits, "_" and "-". */
export const SAFE_SEGMENT = /^[A-Za-z0-9_-]{1,128}$/;

/**
 * Throws unless every segment of [path] is a plain id. Ids come from tool
 * input (meal, kitchen, order, coupon ids), so this is what stops "../",
 * "?", "%2F" or ":commit" from ever reaching a Firestore URL.
 */
export function assertSafePath(path: string): void {
  const segments = path.split("/");
  if (!segments.length || !segments.every((s) => SAFE_SEGMENT.test(s))) {
    throw new UnsafePathError("unsafe Firestore path");
  }
}

/** Error statuses that mean "someone else changed the data first" (a precondition), not a failure. */
const CONFLICT_STATUSES = new Set(["FAILED_PRECONDITION", "ALREADY_EXISTS", "NOT_FOUND", "ABORTED"]);

/** Reads Firestore's error body ({ error: { status, message } }) - best effort. */
async function errorOf(res: Response): Promise<{ status: string; message: string }> {
  try {
    const j = (await res.json()) as { error?: { status?: string; message?: string } };
    return { status: j.error?.status ?? "", message: j.error?.message ?? "" };
  } catch {
    return { status: "", message: "" };
  }
}

const isConflict = (http: number, status: string) => http === 409 || http === 412 || CONFLICT_STATUSES.has(status);

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
 * One write inside an atomic commit. A precondition makes the whole commit
 * fail (nothing written) if the document changed or exists unexpectedly -
 * that's how plates are reserved and a TrxID is kept unique without a
 * server-side transaction runner.
 */
export type Write =
  | { op: "create"; path: string; data: Record<string, unknown> }
  | { op: "update"; path: string; data: Record<string, unknown>; ifUpdateTime?: string; increments?: Record<string, number> }
  | { op: "merge"; path: string; data: Record<string, unknown>; increments?: Record<string, number> }
  | { op: "delete"; path: string; ifUpdateTime?: string };

/** Thrown when a commit's precondition failed (someone else changed the data first). */
export class ConflictError extends Error {}

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
    assertSafePath(path);
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
    opts: {
      filters?: Filter[];
      select?: string[];
      limit?: number;
      parentPath?: string;
      /** Single-field ordering only (automatic index, no composite index needed). */
      orderBy?: { field: string; direction: "ASCENDING" | "DESCENDING" };
    } = {},
  ): Promise<FsDoc[]> {
    assertSafePath(collection);
    if (opts.parentPath) assertSafePath(opts.parentPath);
    const structuredQuery: Record<string, unknown> = { from: [{ collectionId: collection }] };
    if (opts.select) structuredQuery.select = { fields: opts.select.map((f) => ({ fieldPath: f })) };
    if (opts.orderBy) structuredQuery.orderBy = [{ field: { fieldPath: opts.orderBy.field }, direction: opts.orderBy.direction }];
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

  private docName(path: string): string {
    return `projects/${this.env.FIREBASE_PROJECT_ID}/databases/(default)/documents/${path}`;
  }

  /** Several writes, all or nothing. Throws [ConflictError] when a precondition fails. */
  async commit(writes: Write[]): Promise<void> {
    for (const w of writes) assertSafePath(w.path);
    const body = writes.map((w) => {
      const transforms = (inc?: Record<string, number>) =>
        inc && Object.keys(inc).length
          ? Object.entries(inc).map(([fieldPath, by]) => ({ fieldPath, increment: encodeValue(by) }))
          : undefined;
      switch (w.op) {
        case "create":
          return {
            update: { name: this.docName(w.path), fields: encodeFields(w.data) },
            currentDocument: { exists: false },
          };
        case "update":
          return {
            update: { name: this.docName(w.path), fields: encodeFields(w.data) },
            updateMask: { fieldPaths: Object.keys(w.data) },
            currentDocument: w.ifUpdateTime ? { updateTime: w.ifUpdateTime } : { exists: true },
            updateTransforms: transforms(w.increments),
          };
        case "merge":
          return {
            update: { name: this.docName(w.path), fields: encodeFields(w.data) },
            updateMask: { fieldPaths: Object.keys(w.data) },
            updateTransforms: transforms(w.increments),
          };
        case "delete":
          return { delete: this.docName(w.path), currentDocument: w.ifUpdateTime ? { updateTime: w.ifUpdateTime } : undefined };
      }
    });
    const res = await this.call(`${this.base}:commit`, { method: "POST", body: JSON.stringify({ writes: body }) });
    if (!res.ok) {
      const err = await errorOf(res);
      if (isConflict(res.status, err.status)) throw new ConflictError(`commit failed (${err.status || res.status})`);
      console.error("firestore commit failed", res.status, err.status, err.message);
      throw new FirestoreError(res.status, `commit failed (${err.status || res.status})`);
    }
  }

  /** Creates a document; fails (409) if it already exists. */
  async create(collectionPath: string, id: string, data: Record<string, unknown>): Promise<void> {
    assertSafePath(`${collectionPath}/${id}`);
    const res = await this.call(`${this.base}/${collectionPath}?documentId=${encodeURIComponent(id)}`, {
      method: "POST",
      body: JSON.stringify({ fields: encodeFields(data) }),
    });
    if (!res.ok) {
      const err = await errorOf(res);
      if (res.status === 409 || err.status === "ALREADY_EXISTS") throw new ConflictError(`${collectionPath}/${id} already exists`);
      throw new FirestoreError(res.status, `create ${collectionPath}/${id} failed (${err.status || res.status})`);
    }
  }

  /** Sets only [data]'s fields on an existing document. */
  async update(path: string, data: Record<string, unknown>): Promise<void> {
    assertSafePath(path);
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
    assertSafePath(path);
    // A commit with a precondition (rather than DELETE ?currentDocument.updateTime=,
    // which the emulator mis-handles) - same behaviour in production and in tests.
    const name = `projects/${this.env.FIREBASE_PROJECT_ID}/databases/(default)/documents/${path}`;
    const write: Record<string, unknown> = { delete: name };
    if (updateTime) write.currentDocument = { updateTime };
    const res = await this.call(`${this.base}:commit`, { method: "POST", body: JSON.stringify({ writes: [write] }) });
    if (res.ok) return true;
    const err = await errorOf(res);
    if (isConflict(res.status, err.status)) return false;
    console.error("firestore delete failed", res.status, err.status, err.message);
    throw new FirestoreError(res.status, `delete ${path} failed (${err.status || res.status})`);
  }
}
