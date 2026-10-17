# firestore.rules tests

These run the **real** Firestore rules engine (the emulator, not a
reimplementation) against `../firestore.rules`, so they tell you whether the
file you are about to paste into Firebase Console actually behaves. Free: the
emulator is local and never touches the live `tiffinbox-2cb8b` project.

```bash
cd firestore-tests
npm install     # once; needs Java 11+ for the emulator jar
npm test
```

What they cover today: `orders` create - every real order shape the app writes
(plain, free delivery, coupon + points + rank discount, a zero total, a
pre-order, a surcharged TiffinBox order, an admin restoring from the Recycle
Bin) must be allowed, and the tampered ones (negative money, a rank discount
above the cap, an absurd fee, someone else's `customerId`, a self-assigned
`Delivered`) must be refused; `delivery_surcharges` read/write; and that a chef
cannot raise their own `maxDeliveryFee` while the admin can.

`firebase.json` here only starts the emulator on port 8899; the ruleset itself
is loaded by `rules.test.mjs` from `../firestore.rules`, because the CLI refuses
a rules path outside its own project directory.

Worth extending whenever a rule changes - a mistake here is not a crash, it is
a customer who cannot place an order.
