package com.ratulhossen.tiffinbox.domain.ml

import android.content.Context
import kotlin.math.exp
import kotlin.math.ln1p
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device inference for the "AI Recommend Score" model - a logistic
 * regression trained offline on a public Swiggy restaurant dataset (see
 * ml/train_recommend_model.py and ml/MODEL_REPORT.docx at the repo root
 * for training code and F1/precision/recall numbers). Weights are loaded
 * once from assets/ml/recommend_model.json; prediction is just a dot
 * product + sigmoid - no TensorFlow Lite runtime, no server, nothing to
 * host.
 */
object MlRecommendModel {
    private var featureOrder: List<String> = emptyList()
    private var scalerMean: DoubleArray = DoubleArray(0)
    private var scalerScale: DoubleArray = DoubleArray(0)
    private var weights: DoubleArray = DoubleArray(0)
    private var bias: Double = 0.0
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        try {
            val json = context.assets.open("ml/recommend_model.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            featureOrder = obj.getJSONArray("feature_order").toStringList()
            scalerMean = obj.getJSONArray("scaler_mean").toDoubleArray()
            scalerScale = obj.getJSONArray("scaler_scale").toDoubleArray()
            weights = obj.getJSONArray("weights").toDoubleArray()
            bias = obj.getDouble("bias")
            loaded = true
        } catch (e: Exception) {
            // Asset missing or malformed - predict() falls back to a neutral score.
            loaded = false
        }
    }

    /**
     * [avgPrice] average price (BDT) of the chef's meals, [vegFraction] share
     * of their meals that are veg (0..1), [numOffers] the number of
     * currently active coupons usable at that chef (its own + platform-wide,
     * see FirestoreCouponRepository.getActiveOfferCounts), [popularity] the chef's totalOrders.
     *
     * Returns a 0..1 "recommend" probability, or a neutral 0.5 if the model
     * asset failed to load.
     */
    fun predict(avgPrice: Double, vegFraction: Double, numOffers: Double, popularity: Double): Double {
        if (!loaded) return 0.5
        val rawFeatures = mapOf(
            "price_f" to avgPrice,
            "pure_veg_f" to vegFraction,
            "num_offers_f" to numOffers,
            "popularity_f" to ln1p(popularity)
        )
        var z = bias
        for (i in featureOrder.indices) {
            val raw = rawFeatures[featureOrder[i]] ?: 0.0
            val scaled = (raw - scalerMean[i]) / scalerScale[i]
            z += weights[i] * scaled
        }
        return 1.0 / (1.0 + exp(-z))
    }
}

internal fun JSONArray.toDoubleArray(): DoubleArray = DoubleArray(length()) { getDouble(it) }
internal fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
