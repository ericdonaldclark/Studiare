package net.ericclark.studiare.components

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/** One backup file as Drive's `files.list` reports it. */
data class DriveFileInfo(val id: String, val name: String, val createdTime: String)

/**
 * Thin Google Drive v3 REST wrapper over the project's existing Ktor client, scoped to exactly
 * what Scheduled Export Backup needs: authorize, find/create the app's backup folder, upload a
 * JSON backup, list and delete old ones. Deliberately hand-rolled rather than pulling in
 * `google-api-client`/`google-api-services-drive` — this project has no other dependency on that
 * stack, and the handful of REST calls below are simpler over the Ktor client already used
 * elsewhere (`AudioStudyService`).
 *
 * Scope is `drive.file` (confirmed via `com.google.android.gms.common.api.Scope`'s plain-string
 * constructor — no `DriveScopes` constant class needed) — files this app creates, visible in the
 * user's own Drive, not a hidden `drive.appdata` folder.
 */
object DriveBackupClient {
    private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val API_BASE = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"

    private val client by lazy { HttpClient(Android) }

    fun buildAuthorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()

    /**
     * Requests access. If already granted (including the common case of a background worker
     * re-checking before a scheduled run), this resolves directly with an access token and
     * [AuthorizationResult.hasResolution] false — no UI involved. If first-time consent is
     * needed, [AuthorizationResult.hasResolution] is true and the caller (which must have an
     * Activity to work with — never the background worker) launches [AuthorizationResult.pendingIntent].
     * Returns null only on an outright failure (no network, API error).
     */
    suspend fun authorize(context: Context): AuthorizationResult? = runCatching {
        Identity.getAuthorizationClient(context).authorize(buildAuthorizationRequest()).await()
    }.getOrNull()

    /** Extracts the final [AuthorizationResult] after the consent `pendingIntent` returns a result. */
    fun resultFromIntent(context: Context, data: Intent): AuthorizationResult? = runCatching {
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
    }.getOrNull()

    suspend fun fetchAccountEmail(accessToken: String): String? = runCatching {
        val response: HttpResponse = client.get("$API_BASE/about") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("fields", "user(emailAddress)")
        }
        if (!response.status.isSuccess()) return@runCatching null
        JSONObject(response.bodyAsText()).optJSONObject("user")?.optString("emailAddress")?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** Reuses [existingFolderId] if it still resolves (not trashed/deleted elsewhere); otherwise creates a fresh one. */
    suspend fun ensureBackupFolder(accessToken: String, existingFolderId: String?): String? {
        if (existingFolderId != null) {
            val stillValid = runCatching {
                val response = client.get("$API_BASE/files/$existingFolderId") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    parameter("fields", "id,trashed")
                }
                response.status.isSuccess() && !JSONObject(response.bodyAsText()).optBoolean("trashed", false)
            }.getOrDefault(false)
            if (stillValid) return existingFolderId
        }
        return runCatching {
            val metadata = JSONObject().apply {
                put("name", "Studiare Backups")
                put("mimeType", "application/vnd.google-apps.folder")
            }
            val response = client.post("$API_BASE/files") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(metadata.toString())
            }
            if (!response.status.isSuccess()) return@runCatching null
            JSONObject(response.bodyAsText()).optString("id").takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    /**
     * Uploads [contentBytes] (tagged [mimeType] — `application/json`, `text/csv`, or the binary
     * `application/octet-stream` for Anki packages) as a new file named [fileName] inside
     * [folderId] via a multipart/related simple upload. The body is built as a `ByteArray`, not a
     * `String`, so binary content survives — a Kotlin `String` can't safely carry arbitrary bytes.
     */
    suspend fun uploadBackup(accessToken: String, folderId: String, fileName: String, mimeType: String, contentBytes: ByteArray): Boolean = runCatching {
        val boundary = "studiare_backup_${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(folderId))
        }
        val preamble = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: $mimeType\r\n\r\n"
        val closing = "\r\n--$boundary--"
        val body = preamble.toByteArray(Charsets.UTF_8) + contentBytes + closing.toByteArray(Charsets.UTF_8)
        val response = client.post("$UPLOAD_BASE/files") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("uploadType", "multipart")
            header(HttpHeaders.ContentType, "multipart/related; boundary=$boundary")
            setBody(body)
        }
        response.status.isSuccess()
    }.getOrDefault(false)

    suspend fun listBackupFiles(accessToken: String, folderId: String): List<DriveFileInfo> = runCatching {
        val response = client.get("$API_BASE/files") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("q", "'$folderId' in parents and trashed = false")
            parameter("fields", "files(id,name,createdTime)")
            parameter("orderBy", "createdTime desc")
        }
        if (!response.status.isSuccess()) return@runCatching emptyList()
        val files = JSONObject(response.bodyAsText()).optJSONArray("files") ?: JSONArray()
        (0 until files.length()).map { i ->
            val obj = files.getJSONObject(i)
            DriveFileInfo(obj.getString("id"), obj.optString("name"), obj.optString("createdTime"))
        }
    }.getOrDefault(emptyList())

    suspend fun deleteFile(accessToken: String, fileId: String): Boolean = runCatching {
        val response = client.delete("$API_BASE/files/$fileId") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        response.status.isSuccess()
    }.getOrDefault(false)
}
