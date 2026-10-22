package com.crecker.manager.core.model

import android.content.Context
import androidx.annotation.StringRes
import java.io.IOException

/** שגיאה עם הודעה מתורגמת – נזרקת מקוד שאין לו Context (למשל uploaders) ומוצגת עם [describe]. */
class LocalizedException(
    @StringRes val messageRes: Int,
    vararg val args: Any,
    cause: Throwable? = null,
) : IOException(cause)

/** תיאור קצר וקריא של חריגה ללוג. */
fun Throwable.describe(context: Context): String =
    if (this is LocalizedException) context.getString(messageRes, *args) else describe()

/** תיאור טכני (שם החריגה + ההודעה). */
fun Throwable.describe(): String = buildString {
    append(this@describe.javaClass.simpleName)
    message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it.trim()) }
}
