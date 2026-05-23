package com.tkolymp.napect.domain.usecase

import com.tkolymp.napect.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ClassifyRecipeUseCaseTest {
    private lateinit var useCase: ClassifyRecipeUseCase

    @Before
    fun setUp() {
        useCase = ClassifyRecipeUseCase()
    }

    @Test
    fun `classify empty input returns MAIN`() {
        assertEquals(Category.MAIN, useCase("", emptyList(), emptyList()))
    }

    @Test
    fun `classify soup keyword returns SOUP`() {
        assertEquals(Category.SOUP, useCase(null, listOf("kuřecí polévka"), listOf("vařit")))
    }

    @Test
    fun `classify dessert keyword returns DESSERT`() {
        assertEquals(Category.DESSERT, useCase("Čokoládový koláč", emptyList(), emptyList()))
    }

    @Test
    fun `classify baking keyword returns BAKING`() {
        assertEquals(Category.BAKING, useCase(null, emptyList(), listOf("pečení při 180°C")))
    }

    @Test
    fun `classify breakfast keyword returns BREAKFAST`() {
        assertEquals(Category.BREAKFAST, useCase("snídaně v posteli", emptyList(), emptyList()))
    }

    @Test
    fun `classify quick keyword returns QUICK`() {
        assertEquals(Category.QUICK, useCase(null, emptyList(), listOf("rychlé vaření")))
    }

    @Test
    fun `classify diet keyword returns DIET`() {
        assertEquals(Category.DIET, useCase(null, listOf("vegan"), emptyList()))
    }

    @Test
    fun `classify with multiple keywords picks first match`() {
        assertEquals(Category.SOUP, useCase("Polévkový koláč", emptyList(), emptyList()))
    }

    @Test
    fun `classify is case insensitive`() {
        assertEquals(Category.SOUP, useCase("POLÉVKA", emptyList(), emptyList()))
    }

    @Test
    fun `classify searches in title ingredients and steps`() {
        assertEquals(
            Category.DESSERT,
            useCase("Recept", listOf("cukr", "mouka"), listOf("upéct"))
        )
    }

    @Test
    fun `classify non matching input returns MAIN`() {
        assertEquals(Category.MAIN, useCase("Plněné papriky", listOf("paprika", "rýže"), listOf("plnit", "vařit")))
    }
}
