package com.example.geckobrowser

import android.content.Context
import android.content.SharedPreferences
import mozilla.appservices.fxaclient.FxaConfig
import mozilla.appservices.fxaclient.FxaServer
import mozilla.appservices.fxaclient.FxaException
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

    val isSignedIn: Boolean get() = prefs.getString(KEY_STATE, null) != null

    val email: String? get() = prefs.getString(KEY_EMAIL, null)

    var lastSyncAt: Long get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        private set(value) { prefs.edit().putLong(KEY_LAST_SYNC, value).apply() }

    private fun config(): FxaConfig = FxaConfig(
        FxaServer.Release,
        BuildConfig.FXA_CLIENT_ID,
        BuildConfig.FXA_REDIRECT_URI,
        null,
    )

    private fun account(): FirefoxAccount {
        account?.let { return it }
        val saved = prefs.getString(KEY_STATE, null)
        val restored = if (saved != null) {
            FirefoxAccount.fromJson(saved)
        } else {
            FirefoxAccount(config())
        }
        account = restored
        return restored
    }

    /** URL to open in a Custom Tab; the redirect comes back to MainActivity. */
    fun beginSignIn(): String? = try {
        val url = account().beginOauthFlow(
            listOf(SCOPE_OLDSYNC),
            "browser",
        )
        saveState()
        url
    } catch (_: FxaException) {
        null
    }

    fun completeSignIn(code: String, state: String): Boolean = try {
        account().completeOauthFlow(code, state)
        saveState()
        val profile = account().getProfile(false)
        prefs.edit().putString(KEY_EMAIL, profile.email).apply()
        true
    } catch (_: FxaException) {
        false
    }

    fun signOut() {
        try { account()?.disconnect() } catch (_: Throwable) {}
        account?.close()
        account = null
        prefs.edit().remove(KEY_STATE).remove(KEY_EMAIL).remove(KEY_LAST_SYNC).apply()
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

    /** Push local spaces to Sync, then pull and return the merged record. */
    fun syncNow(snapshot: SpaceSyncSnapshot): SpaceSyncSnapshot? {
        if (!isSignedIn) return null
        return try {
            val info = account().getAccessToken(SCOPE_OLDSYNC, null)
            val key = info.key?.k ?: return null
            val tokenServer = account().getTokenServerEndpointUrl().trimEnd('/')
            val collection = "workspaces"
            val syncId = COLLECTION_ID

            val envelope = encrypt(snapshot.toJson(), key)
            val bso = JSONObject().apply {
                put("id", RECORD_ID)
                put("payload", envelope)
            }.toString()

            // Upload the single record.
            val url = URL("$tokenServer/1.5/$syncId/storage/$collection/$RECORD_ID")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", "Bearer ${info.token}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(bso.toByteArray(Charsets.UTF_8)) }
            conn.responseCode // consume
            conn.disconnect()

            // Pull the record back (a device may have pushed newer data).
            val get = URL("$tokenServer/1.5/$syncId/storage/$collection/$RECORD_ID").openConnection() as HttpURLConnection
            get.setRequestProperty("Authorization", "Bearer ${info.token}")
            val code = get.responseCode
            val raw = if (code == 200) get.inputStream.bufferedReader().use { it.readText() } else null
            get.disconnect()
            val remote = raw?.let { body ->
                val obj = JSONObject(body)
                decrypt(obj.optString("payload"), key)?.let { SpaceSyncSnapshot.fromJson(it) }
            }
            lastSyncAt = System.currentTimeMillis()
            if (remote != null) SpaceSyncSnapshot.merge(snapshot, remote) else snapshot
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        const val SCOPE_OLDSYNC = "https://identity.mozilla.com/apps/oldsync"
        private const val COLLECTION_ID = "spaces"
        private const val RECORD_ID = "spaces"
        private const val KEY_STATE = "fxa_state"
        private const val KEY_EMAIL = "fxa_email"
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
