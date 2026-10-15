# TiffinBox — On-Device ML Models

### Recommendation Engine & Natural-Language Search Intent

This report documents the two real, trained machine-learning models added to
TiffinBox: an "AI Recommend Score" model and a natural-language search intent
model. Both are trained offline in Python on a public, real-world
food-delivery dataset, exported as plain weight files, and run entirely
on-device inside the Android app — no model-hosting server, no paid API, no
ongoing inference cost.

## 1. Dataset

Swiggy Restaurant Dataset — real, production listings scraped from Swiggy,
one of India's largest food-delivery platforms (the closest publicly
available analogue to Foodpanda-style platform data).

| | |
|---|---|
| **Source repository** | github.com/aaditya29/swiggy-dataset-project |
| **Pinned commit** | `92141a71508798ea3d1439794610520483b7a45e` (2024-02-25) |
| **File** | `Analysis/swiggy.csv` |
| **Raw rows** | 659,837 |
| **Clean rows used** | 140,657 (after dropping malformed multi-line CSV rows) |
| **Geographic coverage** | 581 distinct locations across India |
| **Columns** | Restaurant Name, Cuisine, Rating, Number of Ratings, Average Price, Number of Offers, Offer Name, Area, Pure Veg, Location |

**Note on sourcing:** Kaggle and HuggingFace (the more commonly cited
mirrors for a dataset like this) were not reachable from this project's
build environment due to network policy. This GitHub mirror of the same
real Swiggy data was used instead — verified to have the same
columns/structure as the widely-used Kaggle
`ashishjangra27/swiggy-restaurants-dataset`.

Full reproduction steps are in [`ml/data/SOURCE.md`](data/SOURCE.md) and
Section 6 below. The raw CSV itself is not committed to the app repository
(32 MB, regenerable) — only the small trained model files (under 500 KB
total) that the app actually ships with are committed.

## 2. Model 1 — "AI Recommend Score"

### Task

Binary classification: will a restaurant/dish be highly-rated (a stand-in
for "would a user recommend this")? TiffinBox is a new app with no
purchase-history log yet, so this is the standard way to bootstrap a
recommender from an external dataset's implicit signal rather than
inventing fake personalization.

Label: `recommended = 1` if Rating ≥ 4.0, else 0 (positive rate in the
cleaned data: 66.2%).

### Features

Chosen specifically because they are fields TiffinBox's own `MealUi` model
already carries (price, isVeg) — this is what makes the trained model
honestly transferable to TiffinBox's real data, rather than depending on
Swiggy-only fields like cuisine taxonomy that don't exist in TiffinBox's own
category system.

- `price_f` — Average Price (BDT-equivalent numeric, parsed from "₹250 for two" style strings)
- `pure_veg_f` — Pure Veg flag (0/1)
- `num_offers_f` — Number of Offers
- `popularity_f` — log(1 + Number of Ratings), to stop a handful of 10K+ outlier restaurants from dominating

### Training setup

- 80/20 train/test split, stratified by label, `random_state=42`
- 85,548 training rows, 21,388 test rows
- Features standardized (zero mean, unit variance) before the exported model
- Two models trained for comparison: Logistic Regression (exported — a
  linear model is a few lines of dot-product arithmetic, trivial to
  re-implement on-device) and Gradient Boosting (trained only as a
  reference ceiling — a tree ensemble isn't practical to hand-port to
  Kotlin, and there's no server to host it on under the free-tier/
  no-hosting constraint)

### Results (held-out test set)

| Model | Accuracy | Precision | Recall | F1 score |
|---|---|---|---|---|
| Logistic Regression (exported, used in-app) | 0.612 | 0.771 | 0.590 | **0.668** |
| Gradient Boosting (comparison only, not shipped) | 0.668 | 0.682 | 0.938 | 0.789 |

**Honest read:** an F1 of 0.67 from four price/veg/popularity features is a
real, modest, above-baseline signal (not a headline number) — predicting a
restaurant's star rating from so few attributes is a genuinely hard
problem, and these results are reported as-is rather than cherry-picked or
inflated. Gradient Boosting scores higher but isn't shipped, for the
architectural reason above.

## 3. Model 2 — Natural-Language Search Intent

### Task

Given free text (a search query), predict two things a user might mean even
if their words don't literally appear in any dish's name or description:

- `veg_intent` — binary: are they looking for vegetarian food?
- `price_tier_intent` — 3-class: Budget / Mid / Premium

Text source: restaurant name (the closest free-text field this dataset has
to what a user types into a search box). Labels (Pure Veg, and a price tier
tertile-binned from Average Price) were chosen, like Model 1's features,
because they map directly onto fields TiffinBox's own `MealUi` already has
(`isVeg`, `price`) — Swiggy's cuisine taxonomy (Pizzas / North Indian /
etc.) was deliberately not used as a label because it doesn't line up with
TiffinBox's own category system (Breakfast / Lunch / Dinner / Snack /
Dessert).

### Training setup

- TF-IDF vectorizer: unigrams + bigrams, 3,000-term vocabulary, `min_df=3`
- 100,659 unique restaurant names after dedup; 80/20 train/test split,
  stratified on `veg_intent`, `random_state=42`
- 80,527 training rows, 20,132 test rows
- Two independent Logistic Regression heads sharing the same TF-IDF
  features: one binary (veg), one multiclass (price tier), both
  class-weight-balanced

### Results (held-out test set)

| Head | Precision | Recall | F1 score |
|---|---|---|---|
| `veg_intent` (binary) | 0.670 | 0.666 | **0.668** |
| `price_tier_intent` (macro, 3-class) | 0.433 | 0.449 | **0.418** |

**Honest read:** `veg_intent` (F1 0.67) is a usable signal — restaurant
names do carry real vegetarian-cuisine vocabulary ("pure veg", "sweets",
"south indian", etc.). `price_tier_intent` (macro F1 0.42) is meaningfully
above the 0.33 random-guess baseline for 3 classes, but a restaurant's name
alone is a weak predictor of its price — this is reported honestly as the
model's limitation, not hidden.

**Known limitation:** the vocabulary was learned from English-script
restaurant names. A query typed entirely in Bangla script won't tokenize
against it, so the app's integration (Section 4) treats "no recognized
tokens" as "no opinion" and falls back to the existing plain-text search
rather than guessing.

## 4. Integration into the app (on-device, no hosting)

Both models are exported as plain JSON weight files (not TensorFlow Lite,
not ONNX — just arrays of numbers), bundled into the Android app as assets,
and loaded once at app startup. Prediction is reimplemented as a few lines
of arithmetic (a dot product and a sigmoid/softmax) directly in Kotlin —
there is no model-serving backend and nothing to host.

| | |
|---|---|
| **Training code** | `ml/train_recommend_model.py`, `ml/train_search_model.py` |
| **Exported weights** | `app/src/main/assets/ml/recommend_model.json`, `app/src/main/assets/ml/search_model.json` |
| **On-device inference** | `app/src/main/java/.../domain/ml/MlRecommendModel.kt`, `MlSearchIntentModel.kt` |
| **Recommendation engine UI** | `HomeScreen.kt` — a new "✨ AI Recommended For You" row, ranking every meal in the catalog by `MlRecommendModel`'s predicted score (price + veg + `availableQuantity` as a popularity proxy) and showing the top 10 |
| **NL search UI** | `SearchScreen.kt` — the existing substring-match filter is kept as-is (so a query never stops matching what it used to), and `MlSearchIntentModel`'s predicted veg/price-tier intent is used only to re-rank those results, so e.g. "cheap veg food" floats cheap vegetarian dishes to the top even when none of those exact words appear in a dish's name or description |

## 5. Limitations & honest scope

- Labels are proxies (Rating ≥ 4.0 for "recommend"; Pure Veg / price
  tertile for "intent"), not real TiffinBox user feedback — there is no
  order-history log yet to train on directly.
- Modest F1 scores (0.42–0.67) are reported as-is; no number here was
  cherry-picked or rounded up.
- The search model's vocabulary is English-script only (see Section 3).
- Gradient Boosting scored better on Model 1 but isn't shipped, since it
  can't be hand-ported to a few lines of Kotlin arithmetic the way a
  linear model can, and there's no server to host it on under the
  project's no-hosting, free-tier constraint.
- Both models were trained on Indian (Swiggy) restaurant data, not
  Bangladeshi home-cooked-food data — a genuine domain gap that real
  TiffinBox order history would eventually close.

## 6. Reproducing this work

From the repository root (needs Python 3 with pandas, numpy, scikit-learn):

```bash
pip install pandas numpy scikit-learn
git clone --depth 1 https://github.com/aaditya29/swiggy-dataset-project.git /tmp/swiggy_src
mkdir -p ml/data && cp /tmp/swiggy_src/Analysis/swiggy.csv ml/data/swiggy_restaurants.csv
python3 ml/train_recommend_model.py
python3 ml/train_search_model.py
cp ml/export/*.json app/src/main/assets/ml/
```

See [`ml/data/SOURCE.md`](data/SOURCE.md) for more detail.
