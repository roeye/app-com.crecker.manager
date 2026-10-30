package com.crecker.manager.core.model

import java.util.Locale

/** גודל קובץ לתצוגה: "512 B" או "24.3 KB". */
fun formatSize(bytes: Long): String =
    if (bytes < 1024) "$bytes B" else "%.1f KB".format(Locale.US, bytes / 1024.0)

fun formatSize(bytes: Int): String = formatSize(bytes.toLong())
