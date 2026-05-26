package com.tkolymp.napect.ui.recipes

fun formatTimeMinutes(minutes: Int): String =
    if (minutes < 60) "$minutes min"
    else {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "${h}h" else "${h}h ${m}min"
    }
