package com.github.adibfara.filerush

data class QuickFileEntry(
    val path: String,
    val isDirectory: Boolean,
    val existing: Boolean,
    val templateName: String? = null,
    val displayName: String? = null
)
