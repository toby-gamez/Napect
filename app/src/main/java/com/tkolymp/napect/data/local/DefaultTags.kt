package com.tkolymp.napect.data.local

import com.tkolymp.napect.domain.model.TagGroup

val DEFAULT_TAGS: List<Pair<String, TagGroup>> = listOf(
    // DIFFICULTY
    Pair("Easy", TagGroup.DIFFICULTY),
    Pair("Medium", TagGroup.DIFFICULTY),
    Pair("Hard", TagGroup.DIFFICULTY),
    // TIME
    Pair("15 min", TagGroup.TIME),
    Pair("30 min", TagGroup.TIME),
    Pair("1 h", TagGroup.TIME),
    Pair("2 h+", TagGroup.TIME),
    // DIET
    Pair("Vegan", TagGroup.DIET),
    Pair("Vegetarian", TagGroup.DIET),
    Pair("Gluten-Free", TagGroup.DIET),
    Pair("Dairy-Free", TagGroup.DIET),
    // CUISINE
    Pair("Italian", TagGroup.CUISINE),
    Pair("Chinese", TagGroup.CUISINE),
    Pair("Mexican", TagGroup.CUISINE),
    Pair("Indian", TagGroup.CUISINE),
    Pair("French", TagGroup.CUISINE),
    Pair("Czech", TagGroup.CUISINE),
    Pair("American", TagGroup.CUISINE),
    Pair("Japanese", TagGroup.CUISINE),
    // COMMON INGREDIENTS / TAGS
    Pair("Chicken", TagGroup.OTHER),
    Pair("Beef", TagGroup.OTHER),
    Pair("Pork", TagGroup.OTHER),
    Pair("Pasta", TagGroup.OTHER),
    Pair("Rice", TagGroup.OTHER),
    Pair("Dessert", TagGroup.CATEGORY),
    Pair("Spicy", TagGroup.OTHER),
    Pair("Quick", TagGroup.CATEGORY),
    Pair("Budget", TagGroup.OTHER),
    Pair("Healthy", TagGroup.OTHER),
    Pair("Sweet", TagGroup.OTHER),
    Pair("Savory", TagGroup.OTHER),
    // METHOD
    Pair("Fried", TagGroup.METHOD),
    Pair("Baked", TagGroup.METHOD),
    Pair("Grilled", TagGroup.METHOD),
    Pair("Steamed", TagGroup.METHOD),
    Pair("Raw", TagGroup.METHOD),
    // MEAL
    Pair("Breakfast", TagGroup.CATEGORY),
    Pair("Lunch", TagGroup.MEAL),
    Pair("Dinner", TagGroup.MEAL),
    Pair("Snack", TagGroup.MEAL),
    // OTHER
    Pair("Kid-Friendly", TagGroup.OTHER),
    Pair("One Pot", TagGroup.OTHER),
    Pair("Meal Prep", TagGroup.OTHER),
    Pair("Holiday", TagGroup.CATEGORY),
    // Additional category-aligned tags
    Pair("Soup", TagGroup.CATEGORY),
    Pair("Main", TagGroup.CATEGORY),
    Pair("Baking", TagGroup.CATEGORY)
)
