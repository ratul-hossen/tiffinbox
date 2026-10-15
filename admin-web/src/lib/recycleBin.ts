import { deleteDoc, doc, setDoc } from "firebase/firestore";
import { auth, db } from "../firebase";
import type { RecycleBinEntry } from "../types";

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

/** Writes the entry's snapshot back to its original collection/id, then removes the recycle_bin entry. */
export async function restoreFromRecycleBin(entry: RecycleBinEntry): Promise<void> {
  await setDoc(doc(db, entry.sourceCollection, entry.sourceId), entry.data ?? {});
  await deleteDoc(doc(db, "recycle_bin", entry.id));
}

/** Deletes the recycle_bin entry itself - the data is gone for good after this. */
export async function permanentlyDelete(entryId: string): Promise<void> {
  await deleteDoc(doc(db, "recycle_bin", entryId));
}
