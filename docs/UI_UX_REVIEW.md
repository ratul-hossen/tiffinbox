# TiffinBox UI/UX Review (2026-10-16)

এই session-এ যে পরিবর্তনগুলো হয়েছে (Role Selection, Home header, Meal Details,
Cart, Lottie motion), সেগুলো একজন professional UI/UX designer-এর চোখে যাচাই করা।
Owner-এর পাঠানো screenshot আর code পড়ে করা, emulator-এ চালিয়ে নয়
(এখানে Android SDK নেই)।

Status-এর মানে:
- **✅ Fixed:** এই review-এর পরের commit-এ ঠিক করা হয়েছে।
- **🕓 Later:** পরের ধাপে করা হবে।
- **❓ Owner:** লেখা (copy) বা behaviour বদলায়, তাই owner-এর সিদ্ধান্ত লাগবে।

## সার্বিক মূল্যায়ন

| দিক | নম্বর (১০-এ) | মন্তব্য |
|---|---|---|
| Visual identity | ৭ | Black + red theme, আসল খাবারের ছবি আর orbit animation app-কে চেনার মতো করেছে। |
| Layout ও hierarchy | ৫.৫ | Home header অনেক লম্বা, আসল content (খাবার, chef) নিচে চলে যায়। |
| Consistency | ৬ | "See all" pill, chip, সংখ্যার format একেক জায়গায় একেক রকম। |
| Motion | ৮ | Cleanup-এর পর motion purpose-driven। Orbit সবচেয়ে ভালো অংশ। |
| Data states (empty/new/far) | ৪.৫ | নতুন chef-এর "0.0 (0)", "2635.8 km" দেখতে ভাঙা লাগে। |
| Accessibility | ৬ | Contrast মোটামুটি, কিছু touch target ছোট, emoji screen reader-এ পড়া হয়। |

**সবচেয়ে ভালো:** খাবারের orbit animation, Meal Details-এর full-bleed ছবি, Home header-এ
profile / app name / chat / cart এক সারিতে আসা, Role Selection-এর selection ring।

**সবচেয়ে বড় সমস্যা:** Home-এর প্রথম screen-এ খাবার প্রায় দেখাই যায় না, আর chef card-এর
data (rating, distance) নতুন বা দূরের chef-এর বেলায় ভাঙা দেখায়।

## Home

| # | সমস্যা | কেন সমস্যা | সমাধান | Status |
|---|---|---|---|---|
| H1 | Header অনেক লম্বা (দুই সারি + greeting + ১২৮dp orbit + search) | প্রথম screen-এর প্রায় অর্ধেক header, তার নিচে বড় banner। খাবার বা chef দেখতে scroll করতে হয়। | Orbit ১১২dp, spacing কমানো। | ✅ Fixed |
| H2 | Header-এর গোল button ৪৪dp | Android-এর touch target guideline ৪৮dp। | ৪৮dp করা। | ✅ Fixed |
| H3 | "See all" ভরাট লাল pill | প্রতিটা section-এ একটা করে ভারী লাল button, চোখ খাবার থেকে সরে যায়। Primary action মনে হয়, অথচ secondary। | Primary রঙের হালকা text link + ছোট arrow। | ✅ Fixed |
| H4 | Header gradient-এর রং ঘুরে ঘুরে লাল থেকে কমলায় যায় | এক screenshot-এ লাল, আরেকটায় কমলা। Brand রং স্থির মনে হয় না। | রং-বদল শুধু theme-এর দুই brand রঙের মধ্যে রাখা, বা বন্ধ করা। | 🕓 Later |
| H5 | "Today's pick" banner আর "AI Recommended" একই meal দুবার দেখায় | Duplicate content, জায়গা নষ্ট। | Banner-এ থাকা meal নিচের list থেকে বাদ দেওয়া। | 🕓 Later |
| H6 | Profile button শুধু একটা generic person icon | ব্যক্তিগত মনে হয় না। | User-এর নামের প্রথম অক্ষর দেখানো। | 🕓 Later |
| H7 | Points chip-এ "🍙 ⚡0" | নতুন user বোঝে না এটা কী। | ০ point হলে ছোট করে দেখানো বা label দেওয়া। | ❓ Owner |
| H8 | Chat icon সবুজ, header লাল/কমলা | সবুজ-লাল একসাথে খিটখিটে লাগে। তবে WhatsApp-এর মতো সবুজ রাখা আগের সিদ্ধান্ত। | এখনকার মতো রাখা। Owner চাইলে সাদা icon + ছোট সবুজ dot। | ❓ Owner |

## Chef card (Home / list)

| # | সমস্যা | কেন সমস্যা | সমাধান | Status |
|---|---|---|---|---|
| C1 | Distance "2635.8 km" | দশমিকসহ বড় সংখ্যা পড়তে কষ্ট, ভাঙা data মনে হয়। | ১০ km-এর বেশি হলে পূর্ণ সংখ্যা, হাজারে comma ("2,636 km")। | ✅ Fixed |
| C2 | নতুন chef-এর rating "0.0 (0)" | ০ star মানে খারাপ chef মনে হয়, আসলে review-ই হয়নি। | Review না থাকলে "New" badge। | ❓ Owner (নতুন লেখা) |
| C3 | Kitchen photo না থাকলে বড় gradient box-এ একটা emoji | Card-এর ৬০% জায়গা খালি লাগে। | Chef-কে photo দেওয়ার nudge আগেই আছে। Placeholder-এ সূক্ষ্ম pattern দেওয়া যায়। | 🕓 Later |

## Meal Details

| # | সমস্যা | কেন সমস্যা | সমাধান | Status |
|---|---|---|---|---|
| M1 | Dietary / Allergen chip-গুলো চাপা যায় (ripple) কিন্তু কিছু হয় না | Button-এর মতো দেখায়, ভুল affordance। | Click ছাড়া সাধারণ pill। | ✅ Fixed |
| M2 | Allergen না থাকলেও খালি section বা "None" chip | কোনো তথ্য নেই, জায়গা নষ্ট। | Allergen না থাকলে পুরো section লুকানো। | ✅ Fixed |
| M3 | "5 plates left today" লাল লেখা | লাল রং error-এর মতো দেখায়। | সাধারণত tertiary (amber) রং, ৩ বা তার কম থাকলে লাল। | ✅ Fixed |
| M4 | কোন chef-এর রান্না, rating কত, তা এই page-এ নেই | Order-এর আগে সবচেয়ে জরুরি তথ্যের একটা। | নামের নিচে kitchen নাম + rating, tap করলে Chef Detail। | 🕓 Later |
| M5 | Description-এ পুরোনো default "নতুন যোগ করা মিল" | পুরোনো build-এর data। Code-এ আর আসে না। | Chef Menu Manager থেকে বদলাতে পারে। | ❓ Owner (data) |

## Role Selection

| # | সমস্যা | কেন সমস্যা | সমাধান | Status |
|---|---|---|---|---|
| R1 | Subtitle "You can change this later" সত্যি না | App-এ role বদলানোর কোনো উপায় নেই। `firestore.rules`-ও user-কে নিজের role বদলাতে দেয় না। ভুল প্রতিশ্রুতি trust নষ্ট করে। | লেখা বদলানো বা subtitle সরানো। | ❓ Owner (লেখা) |
| R2 | Selection ring + বাকিটা dim | পরিষ্কার, ভালো কাজ করছে। | — | ভালো |

## Cart / সাধারণ

| # | সমস্যা | কেন সমস্যা | সমাধান | Status |
|---|---|---|---|---|
| G1 | Cart-এ meal-এর ছবি | Emoji-র বদলে এখন আসল ছবি। | — | ভালো |
| G2 | পুরো app গাঢ় লাল/বাদামি background-এ গাঢ় card | Card আর background-এর পার্থক্য কম, সব একরকম লাগে। | Card-এ একটু হালকা surface বা সূক্ষ্ম border। Light mode-ও যাচাই করা দরকার। | 🕓 Later |
| G3 | Emoji লেখার ভেতরে ("🍙 ⚡0", "✨ Today's pick") | TalkBack emoji-র নাম পড়ে শোনায়। | Emoji-গুলোকে আলাদা, screen reader থেকে লুকানো element করা। | 🕓 Later |

## Motion: Home-এর plate orbit অন্য কোথাও (owner-এর অনুরোধ)

Orbit-টা কেন ভালো লাগে: এটা "ঘুরে, থামে, আবার ঘুরে" (step-রে চলে) আর সামনের item-টা বড় হয়।
তাই চোখ একবারে একটা জিনিসে যায়। এটা app-এর signature motion, তাই এখানে নিয়ম হলো:
**একই screen-এ দুটো orbit না, আর অর্থ না থাকলে orbit না।** প্রতিটা সম্ভাব্য জায়গা যাচাই:

| জায়গা | সিদ্ধান্ত | কেন |
|---|---|---|
| Rewards-এর rank badge | ✅ যোগ করা হয়েছে | ৫টা rank একটা ring-এ। Screen খুললে Rookie থেকে আপনার rank পর্যন্ত এক ধাপ এক ধাপ করে ঘুরে, আপনার rank সামনে এসে থামে, তারপর badge pop আর star burst একবার। Motion-টা progress বোঝায়, তারপর থেমে থাকে। |
| Splash | ❌ বাদ | Screen মাত্র ~১.৫ সেকেন্ড থাকে, এক ধাপও পুরো দেখা যাবে না। Ripple-ই যথেষ্ট। |
| Login / Signup | ❌ বাদ | Form পূরণের সময় নড়াচড়া মনোযোগ কাড়ে। Home-এর আগে orbit দেখালে Home-এর প্রথম impression-ও কমে যায়। |
| Home-এর "No chefs nearby" empty state | ❌ বাদ | একই screen-এর উপরেই orbit আছে, দুটো হলে বেশি হয়ে যায়। |
| Chef Detail (রান্নার ছবির ring) | ❌ এখন না | Cover photo আর menu list-ই এখানে আসল content। Orbit-এ ছোট ছবি meal list-এর সাথে duplicate হয়। |
| Order tracking | ❌ বাদ | প্রতিটা stage-এর নিজস্ব Lottie আছে, যা stage-এর মানে বোঝায়। Orbit সেখানে কোনো তথ্য দেয় না। |

**নিজের যাচাই (Rewards orbit):** দরকার আছে, কারণ আগে badge একা একটা emoji ছিল আর ladder নিচে
আলাদা list-এ। এখন screen-এর শুরুতেই চোখে পড়ে, পরের rank কোনটা আর কত দূরে। সময় সীমিত:
সর্বোচ্চ rank-এর জন্য ৪ ধাপ × ~০.৮ সেকেন্ড ≈ ৩.৫ সেকেন্ড, তারপর সব থেমে যায়। ঝুঁকি হলো,
বারবার Rewards খুললে প্রতিবার পুরো ঘোরা দেখতে হয়। ব্যবহারে বিরক্ত লাগলে পরের ধাপে শুধু প্রথমবার
বা rank বাড়ার পর পুরো animation দেখানো হবে (🕓 Later, M-R1)।

## পরের ধাপ

1. 🕓 Later-এর কাজগুলো এই ক্রমে: H5 → M4 → H4 → G2 → H6 → C3 → G3।
2. ❓ Owner-এর ৫টা সিদ্ধান্ত (H7, H8, C2, M5, R1) পেলে সেগুলো করা হবে।
3. নতুন build-এর screenshot পেলে এই review আবার update করা হবে।
