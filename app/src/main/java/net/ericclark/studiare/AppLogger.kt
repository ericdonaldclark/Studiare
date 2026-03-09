package net.ericclark.studiare

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Centralized logging object.
 * - Logs to standard Android Logcat for debugging.
 * - Forwards non-fatal errors and critical logs to Firebase Crashlytics.
 */
object AppLogger {
    private const val TAG = "AppLogger"

    // Store the debug state locally so we don't reference BuildConfig in this file
    private var isDebug: Boolean = false

    /**
     * Initialize the logger.
     * @param isDebugBuild Pass BuildConfig.DEBUG from your Application or Activity here.

    fun init(isDebugBuild: Boolean) {
        this.isDebug = isDebugBuild
        // Configure Crashlytics based on the passed flag
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!isDebugBuild)
    }
     */
    fun init(isDebugBuild: Boolean) {
        this.isDebug = isDebugBuild
        // Temporarily force it on for testing
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
    }

    fun setUserId(userId: String) {
        FirebaseCrashlytics.getInstance().setUserId(userId)
    }

    fun setCustomKey(key: String, value: String) {
        FirebaseCrashlytics.getInstance().setCustomKey(key, value)
    }

    fun d(tag: String, message: String) {
        // Use the local property, NOT BuildConfig
        if (isDebug) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        // Optional: Log important info steps to Crashlytics logs (not as issues)
        FirebaseCrashlytics.getInstance().log("$tag: $message")
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        FirebaseCrashlytics.getInstance().log("WARNING: $tag: $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)

        // Record non-fatal exception
        if (throwable != null) {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } else {
            // If there's no exception object, create one to capture the stack trace
            FirebaseCrashlytics.getInstance().recordException(Exception("$tag: $message"))
        }
    }
}