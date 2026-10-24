package com.crecker.manager.core.security

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.crecker.manager.graph

/**
 * אימות מול נעילת המכשיר: טביעת אצבע / זיהוי פנים, עם PIN / pattern / סיסמה כגיבוי.
 * האפליקציה לא מקבלת שום מידע ביומטרי – רק הצלחה/כשל.
 */
object DeviceAuth {
    /** BIOMETRIC_WEAK (ולא STRONG) – כולל זיהוי פנים ברוב המכשירים, ונתמך עם DEVICE_CREDENTIAL גם לפני Android 11. */
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** האם מוגדרת במכשיר נעילה כלשהי שאפשר לאמת מולה. */
    fun isAvailable(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            // לפני Android 11 canAuthenticate לא תומך ב-DEVICE_CREDENTIAL
            context.getSystemService(KeyguardManager::class.java).isDeviceSecure
        }

    /**
     * מציג את דיאלוג האימות של המערכת. [onResult] נקרא פעם אחת: true = אומת, false = בוטל/נכשל.
     *
     * @param requireConfirmation false = אחרי זיהוי פנים תקין נכנסים מיד, בלי ללחוץ "אישור".
     * זה רמז למערכת (Android 10+), והיצרן רשאי להתעלם ממנו. לשינויים שמחלישים אבטחה – להשאיר true.
     */
    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        requireConfirmation: Boolean = true,
        onResult: (Boolean) -> Unit,
    ) {
        val activity = context.findFragmentActivity() ?: return onResult(false)
        val appLock = activity.graph.appLock
        appLock.authenticating = true
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                appLock.authenticating = false
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                appLock.authenticating = false
                onResult(false)
            }
            // onAuthenticationFailed = ניסיון בודד שלא זוהה; הדיאלוג נשאר פתוח
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(requireConfirmation)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }

    private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
}
