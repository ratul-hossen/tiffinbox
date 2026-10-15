package com.ratulhossen.tiffinbox.domain.ml

import android.content.Context
import kotlin.math.exp
import kotlin.math.sqrt
import org.json.JSONObject

data class SearchIntent(
    /** Predicted probability the query is asking for vegetarian food (0..1). */
    val vegProbability: Double,
    /** Predicted price tier, or null if the model couldn't load / query was empty. */
    val priceTier: PriceTier?,
    /** Confidence (softmax probability) in [priceTier]. */
    val priceTierConfidence: Double
)

enum class PriceTier { BUDGET, MID, PREMIUM }

/**
 * On-device "natural-language search intent" model - predicts veg-intent
 * and price-tier-intent straight from a free-text query, trained offline
 * on a public Swiggy restaurant-name dataset (see
 * ml/train_search_model.py and ml/MODEL_REPORT.docx at the repo root for
 * training code and F1 numbers). Re-implements sklearn's
 * TfidfVectorizer(ngram_range=(1,2)) + LogisticRegression scoring by hand
 * in plain Kotlin arithmetic - no TensorFlow Lite runtime, no server.
 *
 * Trained on restaurant *names* (the closest free-text field the source
 * dataset has to a search query), so it works best on short English-ish
 * food phrases ("cheap veg food", "pizza") - a query entirely in Bangla
 * script won't tokenize against this (English-vocabulary) model and the
 * caller gets back a neutral/absent result rather than a wrong guess.
 */
object MlSearchIntentModel {
    private var tokenIndex: Map<String, Int> = emptyMap()
    private var idf: DoubleArray = DoubleArray(0)
    private var vegWeights: DoubleArray = DoubleArray(0)
    private var vegBias: Double = 0.0
    private var priceWeights: Array<DoubleArray> = emptyArray()
    private var priceBias: DoubleArray = DoubleArray(0)
    private var loaded = false

    private val wordRegex = Regex("""\w{2,}""")

    fun ensureLoaded(context: Context) {
        if (loaded) return
        try {
            val json = context.assets.open("ml/search_model.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val tokenizer = obj.getJSONObject("tokenizer")
            val tokens = tokenizer.getJSONArray("tokens").toStringList()
            tokenIndex = tokens.withIndex().associate { (i, tok) -> tok to i }
            idf = tokenizer.getJSONArray("idf").toDoubleArray()

            val heads = obj.getJSONObject("heads")
            val vegHead = heads.getJSONObject("veg_intent")
            vegWeights = vegHead.getJSONArray("weights").toDoubleArray()
            vegBias = vegHead.getDouble("bias")

            val priceHead = heads.getJSONObject("price_tier_intent")
            val weightsArr = priceHead.getJSONArray("weights")
            priceWeights = Array(weightsArr.length()) { i -> weightsArr.getJSONArray(i).toDoubleArray() }
            priceBias = priceHead.getJSONArray("bias").toDoubleArray()

            loaded = true
        } catch (e: Exception) {
            loaded = false
        }
    }

    /** Returns null if the model isn't loaded or the query has no tokens the model recognizes. */
    fun predict(query: String): SearchIntent? {
        if (!loaded || query.isBlank()) return null

        val words = wordRegex.findAll(query.lowercase()).map { it.value }.toList()
        if (words.isEmpty()) return null

        val ngrams = buildList {
            addAll(words)
            for (i in 0 until words.size - 1) add("${words[i]} ${words[i + 1]}")
        }

        // raw tf-idf per vocab dimension actually present in this query
        val present = HashMap<Int, Double>()
        for (gram in ngrams) {
            val idx = tokenIndex[gram] ?: continue
            val raw = present.getOrDefault(idx, 0.0) + idf[idx]
            present[idx] = raw
        }
        if (present.isEmpty()) return null

        val norm = sqrt(present.values.sumOf { it * it })
        if (norm == 0.0) return null
        val normalized = present.mapValues { (_, v) -> v / norm }

        var vegZ = vegBias
        for ((idx, v) in normalized) vegZ += vegWeights[idx] * v
        val vegProbability = 1.0 / (1.0 + exp(-vegZ))

        val classZ = DoubleArray(priceWeights.size) { k ->
            var z = priceBias[k]
            for ((idx, v) in normalized) z += priceWeights[k][idx] * v
            z
        }
        val maxZ = classZ.max()
        val expZ = classZ.map { exp(it - maxZ) }
        val sumExp = expZ.sum()
        val probs = expZ.map { it / sumExp }
        val bestIdx = probs.indices.maxByOrNull { probs[it] } ?: 0

        return SearchIntent(
            vegProbability = vegProbability,
            priceTier = PriceTier.entries.getOrNull(bestIdx),
            priceTierConfidence = probs[bestIdx]
        )
    }
}
