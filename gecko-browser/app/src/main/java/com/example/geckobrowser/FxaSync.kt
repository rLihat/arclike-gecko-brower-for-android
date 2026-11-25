package com.example.geckobrowser

import android.content.Context
import android.content.SharedPreferences
import mozilla.appservices.fxaclient.FxaConfig
import mozilla.appservices.fxaclient.FxaServer
import mozilla.appservices.fxaclient.FxaEvent
import mozilla.appservices.fxaclient.FxaException
import mozilla.appservices.fxaclient.FxaState
import mozilla.appservices.fxaclient.FirefoxAccount
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Real Mozilla-account sign-in and a custom Sync 1.5 record for Spaces.
 * Mirrors Zen's "Workspaces & Tabs" engine: one encrypted record,
 * last-writer-wins on updatedAt, tombstones propagate deletes. Passwords are
 * never requested or stored here.
 */
class FxaSync(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("horizen_fxa", Context.MODE_PRIVATE)

    private var account: FirefoxAccount? = null
    private var rustReady = false

    /** NSS + the Rust HTTP client are only needed once an account is used. */
    @Synchronized
    private fun ensureRust() {
        if (rustReady) return
        mozilla.appservices.init_rust_components.initialize()
        mozilla.components.support.rusthttp.RustHttpConfig.setClient(lazy {
            mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient()
        })
        rustReady = true
    }

    val isSignedIn: Boolean get() = prefs.getString(KEY_STATE, null) != null

    val email: String? get() = prefs.getString(KEY_EMAIL, null)

    val avatarUrl: String? get() = prefs.getString(KEY_AVATAR, null)

    val displayName: String? get() = prefs.getString(KEY_DISPLAY_NAME, null)

    val redirectUrl: String get() = BuildConfig.FXA_REDIRECT_URI

    var lastError: String? = null
        private set

    var lastSyncAt: Long get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        private set(value) { prefs.edit().putLong(KEY_LAST_SYNC, value).apply() }

    private fun config(): FxaConfig = FxaConfig(
        // Matches Mozilla's Firefox Accounts sample client config.
        FxaServer.Custom("https://accounts.firefox.com"),
        BuildConfig.FXA_CLIENT_ID,
        BuildConfig.FXA_REDIRECT_URI,
        null,
    )

    private fun account(): FirefoxAccount {
        account?.let { return it }
        ensureRust()
        val saved = prefs.getString(KEY_STATE, null)
        val restored = if (saved != null) {
            FirefoxAccount.fromJson(saved)
        } else {
            FirefoxAccount(config())
        }
        account = restored
        return restored
    }

    /** URL to open in the in-app sign-in WebView. */
    fun beginSignIn(): String? {
        lastError = null
        return try {
            val state = account().processEvent(
                FxaEvent.BeginOAuthFlow("sync", listOf(SCOPE_PROFILE, SCOPE_OLDSYNC), "browser"),
            )
            val url = (state as? FxaState.Authenticating)?.oauthUrl
                ?: throw IllegalStateException("OAuth start returned ${state.javaClass.simpleName}")
            saveState()
            android.util.Log.i("HZ-FxA", "BeginOAuthFlow ok")
            url
        } catch (e: Throwable) {
            lastError = e.message ?: e.javaClass.simpleName
            android.util.Log.e("HZ-FxA", "beginOauthFlow failed: $lastError", e)
            null
        }
    }

    fun completeSignIn(code: String, state: String): Boolean = try {
        account().processEvent(FxaEvent.CompleteOAuthFlow(code, state))
        saveState()
        refreshProfile()
        true
    } catch (e: Throwable) {
        lastError = e.message ?: e.javaClass.simpleName
        android.util.Log.e("HZ-FxA", "completeOauthFlow failed: $lastError", e)
        false
    }

    /** Pulls email, display name and avatar URL into local prefs. */
    fun refreshProfile() {
        if (!isSignedIn) return
        try {
            val profile = account().getProfile(true)
            prefs.edit()
                .putString(KEY_EMAIL, profile.email)
                .putString(KEY_DISPLAY_NAME, profile.displayName)
                .putString(KEY_AVATAR, profile.avatar)
                .apply()
        } catch (e: Throwable) {
            lastError = e.message ?: e.javaClass.simpleName
            android.util.Log.e("HZ-FxA", "getProfile failed: $lastError", e)
        }
    }

    fun signOut() {
        try { account()?.disconnect() } catch (_: Throwable) {}
        account?.close()
        account = null
        prefs.edit().remove(KEY_STATE).remove(KEY_EMAIL).remove(KEY_AVATAR)
            .remove(KEY_DISPLAY_NAME).remove(KEY_LAST_SYNC).apply()
    }

    private fun saveState() {
        try {
            prefs.edit().putString(KEY_STATE, account()?.toJson()).apply()
        } catch (_: FxaException) {}
    }

    fun handleIntent(data: android.net.Uri?): Boolean {
        if (data == null) return false
        val code = data.getQueryParameter("code") ?: return false
        val state = data.getQueryParameter("state") ?: return false
        return completeSignIn(code, state)
    }

    /** Pull the remote record, merge it locally, then push the merged result. */
    fun syncNow(snapshot: SpaceSyncSnapshot): SpaceSyncSnapshot? {
        if (!isSignedIn) return null
        return try {
            val info = account().getAccessToken(SCOPE_OLDSYNC, false)
            val scopedKey = info.key?.k ?: run {
                val message = "No sync key for this account"
                lastError = message
                android.util.Log.e("HZ-FxA", message)
                return null
            }

            // The FxA token server hands out the storage node plus the Hawk
            // credentials for it. Its own URL is not a storage endpoint, and
            // the storage node does not accept the OAuth Bearer token.
            val tokenUrl = URL(account().getTokenServerEndpointUrl())
            val tokenConn = tokenUrl.openConnection() as HttpURLConnection
            tokenConn.connectTimeout = 15000
            tokenConn.readTimeout = 15000
            tokenConn.setRequestProperty("Authorization", "Bearer ${info.token}")
            tokenConn.setRequestProperty("Accept", "application/json")
            val tokenCode = tokenConn.responseCode
            if (tokenCode != 200) {
                lastError = "Token server HTTP $tokenCode"
                tokenConn.disconnect()
                return null
            }
            val tokens = readJson(tokenConn, "Token server")
            tokenConn.disconnect()
            val node = tokens.getString("node").trimEnd('/')
            val syncId = tokens.getString("id")
            val hawkKey = decodeHawkKey(tokens.getString("key"))

            val endpoint = "$node/storage/$COLLECTION_NAME/$RECORD_ID"

            // Pull first, merge, then push the merged record. Pushing our local
            // snapshot first would overwrite anything another device wrote.
            val getUrl = URL(endpoint)
            val get = getUrl.openConnection() as HttpURLConnection
            get.connectTimeout = 15000
            get.readTimeout = 15000
            get.setRequestProperty("Authorization", hawk("GET", getUrl, "", syncId, hawkKey))
            get.setRequestProperty("Accept", "application/json")
            val getCode = get.responseCode
            val remoteJson = if (getCode == 200) readJson(get, "Sync storage") else null
            get.disconnect()
            if (getCode !in listOf(200, 404)) {
                lastError = "Sync download HTTP $getCode"
                android.util.Log.e("HZ-FxA", lastError ?: "unknown")
                return null
            }
            val remote = remoteJson?.let { obj ->
                decrypt(obj.optString("payload"), scopedKey)?.let { SpaceSyncSnapshot.fromJson(it) }
            }
            val merged = if (remote != null) SpaceSyncSnapshot.merge(snapshot, remote) else snapshot

            val body = JSONObject().apply {
                put("id", RECORD_ID)
                put("payload", encrypt(merged.toJson(), scopedKey))
            }.toString()
            val putUrl = URL(endpoint)
            val put = putUrl.openConnection() as HttpURLConnection
            put.requestMethod = "PUT"
            put.connectTimeout = 15000
            put.readTimeout = 15000
            put.setRequestProperty("Authorization", hawk("PUT", putUrl, body, syncId, hawkKey))
            put.setRequestProperty("Content-Type", "application/json")
            put.doOutput = true
            put.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val putCode = put.responseCode
            put.disconnect()
            if (putCode !in 200..299) {
                lastError = "Sync upload HTTP $putCode"
                android.util.Log.e("HZ-FxA", lastError ?: "unknown")
                return null
            }
            lastError = null
            lastSyncAt = System.currentTimeMillis()
            merged
        } catch (e: Throwable) {
            lastError = e.message ?: e.javaClass.simpleName
            android.util.Log.e("HZ-FxA", "sync failed: $lastError", e)
            null
        }
    }

    /** Hawk 1.0 authorization header for a Sync 1.5 storage request. */
    private fun hawk(method: String, url: URL, payload: String, id: String, key: ByteArray): String {
        val ts = (System.currentTimeMillis() / 1000L).toString()
        val nonce = b64url(ByteArray(8).also { java.security.SecureRandom().nextBytes(it) })
        val hash = if (method == "GET" || method == "HEAD") null else
            b64url(MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8)))
        val resource = url.path.ifEmpty { "/" } + (url.query?.let { "?$it" } ?: "")
        val port = if (url.port != -1) url.port else if (url.protocol.equals("https", true)) 443 else 80
        // scheme, ts, nonce, method, resource, host, port, hash, app, ext
        val normalized = listOf(
            "hawk.1.header", ts, nonce, method, resource,
            url.host.lowercase(), port.toString(), hash ?: "", "", "",
        ).joinToString("\n", postfix = "\n")
        val mac = b64url(Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key, "HmacSHA256"))
        }.doFinal(normalized.toByteArray(Charsets.UTF_8)))
        return buildString {
            append("Hawk id=\"").append(id).append("\", ts=\"").append(ts)
            append("\", nonce=\"").append(nonce).append("\", mac=\"").append(mac).append('"')
            if (hash != null) append(", hash=\"").append(hash).append('"')
        }
    }

    /** Reads a JSON response, with a useful error when a server returns HTML. */
    private fun readJson(conn: HttpURLConnection, label: String): JSONObject {
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val type = conn.contentType.orEmpty()
        if (!type.contains("json", ignoreCase = true)) {
            throw IllegalStateException("$label returned $type: ${body.take(140)}")
        }
        return try {
            JSONObject(body)
        } catch (e: Throwable) {
            throw IllegalStateException("$label returned invalid JSON: ${body.take(140)}", e)
        }
    }

    /** Token-server keys are base64url, but some servers emit standard base64. */
    private fun decodeHawkKey(value: String): ByteArray = try {
        android.util.Base64.decode(
            value,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
    } catch (_: Throwable) {
        android.util.Base64.decode(value, android.util.Base64.NO_WRAP)
    }

    companion object {
        const val SCOPE_PROFILE = "profile"
        // Firefox Accounts expects the full URL form; the bare host form is
        // rejected by the authorization server ("Unexpected error").
        const val SCOPE_OLDSYNC = "https://identity.mozilla.com/apps/oldsync"
        private const val COLLECTION_NAME = "workspaces"
        private const val RECORD_ID = "spaces"
        private const val KEY_STATE = "fxa_state"
        private const val KEY_EMAIL = "fxa_email"
        private const val KEY_AVATAR = "fxa_avatar"
        private const val KEY_DISPLAY_NAME = "fxa_display_name"
        private const val KEY_LAST_SYNC = "fxa_last_sync"

        private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        private fun b64url(bytes: ByteArray): String {
            val out = StringBuilder()
            var i = 0
            while (i < bytes.size) {
                val b0 = bytes[i].toInt() and 0xFF
                val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
                val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
                out.append(B64[b0 shr 2])
                out.append(B64[((b0 and 3) shl 4) or (b1 shr 4)])
                if (i + 1 < bytes.size) out.append(B64[((b1 and 15) shl 2) or (b2 shr 6)])
                if (i + 2 < bytes.size) out.append(B64[b2 and 63])
                i += 3
            }
            return out.toString()
        }

        private fun unb64url(s: String): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < s.length) {
                val c0 = B64.indexOf(s[i]).coerceAtLeast(0)
                val c1 = if (i + 1 < s.length) B64.indexOf(s[i + 1]).coerceAtLeast(0) else 0
                val c2 = if (i + 2 < s.length) B64.indexOf(s[i + 2]).coerceAtLeast(0) else 0
                val c3 = if (i + 3 < s.length) B64.indexOf(s[i + 3]).coerceAtLeast(0) else 0
                out.write((c0 shl 2) or (c1 shr 4))
                if (i + 2 < s.length) out.write(((c1 and 15) shl 4) or (c2 shr 2))
                if (i + 3 < s.length) out.write(((c2 and 3) shl 6) or c3)
                i += 4
            }
            return out.toByteArray()
        }

        /** HKDF-Extract + Expand, as used by Firefox's key bundle. */
        private fun hkdf(ikm: ByteArray, info: String, length: Int = 32): ByteArray {
            val salt = ByteArray(32)
            val prk = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(salt, "HmacSHA256"))
            }.doFinal(ikm)
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }
            val out = ByteArray(length)
            var block = ByteArray(0)
            var pos = 0
            var counter = 1
            while (pos < length) {
                block = mac.doFinal(block + info.toByteArray(Charsets.UTF_8) + byteArrayOf(counter.toByte()))
                val n = minOf(block.size, length - pos)
                System.arraycopy(block, 0, out, pos, n)
                pos += n
                counter++
            }
            return out
        }

        /** JWCrypto-style envelope: AES-256-GCM payload + HMAC-SHA256. */
        fun encrypt(plain: String, scopedKey: String): String {
            val rawKey = unb64url(scopedKey)
            val encKey = SecretKeySpec(hkdf(rawKey, "encyption"), "AES")
            val hmacKey = hkdf(rawKey, "authentication")
            val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, encKey, GCMParameterSpec(128, iv))
            }
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val hmac = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(hmacKey, "HmacSHA256"))
            }.doFinal(ct)
            return JSONObject().apply {
                put("ciphertext", b64url(ct))
                put("IV", b64url(iv))
                put("hmac", b64url(hmac))
            }.toString()
        }

        fun decrypt(envelope: String, scopedKey: String): String? {
          return try {
            val o = JSONObject(envelope)
            val rawKey = unb64url(scopedKey)
            val encKey = SecretKeySpec(hkdf(rawKey, "encyption"), "AES")
            val hmacKey = hkdf(rawKey, "authentication")
            val ct = unb64url(o.optString("ciphertext"))
            val expected = unb64url(o.optString("hmac"))
            val actual = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(hmacKey, "HmacSHA256"))
            }.doFinal(ct)
            if (!MessageDigest.isEqual(expected, actual)) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, encKey, GCMParameterSpec(128, unb64url(o.optString("IV"))))
            }
            String(cipher.doFinal(ct), Charsets.UTF_8)
          } catch (_: Throwable) {
            null
          }
        }
    }
}
