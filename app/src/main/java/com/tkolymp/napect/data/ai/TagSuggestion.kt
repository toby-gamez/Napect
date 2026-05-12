package com.tkolymp.napect.data.ai

import com.tkolymp.napect.domain.model.Tag

data class TagSuggestion(
    val confirmed: List<Tag> = emptyList(),
    val newlyCreated: List<Tag> = emptyList()
)
