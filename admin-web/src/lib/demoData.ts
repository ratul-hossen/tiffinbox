import {
  addDoc,
  collection,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  where,
  writeBatch,
  type DocumentReference,
} from "firebase/firestore";
import {
  createUserWithEmailAndPassword,
  deleteUser,
  signInWithEmailAndPassword,
  signOut as signOutSecondary,
} from "firebase/auth";
import { db, secondaryAuth } from "../firebase";
import { DEMO_CHEFS, type DemoChefSeed } from "../data/demoChefSeed";

/**
 * Demo-chef lifecycle for admin-web, done the way a production "sandbox
 * data" switch works: seeding is idempotent (click it any time, it only
 * fills what's missing), and removal is a full cascade - the demo chef and
 * everything hanging off it (menu, orders, reviews, chats + messages,
 * coupons, subscriptions, delivery application, the chef's notifications, Recycle Bin copies) and
 * finally the demo login account itself, so revenue/order stats contain no
 * demo leftovers and the next seed starts from a clean slate.
 *
 * Firestore deletes run as the signed-in admin (primary `db`); the Auth
 * account is deleted by signing in as the demo chef on `secondaryAuth`
 * (a user may always delete their own account - no Admin SDK / Cloud
 * Function needed, so this stays on the free Spark plan).
 *
 * Deliberately NOT touched: admins, admin_activity_log (append-only audit
 * trail), real chefs, real customers' user docs.
 */

const BATCH_LIMIT = 450;

async function deleteRefs(refs: DocumentReference[]): Promise<void> {
  for (let i = 0; i < refs.length; i += BATCH_LIMIT) {
    const batch = writeBatch(db);
    refs.slice(i, i + BATCH_LIMIT).forEach((ref) => batch.delete(ref));
    await batch.commit();
  }
}

async function refsWhere(collectionName: string, field: string, value: string): Promise<DocumentReference[]> {
  const snap = await getDocs(query(collection(db, collectionName), where(field, "==", value)));
  return snap.docs.map((d) => d.ref);
}

export interface PurgeCounts {
  chefs: number;
  meals: number;
  orders: number;
  reviews: number;
  chats: number;
  messages: number;
  coupons: number;
  subscriptions: number;
  notifications: number;
  recycleBin: number;
  accounts: number;
}

function emptyCounts(): PurgeCounts {
  return {
    chefs: 0,
    meals: 0,
    orders: 0,
    reviews: 0,
    chats: 0,
    messages: 0,
    coupons: 0,
    subscriptions: 0,
    notifications: 0,
    recycleBin: 0,
    accounts: 0,
  };
}

/** Deletes one chef's doc and every document that belongs to that chef. */
async function purgeChefData(chefId: string, counts: PurgeCounts): Promise<void> {
  const [meals, orders, reviews, coupons, subscriptions, chats] = await Promise.all([
    refsWhere("meals", "chefId", chefId),
    refsWhere("orders", "chefId", chefId),
    refsWhere("reviews", "chefId", chefId),
    refsWhere("coupons", "chefId", chefId),
    refsWhere("subscriptions", "chefId", chefId),
    refsWhere("chats", "chefId", chefId),
  ]);

  // Sub-collections aren't removed with their parent doc in Firestore.
  const messages: DocumentReference[] = [];
  for (const chat of chats) {
    const snap = await getDocs(collection(chat, "messages"));
    messages.push(...snap.docs.map((d) => d.ref));
  }
  const notifications = (await getDocs(collection(db, "notifications", chefId, "items"))).docs.map((d) => d.ref);

  // Recycle Bin copies of this chef, or of anything that belonged to it.
  const binIds = new Set<string>();
  const binRefs: DocumentReference[] = [];
  for (const ref of [
    ...(await refsWhere("recycle_bin", "sourceId", chefId)),
    ...(await refsWhere("recycle_bin", "data.chefId", chefId)),
  ]) {
    if (!binIds.has(ref.id)) {
      binIds.add(ref.id);
      binRefs.push(ref);
    }
  }

  const applicationRef = doc(db, "delivery_applications", chefId);
  const hasApplication = (await getDoc(applicationRef)).exists();
  const chefRef = doc(db, "chefs", chefId);
  const chefExists = (await getDoc(chefRef)).exists();

  await deleteRefs([
    ...messages,
    ...chats,
    ...meals,
    ...orders,
    ...reviews,
    ...coupons,
    ...subscriptions,
    ...notifications,
    ...binRefs,
    ...(hasApplication ? [applicationRef] : []),
    doc(db, "users", chefId),
    chefRef,
  ]);

  counts.chefs += chefExists ? 1 : 0;
  counts.meals += meals.length;
  counts.orders += orders.length;
  counts.reviews += reviews.length;
  counts.chats += chats.length;
  counts.messages += messages.length;
  counts.coupons += coupons.length;
  counts.subscriptions += subscriptions.length;
  counts.notifications += notifications.length;
  counts.recycleBin += binRefs.length;
}

/**
 * Removes all demo data. Every demo account from demoChefSeed.ts is signed
 * into (to learn its uid and delete it), plus any leftover chef doc still
 * flagged isDemo (e.g. one whose password was changed) is purged too.
 */
export async function purgeAllDemoData(): Promise<PurgeCounts> {
  const counts = emptyCounts();
  const purged = new Set<string>();

  for (const demoChef of DEMO_CHEFS) {
    let credential;
    try {
      credential = await signInWithEmailAndPassword(secondaryAuth, demoChef.email, demoChef.password);
    } catch {
      continue; // No such account (already removed) or password changed - handled by the isDemo sweep below.
    }
    const uid = credential.user.uid;
    await purgeChefData(uid, counts);
    purged.add(uid);
    try {
      await deleteUser(credential.user);
      counts.accounts += 1;
    } catch {
      await signOutSecondary(secondaryAuth);
    }
  }

  const leftovers = await getDocs(query(collection(db, "chefs"), where("isDemo", "==", true)));
  for (const chef of leftovers.docs) {
    if (!purged.has(chef.id)) {
      await purgeChefData(chef.id, counts);
      purged.add(chef.id);
    }
  }
  // Stray demo meals whose chef doc is already gone.
  const strayMeals = await getDocs(query(collection(db, "meals"), where("isDemo", "==", true)));
  counts.meals += strayMeals.size;
  await deleteRefs(strayMeals.docs.map((d) => d.ref));

  return counts;
}

async function writeDemoChef(uid: string, demoChef: DemoChefSeed): Promise<void> {
  await setDoc(doc(db, "chefs", uid), {
    userId: uid,
    ownerName: demoChef.ownerName,
    kitchenName: demoChef.kitchenName,
    kitchenType: demoChef.kitchenType,
    yearsOfExperience: demoChef.yearsOfExperience,
    emoji: demoChef.emoji,
    photoBase64: demoChef.photoBase64,
    bio: demoChef.bio,
    specialty: demoChef.specialty,
    whatsappNumber: demoChef.whatsappNumber,
    bkashNumber: demoChef.bkashNumber,
    nagadNumber: demoChef.nagadNumber,
    latitude: demoChef.latitude,
    longitude: demoChef.longitude,
    distanceKm: 1.0,
    overallRating: demoChef.overallRating,
    taste: demoChef.taste,
    hygiene: demoChef.hygiene,
    packaging: demoChef.packaging,
    onTime: demoChef.onTime,
    totalOrders: demoChef.totalOrders,
    // Demo chefs skip the approval workflow - they're labeled as demo instead.
    isVerified: true,
    approvalStatus: "approved",
    isAvailableToday: true,
    deliveryMode: "self",
    isDemo: true,
  });
  for (const meal of demoChef.meals) {
    await addDoc(collection(db, "meals"), {
      chefId: uid,
      name: meal.name,
      emoji: meal.emoji,
      description: meal.description,
      price: meal.price,
      category: meal.category,
      isVeg: meal.isVeg,
      dietaryTags: meal.dietaryTags,
      allergens: meal.allergens,
      availableQuantity: meal.availableQuantity,
      portionSize: meal.portionSize,
      isAvailable: true,
      isDemo: true,
    });
  }
}

export interface SeedResult {
  created: number;
  skipped: number;
  failed: string[];
}

/**
 * Idempotent: creates each missing demo chef. If its login account already
 * exists (e.g. left over from an older removal), it signs in to that
 * account and only re-creates the chef doc + menu when they're missing.
 */
export async function seedDemoChefs(): Promise<SeedResult> {
  const result: SeedResult = { created: 0, skipped: 0, failed: [] };
  for (const demoChef of DEMO_CHEFS) {
    try {
      let uid: string;
      try {
        uid = (await createUserWithEmailAndPassword(secondaryAuth, demoChef.email, demoChef.password)).user.uid;
      } catch (err) {
        if (!(err instanceof Error && err.message.includes("auth/email-already-in-use"))) throw err;
        uid = (await signInWithEmailAndPassword(secondaryAuth, demoChef.email, demoChef.password)).user.uid;
        if ((await getDoc(doc(db, "chefs", uid))).exists()) {
          result.skipped += 1;
          continue;
        }
      }
      await writeDemoChef(uid, demoChef);
      result.created += 1;
    } catch {
      result.failed.push(demoChef.kitchenName);
    } finally {
      if (secondaryAuth.currentUser) await signOutSecondary(secondaryAuth);
    }
  }
  return result;
}
