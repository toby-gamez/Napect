package com.tkolymp.napect.data.parse

/**
 * Small heuristic ingredient parser. Tries to extract amount (including fractions), unit and name.
 * Not perfect, but handles common patterns like "1 1/2 cup flour", "2 cups sugar", "1/2 tsp salt" and unicode fractions.
 */
object IngredientParser {
    private val unicodeFractionMap = mapOf(
        '½' to "1/2",
        '¼' to "1/4",
        '¾' to "3/4",
        '⅓' to "1/3",
        '⅔' to "2/3",
        '⅛' to "1/8"
    )

    private val units = setOf(
        // Metric
        "g", "gram", "grams",
        "kg", "kilogram", "kilograms",
        "mg",
        "ml", "milliliter", "milliliters", "millilitre", "millilitres",
        "l", "liter", "liters", "litre", "litres",
        "dl", "cl",
        // English volumetric
        "tsp", "tsp.", "teaspoon", "teaspoons",
        "tbsp", "tbsp.", "tablespoon", "tablespoons",
        "cup", "cups",
        // English piece
        "piece", "pieces", "pcs",
        "slice", "slices",
        "clove", "cloves",
        "pinch",
        "handful",
        "can", "package", "pkg",
        // Czech metric
        "gramu", "gramy", "gramů",
        "kilogramu", "kilogramy", "kilogramů",
        "mililitru", "mililitry", "mililitrů",
        "litru", "litry", "litrů",
        // Czech volumetric
        "lžíce", "lžíci", "lžic",
        "lžička", "lžičky", "lžičku", "lžičce",
        "šálek", "šálku", "šálky",
        "hrnek", "hrnku", "hrnky",
        // Czech piece
        "ks",
        "kus", "kusy", "kusů",
        "špetka", "špetku", "špetky",
        "stroužek", "stroužku", "stroužky", "stroužků",
        "plátek", "plátky", "plátků",
        "hrst", "hrstka", "hrsti",
        "balení", "balíček",
        "konzerva", "konzervy",
        "větvička", "větvičky",
    )

    data class Parsed(val amount: Double?, val unit: String?, val name: String)

    fun parse(input: String): Parsed {
        var s = input.trim()
        if (s.isEmpty()) return Parsed(null, null, "")

        // normalize unicode fractions
        unicodeFractionMap.forEach { (k, v) -> s = s.replace(k.toString(), v) }
        // normalize commas in numbers
        s = s.replace(',', '.')

        var amount: Double? = null
        var rest = s

        // mixed fraction e.g. "1 1/2"
        val mixed = Regex("^(\\d+)\\s+(\\d+)/(\\d+)\\b(.*)")
        val frac = Regex("^(\\d+)/(\\d+)\\b(.*)")
        val dec = Regex("^(\\d+(?:\\.\\d+)?)\\b(.*)")

        when {
            mixed.containsMatchIn(s) -> {
                val m = mixed.find(s)!!
                val whole = m.groupValues[1].toDouble()
                val num = m.groupValues[2].toDouble()
                val den = m.groupValues[3].toDouble()
                amount = whole + (num / den)
                rest = m.groupValues[4].trim()
            }
            frac.containsMatchIn(s) -> {
                val m = frac.find(s)!!
                val num = m.groupValues[1].toDouble()
                val den = m.groupValues[2].toDouble()
                amount = num / den
                rest = m.groupValues[3].trim()
            }
            dec.containsMatchIn(s) -> {
                val m = dec.find(s)!!
                amount = m.groupValues[1].toDoubleOrNull()
                rest = m.groupValues[2].trim()
            }
        }

        // attempt to find a unit at the beginning of rest
        var unit: String? = null
        if (rest.isNotBlank()) {
            val tokens = rest.split(Regex("\\s+"))
            if (tokens.isNotEmpty()) {
                val t0 = tokens[0].lowercase().replace(".", "")
                if (t0 in units) {
                    unit = tokens[0]
                    rest = tokens.drop(1).joinToString(" ")
                } else {
                    unit = null
                }
            }
        }

        val name = rest.trim()
        return Parsed(amount, unit?.ifBlank { null }, name)
    }
}
