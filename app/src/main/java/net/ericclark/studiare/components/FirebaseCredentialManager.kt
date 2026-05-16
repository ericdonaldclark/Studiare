package net.ericclark.studiare.components

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.json.JSONObject

class FirebaseCredentialManager(private val context: Context) {

    private val TAG = "FirebaseCredentialMgr"
    private val PREF_FILE = "secure_byob_prefs"
    private val APP_NAME = "StudiareBYOB"

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREF_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hasCredentials(): Boolean {
        return sharedPreferences.contains("project_id")
    }

    /**
     * Parses the imported google-services.json string and saves the required keys securely.
     */
    fun parseAndSaveCredentials(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            val projectInfo = root.getJSONObject("project_info")
            val projectId = projectInfo.getString("project_id")
            val storageBucket = projectInfo.optString("storage_bucket", "")
            val firebaseDbUrl = projectInfo.optString("firebase_url", "")

            val clientArray = root.getJSONArray("client")
            // Find the client object that matches this app's package name
            var targetClient: JSONObject? = null
            for (i in 0 until clientArray.length()) {
                val client = clientArray.getJSONObject(i)
                val packageName = client.getJSONObject("client_info")
                    .getJSONObject("android_client_info")
                    .getString("package_name")

                if (packageName == context.packageName) {
                    targetClient = client
                    break
                }
            }

            if (targetClient == null) {
                Log.e(TAG, "google-services.json does not contain a client for ${context.packageName}")
                return false
            }

            val appId = targetClient.getJSONObject("client_info").getString("mobilesdk_app_id")
            val apiKey = targetClient.getJSONArray("api_key").getJSONObject(0).getString("current_key")

            // Extract Web Client ID (Client Type 3) needed for Google Sign-In
            var webClientId = ""
            val oauthClients = targetClient.optJSONArray("oauth_client")
            if (oauthClients != null) {
                for (j in 0 until oauthClients.length()) {
                    val clientObj = oauthClients.getJSONObject(j)
                    if (clientObj.optInt("client_type") == 3) {
                        webClientId = clientObj.getString("client_id")
                        break
                    }
                }
            }

            // Save to EncryptedPrefs
            sharedPreferences.edit().apply {
                putString("project_id", projectId)
                putString("app_id", appId)
                putString("api_key", apiKey)
                putString("storage_bucket", storageBucket)
                putString("firebase_url", firebaseDbUrl)
                putString("web_client_id", webClientId)
            }.apply()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse google-services.json", e)
            false
        }
    }

    /**
     * Initializes or retrieves the dynamic FirebaseApp instance using stored credentials.
     */
    fun getOrInitializeFirebaseApp(): FirebaseApp? {
        if (!hasCredentials()) return null

        return try {
            FirebaseApp.getInstance(APP_NAME)
        } catch (e: IllegalStateException) {
            // App doesn't exist yet, initialize it
            val options = FirebaseOptions.Builder()
                .setProjectId(sharedPreferences.getString("project_id", "")!!)
                .setApplicationId(sharedPreferences.getString("app_id", "")!!)
                .setApiKey(sharedPreferences.getString("api_key", "")!!)
                .apply {
                    val bucket = sharedPreferences.getString("storage_bucket", "")
                    if (!bucket.isNullOrEmpty()) setStorageBucket(bucket)

                    val dbUrl = sharedPreferences.getString("firebase_url", "")
                    if (!dbUrl.isNullOrEmpty()) setDatabaseUrl(dbUrl)
                }
                .build()

            FirebaseApp.initializeApp(context, options, APP_NAME)
        }
    }

    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
        try {
            FirebaseApp.getInstance(APP_NAME).delete()
        } catch (e: IllegalStateException) {
            // App was already not initialized
        }
    }

    fun getActiveProjectId(): String? {
        return sharedPreferences.getString("project_id", null)
    }

    fun getWebClientId(): String? {
        return sharedPreferences.getString("web_client_id", null)?.takeIf { it.isNotEmpty() }
    }
}