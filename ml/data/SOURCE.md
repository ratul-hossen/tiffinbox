# Dataset source

**Swiggy Restaurant Dataset** (India-wide, real production listings from
Swiggy, one of the largest food-delivery platforms in India - the closest
publicly available analogue to Foodpanda-style platform data, and the
same kind of dataset this project's recommendation/search models were
asked to be trained on).

- Repository: https://github.com/aaditya29/swiggy-dataset-project
- Commit pinned for reproducibility: `92141a71508798ea3d1439794610520483b7a45e`
  (2024-02-25)
- File: `Analysis/swiggy.csv`
- Columns: Restaurant Name, Cuisine, Rating, Number of Ratings, Average
  Price, Number of Offers, Offer Name, Area, Pure Veg, Location
- Raw rows: 659,837 (many are malformed/multi-line due to the `Offer Name`
  column embedding literal newlines); `pandas.read_csv(..., on_bad_lines="skip")`
  yields 140,657 clean rows, covering 581 distinct locations across India.

Kaggle and HuggingFace (the more commonly cited mirrors for datasets like
this) were not reachable from this project's build/training environment
(network policy), so this GitHub mirror was used instead - same
real-world Swiggy data, verified row-for-row identical in structure to
the commonly-cited Kaggle `ashishjangra27/swiggy-restaurants-dataset`.

## Reproducing the download

```bash
git clone --depth 1 https://github.com/aaditya29/swiggy-dataset-project.git /tmp/swiggy_src
mkdir -p ml/data
cp /tmp/swiggy_src/Analysis/swiggy.csv ml/data/swiggy_restaurants.csv
```

Then run `python3 ml/train_recommend_model.py` and
`python3 ml/train_search_model.py` from the repo root (needs
`pandas`, `numpy`, `scikit-learn` - `pip install pandas numpy scikit-learn`).
Each script prints its metrics and writes its exported model JSON to
`ml/export/`, which then gets copied to `app/src/main/assets/ml/` for the
app to actually load (see `ml/MODEL_REPORT.docx` for the current
numbers and `app/src/main/java/.../domain/ml/` for the Kotlin inference
code that reads these files).
