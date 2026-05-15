package com.tkolymp.napect.data.ai

import com.tkolymp.napect.domain.model.TagGroup
import java.text.Normalizer

/**
 * Simple keyword-based tag suggester. Operates offline.
 * Text is lowercased and diacritics are stripped before matching, so patterns can use
 * plain ASCII forms (e.g. "kureci" matches "kuřecí").
 */
object TagSuggester {
    private val KEYWORD_MAP: List<Pair<Regex, List<Pair<String, TagGroup>>>> = listOf(

        // ── TIME ──────────────────────────────────────────────────────────────────
        Regex("\\b15\\s?min\\b") to listOf("15 min" to TagGroup.TIME),
        Regex("\\b30\\s?min\\b") to listOf("30 min" to TagGroup.TIME),
        Regex("\\b1\\s?h(our)?s?\\b") to listOf("1 h" to TagGroup.TIME),
        Regex("\\b2\\s?h\\+?\\b") to listOf("2 h+" to TagGroup.TIME),

        // ── DIET ──────────────────────────────────────────────────────────────────
        Regex("\\bvegan\\b") to listOf("Vegan" to TagGroup.DIET),
        Regex("\\bvegetarian\\b|\\bvegetariansky\\b|\\bvegetarianska\\b|\\bvegetarianske\\b") to listOf("Vegetariánské" to TagGroup.DIET),
        Regex("\\bgluten(-|\\s)?free\\b|\\bgluten\\b|\\bbezlepkov\\b|\\bbez lepku\\b") to listOf("Bez lepku" to TagGroup.DIET),
        Regex("\\bdairy(-|\\s)?free\\b|\\bdairy\\b|\\bbez mleka\\b|\\bbez laktoz\\b|\\blaktozov\\b") to listOf("Bez mléka" to TagGroup.DIET),

        // ── METHOD ────────────────────────────────────────────────────────────────
        Regex("\\bfry\\b|\\bfried\\b|\\bsmazen[yae]?\\b|\\bsmazit\\b|\\bsmazime\\b|\\bsmaz(te|it)\\b") to listOf("Smažené" to TagGroup.METHOD),
        Regex("\\bbake\\b|\\bbaked\\b|\\boven\\b|\\bpecen[yae]?\\b|\\bpeceme\\b|\\bpec(te|eme|i)\\b") to listOf("Pečené" to TagGroup.METHOD),
        Regex("\\bgrill\\b|\\bgrilled\\b|\\bgrilovan[yae]?\\b|\\bgrilovat\\b|\\bgrilujeme\\b") to listOf("Grilované" to TagGroup.METHOD),
        Regex("\\bsteam\\b|\\bsteamed\\b|\\bdusit\\b|\\bdusime\\b|\\bdusime\\b|\\bdusene\\b|\\bpara\\b|\\bvarem\\b|\\bvare v pare\\b") to listOf("Dušené" to TagGroup.METHOD),
        Regex("\\braw\\b|\\bsyrove\\b|\\bsyrovy\\b|\\bnepecen[yae]?\\b") to listOf("Syrové" to TagGroup.METHOD),
        // No-bake → "Bez pečení" (AI-created tag, not a default)
        Regex("\\bno[- ]?bake\\b|\\bnobake\\b|\\bnepecen[yae]?\\b") to listOf("Bez pečení" to TagGroup.METHOD),

        // ── CUISINE ───────────────────────────────────────────────────────────────
        Regex("\\bital(ian)?\\b|\\bitalsk[yae]\\b") to listOf("Italská" to TagGroup.CUISINE),
        Regex("\\bpasta\\b|\\bpizza\\b|\\brizoto\\b|\\brisotto\\b") to listOf("Italská" to TagGroup.CUISINE),
        Regex("\\bsoy sauce\\b|\\bwok\\b|\\btofu\\b|\\bcinsky\\b|\\bcinska\\b|\\bcinske\\b") to listOf("Čínská" to TagGroup.CUISINE),
        Regex("\\bmexic(an|o|ky|ka|ke)?\\b|\\btortill\\b|\\btaco\\b|\\bburrito\\b") to listOf("Mexická" to TagGroup.CUISINE),
        Regex("\\bindian\\b|\\bindick[yae]\\b|\\bcurry\\b|\\bkari\\b|\\bgaram masala\\b") to listOf("Indická" to TagGroup.CUISINE),
        Regex("\\bfrench\\b|\\bfrancouzsk[yae]\\b|\\bprovenc\\b") to listOf("Francouzská" to TagGroup.CUISINE),
        Regex("\\bczech\\b|\\bcesk[yae]\\b|\\bcechia\\b|\\bmoravn\\b") to listOf("Česká" to TagGroup.CUISINE),
        Regex("\\bamerican\\b|\\bamericky\\b|\\bamericka\\b") to listOf("Americká" to TagGroup.CUISINE),
        Regex("\\bjapanese\\b|\\bjaponsk[yae]\\b|\\bsushi\\b|\\bramen\\b|\\bmiso\\b") to listOf("Japonská" to TagGroup.CUISINE),

        // ── INGREDIENTS / PROTEINS ────────────────────────────────────────────────
        Regex("\\bchicken\\b|\\bkureci\\b|\\bkure\\b|\\bkurata\\b|\\bkurinu\\b|\\bkura\\b") to listOf("Kuřecí" to TagGroup.OTHER),
        Regex("\\bbeef\\b|\\bhovezi\\b|\\bhovezim\\b|\\bhovez[ia]\\b") to listOf("Hovězí" to TagGroup.OTHER),
        Regex("\\bpork\\b|\\bveprove\\b|\\bveprovy\\b|\\bveprov\\b|\\bsalama\\b|\\bsunka\\b") to listOf("Vepřové" to TagGroup.OTHER),
        Regex("\\bpasta\\b|\\btestoviny\\b|\\bspagety\\b|\\bspagetti\\b|\\bpenne\\b|\\brigatoni\\b|\\bfusilli\\b|\\blasagne\\b") to listOf("Těstoviny" to TagGroup.OTHER),
        Regex("\\brice\\b|\\bryze\\b|\\bryzi\\b|\\brizoto\\b") to listOf("Rýže" to TagGroup.OTHER),

        // ── PROPERTIES ────────────────────────────────────────────────────────────
        Regex("\\bspicy\\b|\\bchili\\b|\\bchilli\\b|\\bpalive\\b|\\bpalivy\\b|\\bostry\\b|\\bostra\\b|\\bkoreneny\\b") to listOf("Pálivé" to TagGroup.OTHER),
        Regex("\\bquick\\b|\\bfast\\b|\\b30\\s?min\\b|\\b15\\s?min\\b|\\brychle\\b|\\brychlou\\b|\\brychl[yae]\\b") to listOf("Rychlé" to TagGroup.CATEGORY),
        Regex("\\bcheap\\b|\\bbudget\\b|\\blevne\\b|\\blevny\\b|\\bekonom\\b") to listOf("Ekonomické" to TagGroup.OTHER),
        Regex("\\bhealthy\\b|\\bzdrave\\b|\\bzdravy\\b|\\bzdrava\\b") to listOf("Zdravé" to TagGroup.OTHER),
        Regex("\\bsweet\\b|\\bsladke\\b|\\bsladky\\b|\\bsladka\\b") to listOf("Sladké" to TagGroup.OTHER),
        Regex("\\bsavory\\b|\\bsavour\\b|\\bslane\\b|\\bslany\\b|\\bslana\\b") to listOf("Slané" to TagGroup.OTHER),

        // ── CATEGORY ──────────────────────────────────────────────────────────────
        Regex("\\bsoup\\b|\\bbroth\\b|\\bpolevka\\b|\\bvyvar\\b|\\bpolevce\\b|\\bpolevi\\b") to listOf("Polévka" to TagGroup.CATEGORY),
        Regex("\\bdessert\\b|\\bcake\\b|\\bcookie\\b|\\bpudding\\b|\\bpie\\b|\\bdezert\\b|\\bdort\\b|\\bkolac\\b|\\bzakusek\\b|\\bcukrovi\\b") to listOf(
            "Dezert" to TagGroup.CATEGORY,
            "Sladké" to TagGroup.OTHER,
        ),
        Regex("\\bbaking\\b|\\bpeceni\\b") to listOf("Pečení" to TagGroup.CATEGORY),
        Regex("\\bbreakfast\\b|\\bsnidane\\b|\\bsnidani\\b|\\bsnidanku\\b") to listOf("Snídaně" to TagGroup.CATEGORY),
        Regex("\\bholiday\\b|\\bchristmas\\b|\\beaster\\b|\\bsvatecni\\b|\\bvanoce\\b|\\bvanocu\\b|\\bvelikonoce\\b|\\bvelikonoc\\b") to listOf("Sváteční" to TagGroup.CATEGORY),

        // ── CHEESECAKE / STRAWBERRY (specific, create as AI tag if not in defaults) ──
        Regex("\\bcheesecake\\b|\\btvarohovy dort\\b|\\btvarohov\\b") to listOf("Cheesecake" to TagGroup.OTHER, "Dezert" to TagGroup.CATEGORY),
        Regex("\\bstrawberr(y|ies)\\b|\\bjahodov\\b|\\bjahod\\b|\\bjahody\\b") to listOf("Jahodový" to TagGroup.OTHER),

        // ── DIFFICULTY ────────────────────────────────────────────────────────────
        Regex("\\beasy\\b|\\bsimple\\b|\\bjednoduch\\b|\\bprost[ae]\\b") to listOf("Jednoduché" to TagGroup.DIFFICULTY),
        Regex("\\bmedium\\b|\\bstredni\\b|\\bstrednich\\b") to listOf("Střední" to TagGroup.DIFFICULTY),
        Regex("\\bhard\\b|\\bdifficult\\b|\\bnarocn\\b|\\bslozhit\\b") to listOf("Náročné" to TagGroup.DIFFICULTY),
    )

    fun suggest(text: String): Set<Pair<String, TagGroup>> {
        // Lowercase and remove diacritics so patterns match both English and Czech variants
        val lower = text.lowercase()
        val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
        val results = mutableSetOf<Pair<String, TagGroup>>()
        for ((regex, vs) in KEYWORD_MAP) {
            if (regex.containsMatchIn(normalized)) {
                for (v in vs) results.add(v)
            }
        }
        return results
    }
}
