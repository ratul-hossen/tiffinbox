"""
Model 1: "AI Recommend Score" - binary classifier.

Dataset: Swiggy India restaurant listings (real, public, ~140K rows after
cleaning), see ml/data/SOURCE.md for provenance.

Task: predict whether a restaurant is highly-rated (Rating >= 4.0, used as
a proxy "would recommend" signal - this is the standard way to bootstrap a
recommender when no direct user-feedback log exists yet, which is
TiffinBox's situation as a new app) from attributes that TiffinBox's own
MealUi/ChefUi already carry: price, veg/non-veg, and popularity (order/
rating count). This keeps the feature space honestly transferable to
TiffinBox's own data instead of depending on Swiggy-only fields.

Output: ml/export/recommend_model.json - plain logistic-regression weights
(a dot product + sigmoid), small enough to re-implement as a few lines of
Kotlin arithmetic on-device - no TensorFlow Lite runtime, no server.
"""
import json
import re

import numpy as np
import pandas as pd
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler


def parse_rating(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def parse_num_ratings(value):
    if not isinstance(value, str):
        return 0
    value = value.strip()
    if value == "Too Few Ratings":
        return 0
    match = re.match(r"([\d.]+)(K)?\+?\s*ratings?", value, re.IGNORECASE)
    if not match:
        return 0
    n = float(match.group(1))
    if match.group(2):
        n *= 1000
    return n


def parse_price(value):
    if not isinstance(value, str):
        return None
    match = re.search(r"[\d,]+", value)
    if not match:
        return None
    return float(match.group(0).replace(",", ""))


def main():
    df = pd.read_csv("ml/data/swiggy_restaurants.csv", on_bad_lines="skip")
    print(f"Raw rows: {len(df)}")

    df["rating_f"] = df["Rating"].apply(parse_rating)
    df["price_f"] = df["Average Price"].apply(parse_price)
    df["num_ratings_f"] = df["Number of Ratings"].apply(parse_num_ratings)
    df["pure_veg_f"] = (df["Pure Veg"] == "Yes").astype(int)
    df["num_offers_f"] = pd.to_numeric(df["Number of Offers"], errors="coerce").fillna(0)

    df = df.dropna(subset=["rating_f", "price_f"])
    df = df[(df["price_f"] > 0) & (df["price_f"] < 5000)]
    print(f"Rows after cleaning: {len(df)}")

    # log1p popularity so a handful of 10K+ outlier restaurants don't dominate
    df["popularity_f"] = np.log1p(df["num_ratings_f"])

    df["recommended"] = (df["rating_f"] >= 4.0).astype(int)
    print(f"Positive class (recommended) rate: {df['recommended'].mean():.3f}")

    feature_cols = ["price_f", "pure_veg_f", "num_offers_f", "popularity_f"]
    X = df[feature_cols].to_numpy(dtype=float)
    y = df["recommended"].to_numpy(dtype=int)

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_train)
    X_test_s = scaler.transform(X_test)

    results = {}

    # Model A: Logistic Regression - the one we actually export (linear,
    # trivial to re-implement as a dot product in Kotlin).
    logreg = LogisticRegression(max_iter=1000, class_weight="balanced")
    logreg.fit(X_train_s, y_train)
    pred_lr = logreg.predict(X_test_s)
    results["logistic_regression"] = {
        "accuracy": accuracy_score(y_test, pred_lr),
        "precision": precision_score(y_test, pred_lr),
        "recall": recall_score(y_test, pred_lr),
        "f1": f1_score(y_test, pred_lr),
    }

    # Model B: Gradient Boosting - trained as a comparison point only (not
    # exported - a tree ensemble isn't a few lines of on-device arithmetic
    # the way a linear model is, and the free-tier/no-hosting constraint
    # rules out a server to run it on).
    gbc = GradientBoostingClassifier(random_state=42, n_estimators=100, max_depth=3)
    gbc.fit(X_train, y_train)
    pred_gb = gbc.predict(X_test)
    results["gradient_boosting"] = {
        "accuracy": accuracy_score(y_test, pred_gb),
        "precision": precision_score(y_test, pred_gb),
        "recall": recall_score(y_test, pred_gb),
        "f1": f1_score(y_test, pred_gb),
    }

    for name, m in results.items():
        print(f"\n{name}:")
        for k, v in m.items():
            print(f"  {k}: {v:.4f}")

    # Export the logistic regression as plain weights for Kotlin inference:
    # score = sigmoid(w . ((x - mean) / std) + b)
    export = {
        "model_type": "logistic_regression",
        "feature_order": feature_cols,
        "scaler_mean": scaler.mean_.tolist(),
        "scaler_scale": scaler.scale_.tolist(),
        "weights": logreg.coef_[0].tolist(),
        "bias": float(logreg.intercept_[0]),
        "metrics": {
            "logistic_regression": results["logistic_regression"],
            "gradient_boosting_comparison": results["gradient_boosting"],
        },
        "train_rows": len(X_train),
        "test_rows": len(X_test),
        "dataset": "Swiggy India restaurant listings (public, ~140K cleaned rows)",
    }
    with open("ml/export/recommend_model.json", "w") as f:
        json.dump(export, f, indent=2)
    print("\nExported ml/export/recommend_model.json")


if __name__ == "__main__":
    main()
