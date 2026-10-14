import { initializeApp, getApps, getApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

// Values come from admin-web/.env.local (see README.md). This must point at
// the SAME Firebase project the TiffinBox Android app uses, so the admin
// panel reads/writes the exact same Firestore collections.
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);

// A second, independent Firebase App instance used ONLY to create
// delivery-partner Auth accounts from the admin panel. Firebase's client SDK
// signs the calling app instance in as whatever user createUserWithEmailAndPassword
// just created, which would otherwise kick the admin out of their own
// session - running that one call against this separate instance avoids
// that entirely, with no backend/Cloud Functions (and so no Blaze plan)
// required.
const secondaryApp = getApps().some((a) => a.name === "DeliveryPartnerCreation")
  ? getApp("DeliveryPartnerCreation")
  : initializeApp(firebaseConfig, "DeliveryPartnerCreation");
export const secondaryAuth = getAuth(secondaryApp);
