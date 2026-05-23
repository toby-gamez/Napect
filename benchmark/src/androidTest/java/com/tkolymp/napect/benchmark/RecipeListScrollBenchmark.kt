package com.tkolymp.napect.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeListScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollRecipeList() = benchmarkRule.measureRepeated(
        packageName = "com.tobiso.napect",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
    ) {
        pressHome()
        startActivityAndWait()
        // Wait for the recipe list to appear
        device.wait(Until.hasObject(By.scrollable(true)), 3_000)
        val list = device.findObject(By.scrollable(true))
        list?.fling(Direction.DOWN)
        list?.fling(Direction.UP)
    }
}
