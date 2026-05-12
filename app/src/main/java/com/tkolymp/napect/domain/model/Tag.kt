package com.tkolymp.napect.domain.model

data class Tag(
    val id: Long = 0,
    val name: String,
    val group: TagGroup,
    val isAiGenerated: Boolean = false,
    val isUserCreated: Boolean = false,
)
