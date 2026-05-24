package com.tkolymp.napect.data.network

class UrlImportCache(private val maxSize: Int = 20) {
    private val cache = LinkedHashMap<String, ImportedRecipeData>(16, 0.75f, true)

    @Synchronized
    fun get(url: String): ImportedRecipeData? = cache[url]

    @Synchronized
    fun put(url: String, data: ImportedRecipeData) {
        cache[url] = data
        if (cache.size > maxSize) {
            cache.entries.iterator().run { next(); remove() }
        }
    }
}
