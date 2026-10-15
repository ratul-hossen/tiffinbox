"""
Model 2: "Natural-language search intent" classifier.

Dataset: same Swiggy India restaurant listings as Model 1 (see
ml/data/SOURCE.md). Text = restaurant name (the closest free-text field in
this dataset to what a user types into a search box). Labels =
Pure Veg (binary) and a price tier (Budget / Mid / Premium, tertile-binned
from Average Price) - both of which map directly onto fields TiffinBox's
own MealUi already has (isVeg: Boolean, price: Int), unlike Swiggy's
cuisine taxonomy (Pizzas/North Indian/etc.), which doesn't line up with
TiffinBox's own category system (Breakfast/Lunch/Dinner/Snack/Dessert).

At inference time in the app, a user's free-text search query is run
through the same TF-IDF vectorizer + these two classifiers to predict
"are they looking for veg food" / "what price tier" even when their words
don't literally match any dish name - then that's used to re-rank/filter
the existing substring-match results in SearchScreen.

Output: ml/export/search_model.json - TF-IDF vocabulary + idf weights, and
per-class logistic-regression weights for both heads. Small enough to
re-implement in Kotlin as a hashmap lookup + dot product - no TFLite
runtime, no server.
"""
import json
import re

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score, precision_score, recall_score
from sklearn.model_selection import train_test_split


def parse_price(value):
    if not isinstance(value, str):
        return None
    match = re.search(r"[\d,]+", value)
    if not match:
        return None
    return float(match.group(0).replace(",", ""))


def main():
    df = pd.read_csv("ml/data/swiggy_restaurants.csv", on_bad_lines="skip")
    df["price_f"] = df["Average Price"].apply(parse_price)
    df = df.dropna(subset=["price_f", "Restaurant Name"])
    df = df[(df["price_f"] > 0) & (df["price_f"] < 5000)]
    df = df.drop_duplicates(subset=["Restaurant Name"]).reset_index(drop=True)
    print(f"Rows after cleaning/dedup: {len(df)}")

    df["veg_label"] = (df["Pure Veg"] == "Yes").astype(int)

    # Tertile price bins -> 0=Budget, 1=Mid, 2=Premium
    df["price_tier"] = pd.qcut(df["price_f"], q=3, labels=[0, 1, 2]).astype(int)
    print("Price tier distribution:")
    print(df["price_tier"].value_counts().sort_index())

    texts = df["Restaurant Name"].astype(str).tolist()

    vectorizer = TfidfVectorizer(
        max_features=3000,
        ngram_range=(1, 2),
        min_df=3,
        lowercase=True,
        token_pattern=r"(?u)\b\w\w+\b",
    )
    X = vectorizer.fit_transform(texts)

    y_veg = df["veg_label"].to_numpy()
    y_price = df["price_tier"].to_numpy()

    idx_train, idx_test = train_test_split(
        np.arange(len(df)), test_size=0.2, random_state=42, stratify=y_veg
    )
    X_train, X_test = X[idx_train], X[idx_test]

    metrics = {}

    # Head 1: veg-intent (binary)
    veg_clf = LogisticRegression(max_iter=1000, class_weight="balanced")
    veg_clf.fit(X_train, y_veg[idx_train])
    pred_veg = veg_clf.predict(X_test)
    metrics["veg_intent"] = {
        "precision": precision_score(y_veg[idx_test], pred_veg),
        "recall": recall_score(y_veg[idx_test], pred_veg),
        "f1": f1_score(y_veg[idx_test], pred_veg),
    }

    # Head 2: price-tier intent (3-class, one-vs-rest via multinomial)
    price_clf = LogisticRegression(max_iter=1000, class_weight="balanced")
    price_clf.fit(X_train, y_price[idx_train])
    pred_price = price_clf.predict(X_test)
    metrics["price_tier_intent"] = {
        "precision_macro": precision_score(y_price[idx_test], pred_price, average="macro"),
        "recall_macro": recall_score(y_price[idx_test], pred_price, average="macro"),
        "f1_macro": f1_score(y_price[idx_test], pred_price, average="macro"),
    }

    for name, m in metrics.items():
        print(f"\n{name}:")
        for k, v in m.items():
            print(f"  {k}: {v:.4f}")

    vocab = vectorizer.vocabulary_
    idf = vectorizer.idf_.tolist()
    # vocab maps token -> column index; invert so Kotlin can look up by token directly
    vocab_list = sorted(vocab.items(), key=lambda kv: kv[1])
    tokens_in_order = [tok for tok, _ in vocab_list]

    export = {
        "model_type": "tfidf_logreg_multihead",
        "tokenizer": {
            "ngram_range": [1, 2],
            "lowercase": True,
            "tokens": tokens_in_order,
            "idf": idf,
        },
        "heads": {
            "veg_intent": {
                "type": "binary",
                "weights": veg_clf.coef_[0].tolist(),
                "bias": float(veg_clf.intercept_[0]),
            },
            "price_tier_intent": {
                "type": "multiclass",
                "classes": ["budget", "mid", "premium"],
                "weights": price_clf.coef_.tolist(),
                "bias": price_clf.intercept_.tolist(),
            },
        },
        "metrics": metrics,
        "train_rows": int(len(idx_train)),
        "test_rows": int(len(idx_test)),
        "dataset": "Swiggy India restaurant listings (public, deduplicated restaurant names)",
    }
    with open("ml/export/search_model.json", "w") as f:
        json.dump(export, f, indent=2)
    print("\nExported ml/export/search_model.json")


if __name__ == "__main__":
    main()
