import {
  collection,
  deleteDoc,
  doc,
  getDocs,
  setDoc,
  writeBatch,
  type DocumentReference,
} from "firebase/firestore";
import { auth, db } from "../firebase";
import type { RecycleBinEntry } from "../types";

/**
 * Reserved key inside a recycle_bin entry's `data` holding a chat thread's
 * `messages` subcollection. Prefixed so it can never collide with a real
 * field on the snapshotted document.
 */
const CHAT_MESSAGES_KEY = "__messages";

/** Firestore caps a writeBatch at 500 operations. */
const BATCH_LIMIT = 500;

/** Deterministic recycle_bin doc id for a given source doc - also usable from a writeBatch. */
export function recycleBinEntryId(sourceCollection: string, sourceId: string): string {
  return `${sourceCollection}__${sourceId}`;
}

/**
 * Builds the recycle_bin document body for a soft-deleted doc. Exposed
 * separately from `softDelete` so a bulk removal (e.g. ChefsPage's "Remove
 * all demo chefs") can fold the snapshot write and the real delete into one
 * atomic `writeBatch` instead of many sequential read/write pairs.
 *
 * `label` is a short human-readable string for the Recycle Bin list (e.g. a
 * chef's kitchen name, a meal's name) - purely cosmetic, not used for lookup.
 */
export function buildRecycleBinData(sourceCollection: string, sourceId: string, data: object, label: string) {
  // `data` comes from useCollection, which spreads `{ id, ...doc.data() }` -
  // strip the redundant `id` field before storing the snapshot. Accepting
  // `object` here (rather than Record<string, unknown>) lets every concrete
  // doc type (Chef, Meal, Review, ...) - none of which declare an index
  // signature - be passed straight through from each page's delete handler.
  const { id: _ignoredId, ...snapshot } = data as { id?: unknown };
  return {
    sourceCollection,
    sourceId,
    data: snapshot,
    label,
    deletedAt: Date.now(),
    deletedByEmail: auth.currentUser?.email ?? null,
  };
}

/**
 * Soft-delete: snapshots a doc into `recycle_bin/{collection}__{id}`, then
 * removes it from its real collection. The app (and every other admin page)
 * stops seeing it immediately - restoring is the only way back, from the
 * Recycle Bin page.
 */
export async function softDelete(
  sourceCollection: string,
  sourceId: string,
  data: object,
  label: string,
): Promise<void> {
  const entryId = recycleBinEntryId(sourceCollection, sourceId);
  await setDoc(doc(db, "recycle_bin", entryId), buildRecycleBinData(sourceCollection, sourceId, data, label));
  await deleteDoc(doc(db, sourceCollection, sourceId));
}

/** Deletes `refs` in 500-operation batches (Firestore's writeBatch limit). */
async function deleteInChunks(refs: DocumentReference[]): Promise<void> {
  for (let i = 0; i < refs.length; i += BATCH_LIMIT) {
    const batch = writeBatch(db);
    for (const ref of refs.slice(i, i + BATCH_LIMIT)) batch.delete(ref);
    await batch.commit();
  }
}

/**
 * Soft-deletes a chat thread *and* its `messages` subcollection.
 *
 * Deleting a Firestore document does not touch its subcollections, so a
 * plain `softDelete("chats", ...)` would leave every message orphaned —
 * invisible in the panel and in the app, but still stored, and unreachable
 * once the Recycle Bin entry is purged. So the messages are snapshotted into
 * the entry alongside the thread (under the reserved `__messages` key) and
 * then deleted too: "Delete forever" really removes everything, and Restore
 * brings the whole conversation back, not an empty thread.
 */
export async function softDeleteChat(chatId: string, thread: object, label: string): Promise<void> {
  const messagesSnap = await getDocs(collection(db, "chats", chatId, "messages"));
  const entry = buildRecycleBinData("chats", chatId, thread, label);

  await setDoc(doc(db, "recycle_bin", recycleBinEntryId("chats", chatId)), {
    ...entry,
    data: {
      ...entry.data,
      [CHAT_MESSAGES_KEY]: messagesSnap.docs.map((d) => ({ id: d.id, ...d.data() })),
    },
  });

  // Messages first, thread doc last: if this dies halfway the thread is still
  // listed and the delete can simply be retried, rather than silently leaving
  // an orphaned subcollection behind a vanished parent.
  await deleteInChunks([...messagesSnap.docs.map((d) => d.ref), doc(db, "chats", chatId)]);
}

/** Writes the entry's snapshot back to its original collection/id, then removes the recycle_bin entry. */
export async function restoreFromRecycleBin(entry: RecycleBinEntry): Promise<void> {
  const data = { ...(entry.data ?? {}) };
  // Chat threads carry their messages in the snapshot (see softDeleteChat) -
  // they belong in the subcollection, not as a field on the thread doc.
  const messages = data[CHAT_MESSAGES_KEY];
  delete data[CHAT_MESSAGES_KEY];

  await setDoc(doc(db, entry.sourceCollection, entry.sourceId), data);

  if (Array.isArray(messages)) {
    for (let i = 0; i < messages.length; i += BATCH_LIMIT) {
      const batch = writeBatch(db);
      for (const message of messages.slice(i, i + BATCH_LIMIT)) {
        const { id, ...body } = message as { id?: string };
        if (!id) continue;
        batch.set(doc(db, entry.sourceCollection, entry.sourceId, "messages", id), body);
      }
      await batch.commit();
    }
  }

  await deleteDoc(doc(db, "recycle_bin", entry.id));
}

/** Deletes the recycle_bin entry itself - the data is gone for good after this. */
export async function permanentlyDelete(entryId: string): Promise<void> {
  await deleteDoc(doc(db, "recycle_bin", entryId));
}
