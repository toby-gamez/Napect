package com.tkolymp.napect.data.local

import com.tkolymp.napect.domain.model.TagGroup

val DEFAULT_TAGS: List<Pair<String, TagGroup>> = listOf(
    // DIFFICULTY
    Pair("Jednoduché", TagGroup.DIFFICULTY),
    Pair("Střední", TagGroup.DIFFICULTY),
    Pair("Náročné", TagGroup.DIFFICULTY),
    // TIME
    Pair("15 min", TagGroup.TIME),
    Pair("30 min", TagGroup.TIME),
    Pair("1 h", TagGroup.TIME),
    Pair("2 h+", TagGroup.TIME),
    // DIET
    Pair("Vegan", TagGroup.DIET),
    Pair("Vegetariánské", TagGroup.DIET),
    Pair("Bez lepku", TagGroup.DIET),
    Pair("Bez mléka", TagGroup.DIET),
    // CUISINE
    Pair("Italská", TagGroup.CUISINE),
    Pair("Čínská", TagGroup.CUISINE),
    Pair("Mexická", TagGroup.CUISINE),
    Pair("Indická", TagGroup.CUISINE),
    Pair("Francouzská", TagGroup.CUISINE),
    Pair("Česká", TagGroup.CUISINE),
    Pair("Americká", TagGroup.CUISINE),
    Pair("Japonská", TagGroup.CUISINE),
    // COMMON INGREDIENTS / TAGS
    Pair("Kuřecí", TagGroup.OTHER),
    Pair("Hovězí", TagGroup.OTHER),
    Pair("Vepřové", TagGroup.OTHER),
    Pair("Těstoviny", TagGroup.OTHER),
    Pair("Rýže", TagGroup.OTHER),
    Pair("Dezert", TagGroup.CATEGORY),
    Pair("Pálivé", TagGroup.OTHER),
    Pair("Rychlé", TagGroup.CATEGORY),
    Pair("Ekonomické", TagGroup.OTHER),
    Pair("Zdravé", TagGroup.OTHER),
    Pair("Sladké", TagGroup.OTHER),
    Pair("Slané", TagGroup.OTHER),
    // METHOD
    Pair("Smažené", TagGroup.METHOD),
    Pair("Pečené", TagGroup.METHOD),
    Pair("Grilované", TagGroup.METHOD),
    Pair("Dušené", TagGroup.METHOD),
    Pair("Syrové", TagGroup.METHOD),
    // MEAL
    Pair("Snídaně", TagGroup.CATEGORY),
    Pair("Oběd", TagGroup.MEAL),
    Pair("Večeře", TagGroup.MEAL),
    Pair("Svačina", TagGroup.MEAL),
    // OTHER
    Pair("Pro děti", TagGroup.OTHER),
    Pair("Jednohrnec", TagGroup.OTHER),
    Pair("Příprava jídla", TagGroup.OTHER),
    Pair("Sváteční", TagGroup.CATEGORY),
    // Additional category-aligned tags
    Pair("Polévka", TagGroup.CATEGORY),
    Pair("Hlavní chod", TagGroup.CATEGORY),
    Pair("Pečení", TagGroup.CATEGORY)
)
