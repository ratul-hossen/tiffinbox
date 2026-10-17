package com.ratulhossen.tiffinbox.domain.ml

import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.model.MealUi
import kotlin.math.max
import kotlin.math.min

/**
 * Machine-Learning assisted & Fuzzy Semantic Search Engine for TiffinBox.
 *
 * Combines:
 * 1. Exact & substring matching (highest confidence).
 * 2. Character N-gram (Bi-gram & Tri-gram) similarity & Levenshtein edit distance for typo tolerance.
 * 3. Cross-lingual Bengali, Banglish & English food synonym dictionary.
 * 4. ML intent scoring (veg preference, price tier from [MlSearchIntentModel]).
 * 5. Proximity and rating signals for kitchen discovery.
 */
object MlSearchMatcher {

    data class ScoredResult<T>(
        val item: T,
        val score: Double,
        val isExactOrPrefix: Boolean,
        val matchLabel: String? = null
    )

    // Synonyms & cross-lingual concepts (Bangla script, transliterated Banglish, English)
    private val FOOD_CONCEPTS: List<Set<String>> = listOf(
        // Rice & Grains
        setOf("rice", "bhat", "vat", "ভাত", "chal", "চাল", "polao", "pulao", "পোলাও", "khichuri", "khicuri", "khichdi", "খিচুড়ি", "খিচুড়ি", "tehari", "teheri", "তেহারি", "biryani", "biriyani", "বিরিয়ানি", "বিরিয়ানি", "kacchi", "kaccho", "কাচ্চি", "fried rice"),
        // Chicken / Poultry
        setOf("chicken", "murgi", "morog", "মুরগি", "চিকেন", "chick"),
        // Beef / Meat
        setOf("beef", "goru", "gorur", "গরু", "গরুর", "বিফ", "meat"),
        // Mutton / Lamb
        setOf("mutton", "khashi", "khasir", "খাসি", "খাসির", "lamb"),
        // Fish & Seafood
        setOf("fish", "mach", "macher", "মাছ", "মাছের", "ilish", "ইলিশ", "hilsha", "rui", "রুই", "katla", "কাতলা", "chingri", "চিংড়ি", "চিংড়ি", "prawn", "shrimp"),
        // Egg
        setOf("egg", "dim", "dime", "ডিম", "ডিমের", "anda"),
        // Bread & Breakfast items
        setOf("bread", "ruti", "roti", "রুটি", "porota", "paratha", "পরোটা", "luchi", "লুচি", "naan", "নান", "nasta", "নাস্তা", "breakfast"),
        // Lentils, Vegetables, Bhorta
        setOf("dal", "daal", "ডাল", "lentil", "lentils", "shobji", "sobji", "সবজি", "veg", "vegetable", "vegetables", "shak", "শাক", "bhorta", "vorta", "ভর্তা", "vaji", "bhaji", "ভাজি", "alu", "aloo", "আলু", "potato", "begun", "বেগুন"),
        // Sweets & Desserts
        setOf("sweet", "sweets", "dessert", "misti", "mishti", "মিষ্টি", "payesh", "পায়েশ", "firni", "ফিরনি", "halwa", "হালুয়া", "rosogolla", "রসগোল্লা", "laddu", "লাড্ডু", "doi", "দই"),
        // Beverages
        setOf("tea", "cha", "চা", "coffee", "কফি", "juice", "জুস", "lacchi", "lassi", "লাচ্ছি", "borhani", "বোরহানি", "drink"),
        // Snacks
        setOf("snack", "snacks", "singara", "সিঙ্গারা", "somosa", "সমুচা", "roll", "রোল", "pakora", "পাকোড়া", "chop", "চপ", "puri", "পুরি")
    )

    private val wordSplitRegex = Regex("""[\s,\-_/]+""")

    /**
     * Searches meals with fuzzy, typo-tolerant, synonym and ML intent matching.
     */
    fun searchMeals(
        query: String,
        allMeals: List<MealUi>,
        selectedCategory: String?,
        vegOnly: Boolean,
        searchIntent: SearchIntent?
    ): List<ScoredResult<MealUi>> {
        val trimmed = query.trim().lowercase()

        val candidateList = allMeals.filter { meal ->
            val matchesCategory = selectedCategory == null || meal.category.equals(selectedCategory, ignoreCase = true)
            val matchesVeg = !vegOnly || meal.isVeg
            matchesCategory && matchesVeg
        }

        if (trimmed.isEmpty()) {
            return candidateList.map { ScoredResult(it, 1.0, isExactOrPrefix = true) }
        }

        val queryWords = trimmed.split(wordSplitRegex).filter { it.length >= 2 }
        val scored = candidateList.mapNotNull { meal ->
            val nameLower = meal.name.lowercase()
            val descLower = meal.description.lowercase()
            val tagsLower = meal.dietaryTags.map { it.lowercase() }
            val catLower = meal.category.lowercase()

            // 1. Exact or Substring match (Confidence: 1.0+)
            val isExactName = nameLower == trimmed
            val isSubstringName = nameLower.contains(trimmed)
            val isExactWordInName = nameLower.split(wordSplitRegex).any { it == trimmed }

            var baseScore = 0.0
            var isExactOrPrefix = false
            var matchReason: String? = null

            if (isExactName || isExactWordInName) {
                baseScore = 1.2
                isExactOrPrefix = true
            } else if (isSubstringName) {
                baseScore = 1.0
                isExactOrPrefix = true
            } else if (descLower.contains(trimmed) || tagsLower.any { it.contains(trimmed) } || catLower.contains(trimmed)) {
                baseScore = 0.85
                isExactOrPrefix = true
            } else {
                // 2. Fuzzy & Typo Matching (Levenshtein & N-gram)
                val mealNameWords = nameLower.split(wordSplitRegex).filter { it.length >= 2 }
                var maxFuzzy = 0.0
                for (qw in queryWords) {
                    for (mw in mealNameWords) {
                        val sim = calculateSimilarity(qw, mw)
                        if (sim > maxFuzzy) maxFuzzy = sim
                    }
                    // Also check whole name
                    val wholeSim = ngramSimilarity(trimmed, nameLower)
                    if (wholeSim > maxFuzzy) maxFuzzy = wholeSim
                }

                if (maxFuzzy >= 0.70) {
                    baseScore = 0.65 + (maxFuzzy * 0.25)
                    matchReason = "কাছাকাছি মিল"
                } else {
                    // 3. Cross-lingual & Food Concept Synonyms
                    val hasConceptMatch = FOOD_CONCEPTS.any { concept ->
                        val queryMatchesConcept = queryWords.any { it in concept } || trimmed in concept
                        val mealMatchesConcept = mealNameWords.any { it in concept } ||
                            tagsLower.any { it in concept } ||
                            concept.any { nameLower.contains(it) }
                        queryMatchesConcept && mealMatchesConcept
                    }

                    if (hasConceptMatch) {
                        baseScore = 0.75
                        matchReason = "সম্পর্কিত খাবার"
                    }
                }
            }

            if (baseScore <= 0.0) return@mapNotNull null

            // 4. ML Intent bonus
            if (searchIntent != null) {
                // Veg intent bonus
                if (searchIntent.vegProbability > 0.6 && meal.isVeg) {
                    baseScore += 0.15
                }
                // Price tier intent bonus
                searchIntent.priceTier?.let { tier ->
                    val matchesPrice = when (tier) {
                        PriceTier.BUDGET -> meal.price <= 100
                        PriceTier.MID -> meal.price in 101..200
                        PriceTier.PREMIUM -> meal.price > 200
                    }
                    if (matchesPrice) baseScore += 0.10
                }
            }

            ScoredResult(
                item = meal,
                score = baseScore,
                isExactOrPrefix = isExactOrPrefix,
                matchLabel = matchReason
            )
        }

        return scored.sortedByDescending { it.score }
    }

    /**
     * Searches chefs/kitchens with fuzzy matching and distance weighting.
     */
    fun searchChefs(
        query: String,
        allChefs: List<ChefUi>,
        nearbyOnly: Boolean,
        hasRealDistances: Boolean,
        recommendRadiusKm: Double = 10.0
    ): List<ScoredResult<ChefUi>> {
        val trimmed = query.trim().lowercase()

        val candidateList = allChefs.filter { chef ->
            !nearbyOnly || !hasRealDistances || chef.distanceKm <= recommendRadiusKm
        }

        if (trimmed.isEmpty()) {
            return candidateList
                .sortedBy { it.distanceKm }
                .map { ScoredResult(it, 1.0, isExactOrPrefix = true) }
        }

        val queryWords = trimmed.split(wordSplitRegex).filter { it.length >= 2 }

        val scored = candidateList.mapNotNull { chef ->
            val nameLower = chef.kitchenName.lowercase()
            val bioLower = chef.bio.lowercase()
            val specLower = chef.specialty.map { it.lowercase() }

            val isExactName = nameLower == trimmed
            val isSubstring = nameLower.contains(trimmed) || bioLower.contains(trimmed) || specLower.any { it.contains(trimmed) }

            var score = 0.0
            var isExact = false

            if (isExactName) {
                score = 1.2
                isExact = true
            } else if (isSubstring) {
                score = 1.0
                isExact = true
            } else {
                // Fuzzy match on kitchen name or specialties
                val nameWords = nameLower.split(wordSplitRegex).filter { it.length >= 2 }
                var maxFuzzy = 0.0
                for (qw in queryWords) {
                    for (nw in nameWords) {
                        val sim = calculateSimilarity(qw, nw)
                        if (sim > maxFuzzy) maxFuzzy = sim
                    }
                    for (spec in specLower) {
                        val sim = calculateSimilarity(qw, spec)
                        if (sim > maxFuzzy) maxFuzzy = sim
                    }
                }
                if (maxFuzzy >= 0.70) {
                    score = 0.60 + (maxFuzzy * 0.25)
                }
            }

            if (score <= 0.0) return@mapNotNull null

            // Distance bonus: closer kitchens get a slight relevance boost
            if (hasRealDistances && chef.distanceKm > 0.0) {
                val proximityBonus = (1.0 / (1.0 + (chef.distanceKm / 5.0))) * 0.15
                score += proximityBonus
            }

            ScoredResult(
                item = chef,
                score = score,
                isExactOrPrefix = isExact
            )
        }

        return scored.sortedWith(
            compareByDescending<ScoredResult<ChefUi>> { it.score }
                .thenBy { it.item.distanceKm }
        )
    }

    /**
     * Combines Levenshtein similarity and Bigram Dice/Jaccard similarity.
     */
    fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.contains(s2) || s2.contains(s1)) return 0.88

        val lev = levenshteinSimilarity(s1, s2)
        val ngram = ngramSimilarity(s1, s2)
        return max(lev, ngram)
    }

    /**
     * Normalized Levenshtein similarity (0.0 to 1.0).
     */
    fun levenshteinSimilarity(s1: String, s2: String): Double {
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val dist = levenshteinDistance(s1, s2)
        return (1.0 - (dist.toDouble() / maxLen)).coerceIn(0.0, 1.0)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            val c1 = s1[i - 1]
            for (j in 1..n) {
                val c2 = s2[j - 1]
                val cost = if (c1 == c2) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    /**
     * Bigram Sørensen-Dice coefficient similarity.
     */
    fun ngramSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.length < 2 || s2.length < 2) return 0.0

        val ngrams1 = (0 until s1.length - 1).map { s1.substring(it, it + 2) }
        val ngrams2 = (0 until s2.length - 1).map { s2.substring(it, it + 2) }

        val map2 = HashMap<String, Int>()
        for (gram in ngrams2) {
            map2[gram] = map2.getOrDefault(gram, 0) + 1
        }

        var matches = 0
        for (gram in ngrams1) {
            val count = map2.getOrDefault(gram, 0)
            if (count > 0) {
                matches++
                map2[gram] = count - 1
            }
        }

        return (2.0 * matches) / (ngrams1.size + ngrams2.size)
    }
}
