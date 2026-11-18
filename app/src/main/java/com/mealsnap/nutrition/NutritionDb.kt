package com.mealsnap.nutrition

import com.mealsnap.ai.FoodAnalysis
import com.mealsnap.ai.FoodItem
import java.text.Normalizer
import kotlin.math.min
import kotlin.math.roundToInt

/** One food with nutrition per 100 g and a typical serving. */
data class FoodEntry(
    val name: String,
    val aliases: List<String>,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodiumMg: Double,
    val servingG: Int,
    val servingLabel: String,
    /** True for the catch-all entries used when nothing specific matches. */
    val generic: Boolean = false,
)

/** How well a recognised label matched a [FoodEntry]. */
data class Match(val entry: FoodEntry, val score: Double, val kind: Kind) {
    enum class Kind { EXACT, PARTIAL, CATEGORY, GENERIC }

    /** Multiplier applied to the classifier's probability when ranking candidates. */
    val trust: Double
        get() = when (kind) {
            Kind.EXACT, Kind.PARTIAL -> 1.0
            Kind.CATEGORY -> 0.7
            Kind.GENERIC -> 0.35
        }
}

object NutritionDb {

    val entries: List<FoodEntry> by lazy { parse(NutritionData.RAW) }

    private val genericDessert: FoodEntry by lazy { entries.first { it.generic && it.name == "Dessert" } }
    private val genericDish: FoodEntry by lazy { entries.first { it.generic && it.name == "Savoury dish" } }

    private val stopWords = setOf("with", "and", "of", "the", "in", "a", "an", "on", "style", "fresh", "homemade")

    internal fun parse(raw: String): List<FoodEntry> =
        raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val generic = line.startsWith("!")
            val cols = line.removePrefix("!").split("|")
            require(cols.size == 11) { "Bad nutrition row (${cols.size} columns): $line" }
            FoodEntry(
                name = cols[0].trim(),
                aliases = cols[1].split(",").map { it.trim() }.filter { it.isNotEmpty() },
                kcal = cols[2].toDouble(),
                protein = cols[3].toDouble(),
                carbs = cols[4].toDouble(),
                fat = cols[5].toDouble(),
                fiber = cols[6].toDouble(),
                sugar = cols[7].toDouble(),
                sodiumMg = cols[8].toDouble(),
                servingG = cols[9].toInt(),
                servingLabel = cols[10].trim(),
                generic = generic,
            )
        }

    fun byName(name: String): FoodEntry? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Lower-case, letters and digits only, light singularisation. Same function for both sides of a match. */
    internal fun tokens(text: String): List<String> =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "") // "bánh" -> "banh"
            .lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(" ")
            .filter { it.isNotEmpty() }
            .map { singular(it) }

    private fun singular(token: String): String = when {
        token.length > 4 && token.endsWith("ies") -> token.dropLast(3) + "y"
        token.length > 3 && token.endsWith("s") && !token.endsWith("ss") -> token.dropLast(1)
        else -> token
    }

    private fun keys(entry: FoodEntry): List<List<String>> =
        (listOf(entry.name) + entry.aliases).map { tokens(it) }.filter { it.isNotEmpty() }

    private fun containsRun(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        return (0..haystack.size - needle.size).any { i -> haystack.subList(i, i + needle.size) == needle }
    }

    /**
     * Foods to suggest while the user types a name. Prefix matching, so "chick" finds "Chicken curry".
     * Generic fallback entries are never suggested.
     */
    fun search(query: String, limit: Int = 6): List<FoodEntry> {
        val q = tokens(query)
        if (q.isEmpty()) return emptyList()
        val joined = q.joinToString(" ")

        return entries.asSequence()
            .filter { !it.generic }
            .mapNotNull { entry ->
                var best = 0
                for (key in keys(entry)) {
                    val keyJoined = key.joinToString(" ")
                    val s = when {
                        keyJoined == joined -> 100
                        keyJoined.startsWith(joined) -> 90
                        q.all { qt -> key.any { it.startsWith(qt) } } -> 80 - min(15, key.size - q.size)
                        keyJoined.contains(joined) -> 70
                        else -> 0
                    }
                    if (s > best) best = s
                }
                if (best > 0) entry to best else null
            }
            .sortedWith(compareByDescending<Pair<FoodEntry, Int>> { it.second }.thenBy { it.first.name.length })
            .map { it.first }
            .take(limit)
            .toList()
    }

    /** Category fallbacks, checked in order: if any token of the label starts with a keyword, use that food. */
    private val categories: List<Pair<List<String>, String>> = listOf(
        listOf("biryani", "pulao") to "Biryani",
        listOf("curry", "masala", "korma", "gravy") to "Chicken curry",
        listOf("burger") to "Hamburger",
        listOf("pizza") to "Pizza",
        listOf("sandwich", "sub", "toast") to "Sandwich",
        listOf("noodle", "ramen", "udon", "soba", "pho") to "Chow mein",
        listOf("pasta", "spaghetti", "macaroni", "penne", "linguine", "fettuccine") to "Pasta",
        listOf("rice") to "White rice",
        listOf("soup", "broth", "chowder", "bisque") to "Tomato soup",
        listOf("salad", "slaw") to "Garden salad",
        listOf("cake", "torte", "gateau") to "Cake",
        listOf("pie", "tart") to "Apple pie",
        listOf("cookie", "biscuit") to "Cookie",
        listOf("ice cream", "gelato", "sundae") to "Ice cream",
        listOf("pudding", "custard", "mousse") to "Pudding",
        listOf("bread", "bun", "loaf", "roll", "baguette", "focaccia") to "White bread",
        listOf("sausage", "wurst", "salami") to "Sausage",
        listOf("steak", "beef") to "Steak",
        listOf("chicken") to "Grilled chicken",
        listOf("pork", "ham") to "Pork chop",
        listOf("lamb", "mutton", "goat") to "Mutton curry",
        listOf("fish", "cod", "tuna", "trout", "haddock") to "Fish fillet",
        listOf("shrimp", "prawn", "crab", "lobster", "oyster", "mussel", "clam", "squid") to "Shrimp",
        listOf("egg") to "Boiled egg",
        listOf("potato") to "Roasted potatoes",
        listOf("dumpling", "gyoza", "momo", "wonton") to "Dumplings",
        listOf("wrap", "burrito", "taco") to "Wrap",
        listOf("kebab", "kabab", "skewer", "satay") to "Kebab",
        listOf("cheese") to "Cheese",
        listOf("juice", "smoothie") to "Smoothie",
        listOf("fruit", "berry") to "Fruit salad",
        // Regional dishes named in other languages
        listOf("nasi", "arroz", "gohan", "khao", "bap", "paella") to "Fried rice",
        listOf("caldo", "tang", "guk", "jjigae", "chorba", "ciorba", "soto", "kaeng", "sopa", "sup", "gazpacho", "goulash", "jeongol", "shabu", "tom") to "Lentil soup",
        listOf("mie", "mee", "mein", "guksu", "fun", "pancit") to "Chow mein",
        listOf("pollo", "ayam", "gai", "dak", "galinha", "poulet", "kai") to "Grilled chicken",
        listOf("carne", "galbi", "bulgogi", "daging") to "Steak",
        listOf("torta", "bolo", "pastel", "banh", "kue", "khanom", "tteok", "pitha", "ppang", "pane", "bing") to "Pastry",
        listOf("tahu", "tofu", "tempeh") to "Tofu",
        listOf("queso", "paneer", "chhena") to "Cheese",
        listOf("huevo", "gyeran", "telur") to "Boiled egg",
        listOf("camarao", "gamba", "udang", "tom yum") to "Shrimp",
        listOf("bao", "siu", "har", "gow", "dim sum") to "Dumplings",
        listOf("cuon", "cha gio") to "Spring rolls",
        listOf("candy", "sweet", "dessert", "pastry", "donut", "doughnut", "fudge", "caramel", "halva", "halwa") to "!Dessert",
    )

    /**
     * Maps a label from the food classifier (or a name typed by the user) to the closest food.
     * Always returns something: unknown dishes fall back to a generic savoury dish.
     */
    fun match(label: String): Match {
        val q = tokens(label).filter { it !in stopWords }
        if (q.isEmpty()) return Match(genericDish, 0.0, Match.Kind.GENERIC)

        var best: Match? = null
        for (entry in entries) {
            if (entry.generic) continue
            for (key in keys(entry)) {
                val k = key.filter { it !in stopWords }
                if (k.isEmpty()) continue
                val score = when {
                    q == k -> 1.0
                    q.toSet() == k.toSet() -> 0.97
                    // A one-word key in the middle of a longer label is usually a side ingredient
                    // ("cream of celery chowder" is a soup, not celery), so it only counts as a weak hint.
                    containsRun(q, k) && k.size == 1 && q.size > 1 && q.last() != k[0] -> 0.45
                    containsRun(q, k) -> 0.8 + 0.04 * min(k.size, 3)
                    containsRun(k, q) -> 0.75
                    else -> {
                        val shared = q.toSet().intersect(k.toSet()).size
                        if (shared == 0) 0.0 else 0.5 * shared / maxOf(q.size, k.size)
                    }
                }
                if (score > (best?.score ?: 0.0)) {
                    best = Match(
                        entry,
                        score,
                        if (score >= 0.97) Match.Kind.EXACT else Match.Kind.PARTIAL,
                    )
                }
            }
        }
        if (best != null && best.score >= 0.55) return best

        val phrase = q.joinToString(" ")
        for ((words, target) in categories) {
            val hit = words.any { word ->
                when {
                    " " in word -> phrase.contains(word)
                    // Short words must match a whole token ("egg" must not match "veggie"); longer ones may be a prefix.
                    word.length < 4 -> q.any { it == word }
                    else -> q.any { it.startsWith(word) }
                }
            }
            if (hit) {
                val entry = if (target.startsWith("!")) genericDessert else byName(target)
                if (entry != null) return Match(entry, 0.5, Match.Kind.CATEGORY)
            }
        }
        // A weak hint beats a generic plate.
        if (best != null && best.score >= 0.4) return best.copy(kind = Match.Kind.CATEGORY)
        return Match(genericDish, 0.0, Match.Kind.GENERIC)
    }
}

/** One component of a dish with the amount eaten. */
data class DishItem(val entry: FoodEntry, val grams: Int, val displayName: String = entry.name) {
    private fun per(value: Double): Double = value * grams / 100.0

    val calories: Double get() = per(entry.kcal)
    val protein: Double get() = per(entry.protein)
    val carbs: Double get() = per(entry.carbs)
    val fat: Double get() = per(entry.fat)
    val fiber: Double get() = per(entry.fiber)
    val sugar: Double get() = per(entry.sugar)
    val sodiumMg: Double get() = per(entry.sodiumMg)

    val portionText: String
        get() = if (grams == entry.servingG) "about $grams g (${entry.servingLabel})" else "about $grams g"

    fun toFoodItem() = FoodItem(
        name = displayName,
        portion = portionText,
        calories = calories.roundToInt(),
        proteinG = protein.roundToInt(),
        carbsG = carbs.roundToInt(),
        fatG = fat.roundToInt(),
    )
}

/** A recognised dish built from the nutrition table, so the portion can be rescaled without re-running anything. */
data class Dish(val name: String, val cuisine: String = "", val items: List<DishItem>) {

    fun scaled(factor: Float): Dish =
        copy(items = items.map { it.copy(grams = (it.grams * factor).roundToInt().coerceAtLeast(5)) })

    fun toAnalysis(confidence: String, notes: String, visualCues: List<String> = emptyList()): FoodAnalysis {
        val calories = items.sumOf { it.calories }
        return FoodAnalysis(
            isFood = true,
            dishName = name,
            cuisine = cuisine,
            items = items.map { it.toFoodItem() },
            totalCalories = ((calories / 5.0).roundToInt() * 5),
            proteinG = items.sumOf { it.protein }.roundToInt(),
            carbsG = items.sumOf { it.carbs }.roundToInt(),
            fatG = items.sumOf { it.fat }.roundToInt(),
            fiberG = items.sumOf { it.fiber }.roundToInt(),
            sugarG = items.sumOf { it.sugar }.roundToInt(),
            sodiumMg = items.sumOf { it.sodiumMg }.roundToInt(),
            visualCues = visualCues,
            confidence = confidence,
            notes = notes,
        )
    }

    companion object {
        /** A single food at its typical serving. */
        fun single(entry: FoodEntry, name: String = entry.name, grams: Int = entry.servingG) =
            Dish(name = name, items = listOf(DishItem(entry, grams)))
    }
}
