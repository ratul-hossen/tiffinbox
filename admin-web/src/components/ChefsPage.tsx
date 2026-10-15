import { useMemo, useState } from "react";
import { addDoc, collection, deleteDoc, doc, setDoc, updateDoc, writeBatch } from "firebase/firestore";
import { createUserWithEmailAndPassword, signOut as signOutSecondary } from "firebase/auth";
import { db, secondaryAuth } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { Chef, Meal, Order, Review } from "../types";
import { logActivity } from "../lib/activityLog";
import { DEMO_CHEFS } from "../data/demoChefSeed";
import Badge from "./ui/Badge";
import ChefDetail from "./ChefDetail";

interface Props {
  search: string;
}

export default function ChefsPage({ search }: Props) {
  const { data: chefs, loading, error } = useCollection<Chef>("chefs");
  const { data: meals } = useCollection<Meal>("meals");
  const { data: orders } = useCollection<Order>("orders");
  const { data: reviews } = useCollection<Review>("reviews");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState<{ kitchenName: string; bio: string; specialty: string }>({
    kitchenName: "",
    bio: "",
    specialty: "",
  });
  const [busyId, setBusyId] = useState<string | null>(null);
  const [detailChefId, setDetailChefId] = useState<string | null>(null);
  const [seeding, setSeeding] = useState(false);
  const [removingDemo, setRemovingDemo] = useState(false);
  const [demoMessage, setDemoMessage] = useState<string | null>(null);

  const demoChefCount = useMemo(() => chefs.filter((c) => c.isDemo).length, [chefs]);

  /**
   * Creates the 5 fixed demo chefs (and their menus) from demoChefSeed.ts,
   * each marked isDemo: true. Safe to click more than once - a chef whose
   * demo email already has an Auth account is skipped rather than
   * duplicated. Every write goes through secondaryAuth exactly like
   * DeliveryPartnersPage's account creation, since firestore.rules only
   * allows `create` on chefs/{uid} to the signed-in uid itself.
   */
  async function seedDemoChefs() {
    setSeeding(true);
    setDemoMessage(null);
    let created = 0;
    let skipped = 0;
    try {
      for (const demoChef of DEMO_CHEFS) {
        try {
          const credential = await createUserWithEmailAndPassword(
            secondaryAuth,
            demoChef.email,
            demoChef.password,
          );
          const uid = credential.user.uid;
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
            // Demo chefs don't need the real approval workflow - they're
            // clearly labeled as demo instead (see isDemo below).
            isVerified: true,
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
          await signOutSecondary(secondaryAuth);
          created += 1;
        } catch (err) {
          if (err instanceof Error && err.message.includes("auth/email-already-in-use")) {
            skipped += 1;
            continue;
          }
          throw err;
        }
      }
      await logActivity("Seeded demo chefs", "chefs", "(bulk)", { created, skipped });
      setDemoMessage(
        skipped > 0
          ? `Created ${created} demo chef(s), skipped ${skipped} already seeded.`
          : `Created ${created} demo chef(s).`,
      );
    } catch (err) {
      setDemoMessage(err instanceof Error ? `Failed: ${err.message}` : "Failed to seed demo chefs.");
    } finally {
      setSeeding(false);
    }
  }

  /** One-click removal of every chef/meal created by seedDemoChefs(). */
  async function removeAllDemoChefs() {
    const demoChefs = chefs.filter((c) => c.isDemo);
    if (demoChefs.length === 0) {
      setDemoMessage("No demo chefs to remove.");
      return;
    }
    if (!confirm(`Remove all ${demoChefs.length} demo chefs and their menus? This cannot be undone.`)) {
      return;
    }
    setRemovingDemo(true);
    setDemoMessage(null);
    try {
      const demoMeals = meals.filter((m) => m.isDemo);
      const batch = writeBatch(db);
      demoChefs.forEach((c) => batch.delete(doc(db, "chefs", c.id)));
      demoMeals.forEach((m) => batch.delete(doc(db, "meals", m.id)));
      await batch.commit();
      await logActivity("Removed all demo chefs", "chefs", "(bulk)", {
        chefCount: demoChefs.length,
        mealCount: demoMeals.length,
      });
      setDemoMessage(`Removed ${demoChefs.length} demo chef(s) and ${demoMeals.length} demo meal(s).`);
    } catch (err) {
      setDemoMessage(err instanceof Error ? `Failed: ${err.message}` : "Failed to remove demo chefs.");
    } finally {
      setRemovingDemo(false);
    }
  }

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return chefs;
    return chefs.filter(
      (c) =>
        (c.kitchenName ?? "").toLowerCase().includes(q) ||
        (c.ownerName ?? "").toLowerCase().includes(q) ||
        c.id.toLowerCase().includes(q),
    );
  }, [chefs, search]);

  function startEdit(chef: Chef) {
    setEditingId(chef.id);
    setDraft({
      kitchenName: chef.kitchenName ?? "",
      bio: chef.bio ?? "",
      specialty: (chef.specialty ?? []).join(", "),
    });
  }

  async function saveEdit(chefId: string) {
    setBusyId(chefId);
    try {
      const specialty = draft.specialty
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
      await updateDoc(doc(db, "chefs", chefId), {
        kitchenName: draft.kitchenName,
        bio: draft.bio,
        specialty,
      });
      await logActivity("Edited chef profile", "chefs", chefId, {
        kitchenName: draft.kitchenName,
        specialty,
      });
      setEditingId(null);
    } finally {
      setBusyId(null);
    }
  }

  async function toggleField(chef: Chef, field: "isVerified" | "isAvailableToday") {
    setBusyId(chef.id);
    try {
      const next = !chef[field];
      await updateDoc(doc(db, "chefs", chef.id), { [field]: next });
      await logActivity(
        field === "isVerified" ? "Toggled chef verification" : "Toggled chef availability",
        "chefs",
        chef.id,
        { [field]: next },
      );
      if (field === "isVerified" && next) {
        // In-app notification (notifications/{uid}/items) - the chef's own
        // dashboard pending-approval screen already reflects this live, but
        // this also shows up in their notification history.
        await addDoc(collection(db, "notifications", chef.id, "items"), {
          title: "Account approved",
          body: "Your kitchen profile has been approved - you can now manage your menu and receive orders.",
          createdAt: Date.now(),
        });
      }
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(chefId: string) {
    if (!confirm("Delete this chef? This cannot be undone.")) return;
    setBusyId(chefId);
    try {
      await deleteDoc(doc(db, "chefs", chefId));
      await logActivity("Deleted chef", "chefs", chefId);
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading chefs…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  const detailChef = chefs.find((c) => c.id === detailChefId) ?? null;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <p className="text-sm text-apple-secondary">{filtered.length} chefs</p>
        <div className="flex items-center gap-2">
          <button
            onClick={seedDemoChefs}
            disabled={seeding}
            className="text-xs bg-apple-blue hover:bg-apple-blue-dark disabled:opacity-60 text-white rounded-xl px-3 py-2 font-medium transition-colors duration-150"
          >
            {seeding ? "Seeding…" : "🌱 Seed 5 demo chefs"}
          </button>
          <button
            onClick={removeAllDemoChefs}
            disabled={removingDemo || demoChefCount === 0}
            className="text-xs bg-apple-red hover:brightness-95 disabled:opacity-40 text-white rounded-xl px-3 py-2 font-medium transition-colors duration-150"
          >
            {removingDemo ? "Removing…" : `🗑️ Remove all demo chefs (${demoChefCount})`}
          </button>
        </div>
      </div>
      {demoMessage && (
        <p className="text-xs text-apple-secondary bg-apple-subtle rounded-xl px-3 py-2">{demoMessage}</p>
      )}

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {filtered.map((chef) => (
          <div
            key={chef.id}
            className="bg-apple-surface rounded-2xl border border-apple-border p-5 shadow-card hover:shadow-card-hover transition-shadow duration-200"
          >
            {editingId === chef.id ? (
              <div className="space-y-2">
                <input
                  className="w-full text-sm border border-apple-border2 bg-apple-surface2 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-apple-blue/50 transition-all duration-150"
                  value={draft.kitchenName}
                  onChange={(e) => setDraft({ ...draft, kitchenName: e.target.value })}
                  placeholder="Kitchen name"
                />
                <textarea
                  className="w-full text-sm border border-apple-border2 bg-apple-surface2 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-apple-blue/50 transition-all duration-150"
                  value={draft.bio}
                  onChange={(e) => setDraft({ ...draft, bio: e.target.value })}
                  placeholder="Bio"
                  rows={2}
                />
                <input
                  className="w-full text-sm border border-apple-border2 bg-apple-surface2 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-apple-blue/50 transition-all duration-150"
                  value={draft.specialty}
                  onChange={(e) => setDraft({ ...draft, specialty: e.target.value })}
                  placeholder="Specialty (comma separated)"
                />
                <div className="flex gap-2">
                  <button
                    onClick={() => saveEdit(chef.id)}
                    disabled={busyId === chef.id}
                    className="text-xs bg-apple-blue hover:bg-apple-blue-dark disabled:opacity-60 text-white rounded-lg px-3 py-1.5 font-medium transition-colors duration-150"
                  >
                    Save
                  </button>
                  <button
                    onClick={() => setEditingId(null)}
                    className="text-xs bg-apple-hover hover:bg-apple-hover2 rounded-lg px-3 py-1.5 transition-colors duration-150"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <>
                <div className="flex items-start justify-between">
                  <div>
                    <div className="font-semibold text-apple-text flex items-center gap-1.5">
                      {chef.emoji ?? "👨‍🍳"} {chef.kitchenName ?? "(unnamed kitchen)"}
                      {chef.isDemo && (
                        <span className="text-[10px] font-semibold uppercase tracking-wide bg-amber-100 text-amber-700 rounded-full px-2 py-0.5">
                          Demo
                        </span>
                      )}
                    </div>
                    <div className="text-xs text-apple-secondary">{chef.ownerName}</div>
                  </div>
                  <div className="text-xs text-apple-secondary/70">{chef.id.slice(0, 6)}</div>
                </div>

                <p className="text-sm text-apple-secondary mt-2 line-clamp-2">{chef.bio}</p>

                {chef.specialty && chef.specialty.length > 0 && (
                  <div className="flex flex-wrap gap-1 mt-2">
                    {chef.specialty.map((s) => (
                      <span
                        key={s}
                        className="text-xs bg-apple-blue/10 text-apple-blue rounded-full px-2 py-0.5"
                      >
                        {s}
                      </span>
                    ))}
                  </div>
                )}

                <div className="grid grid-cols-3 gap-2 text-xs text-apple-secondary mt-3">
                  <div>Rating: {chef.overallRating ?? "–"}</div>
                  <div>Orders: {chef.totalOrders ?? 0}</div>
                  <div>Dist: {chef.distanceKm ?? "–"} km</div>
                </div>

                <div className="flex flex-wrap items-center gap-2 mt-3">
                  <Badge
                    tone={chef.isVerified ? "green" : "neutral"}
                    onClick={() => toggleField(chef, "isVerified")}
                    disabled={busyId === chef.id}
                  >
                    {chef.isVerified ? "Approved" : "Pending approval"}
                  </Badge>
                  <Badge
                    tone={chef.isAvailableToday ? "blue" : "neutral"}
                    onClick={() => toggleField(chef, "isAvailableToday")}
                    disabled={busyId === chef.id}
                  >
                    {chef.isAvailableToday ? "Available today" : "Unavailable"}
                  </Badge>
                  {chef.deliveryMode === "tiffinbox" && (
                    <Badge tone="blue">🛵 TiffinBox Delivery</Badge>
                  )}
                </div>
                {!chef.isVerified && (
                  <p className="text-[11px] text-apple-secondary mt-1.5">
                    Hidden from customers and can&apos;t receive orders until approved.
                  </p>
                )}

                <div className="flex gap-3 mt-3 pt-3 border-t border-apple-border text-xs">
                  <button
                    onClick={() => setDetailChefId(chef.id)}
                    className="text-apple-blue hover:text-apple-blue-dark font-medium transition-colors duration-150"
                  >
                    View details
                  </button>
                  <button
                    onClick={() => startEdit(chef)}
                    className="text-apple-secondary hover:text-apple-text transition-colors duration-150"
                  >
                    Edit
                  </button>
                  <button
                    onClick={() => handleDelete(chef.id)}
                    disabled={busyId === chef.id}
                    className="text-apple-red hover:opacity-80 transition-opacity duration-150"
                  >
                    Delete
                  </button>
                </div>
              </>
            )}
          </div>
        ))}
      </div>

      {filtered.length === 0 && <p className="text-sm text-apple-secondary">No chefs found.</p>}

      <ChefDetail
        chef={detailChef}
        meals={meals}
        orders={orders}
        reviews={reviews}
        onClose={() => setDetailChefId(null)}
      />
    </div>
  );
}
