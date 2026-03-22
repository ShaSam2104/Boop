package com.shashsam.boop.ui.models

/**
 * Immutable data model for a single status log entry shown in the activity card.
 */
data class LogEntry(
    val message: String,
    val isError: Boolean = false
)
