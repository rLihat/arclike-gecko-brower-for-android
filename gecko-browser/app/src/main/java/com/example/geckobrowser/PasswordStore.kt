package com.example.geckobrowser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A login the user has chosen to keep in horiZen's own vault. */
data class SavedLogin(
    val id: String,
    val host: String,
    val username: String,
    val password: String,
    val updatedAt: Long,
)

/** The vault's contents, kept in one encrypted file. */
data class PasswordVault(
    val logins: List<SavedLogin> = emptyList(),
    val neverSave: Set<String> = emptySet(),
    val autofillEnabled: Boolean = true,
    val offerToSave: Boolean = true,
)

/**
 * A small, self-contained password store. Everything is encrypted at rest with
 * an AES-256 key that lives in the Android Keystore, so the file on disk is
 * useless without this app on this device. There is no master password and no
 * cloud sync; horiZen only fills what the user has saved here.
 */
object PasswordStore {

    private const val ALIAS = "horizen_logins"
    private const val FILE_NAME = "logins.enc"
    private const val IV_LEN = 12

    private val lock = Any()

    /** Reads the vault, returning an empty one if it cannot be read. */
    fun load(context: Context): PasswordVault = synchronized(lock) {
        val file = file(context)
        if (!file.exists()) return PasswordVault()
        val blob = try { file.readBytes() } catch (_: Throwable) { return PasswordVault() }
        if (blob.size <= IV_LEN) return PasswordVault()
        val iv = blob.copyOfRange(0, IV_LEN)
        val ct = blob.copyOfRange(IV_LEN, blob.size)
        val json = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Throwable) {
            return PasswordVault()
        }
        return try { fromJson(json) } catch (_: Throwable) { PasswordVault() }
    }

    /** Writes the vault atomically. */
    fun save(context: Context, vault: PasswordVault) = synchronized(lock) {
        val json = toJson(vault)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val out = iv + ct
        val file = file(context)
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            tmp.writeBytes(out)
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                file.writeBytes(out)
                tmp.delete()
            }
        } catch (_: Throwable) {
            tmp.delete()
        }
    }

    fun all(context: Context): List<SavedLogin> = load(context).logins

    fun forHost(context: Context, host: String): List<SavedLogin> =
        all(context).filter { matchesHost(it.host, host) }

    fun upsert(context: Context, login: SavedLogin) {
        val vault = load(context)
        val updated = vault.logins.filterNot { it.id == login.id } + login
        save(context, vault.copy(logins = updated.sortedBy { it.host + it.username }))
    }

    fun remove(context: Context, id: String) {
        val vault = load(context)
        save(context, vault.copy(logins = vault.logins.filterNot { it.id == id }))
    }

    fun setAutofillEnabled(context: Context, enabled: Boolean) {
        val vault = load(context)
        save(context, vault.copy(autofillEnabled = enabled))
    }

    fun isAutofillEnabled(context: Context): Boolean = load(context).autofillEnabled

    fun setOfferToSave(context: Context, enabled: Boolean) {
        val vault = load(context)
        save(context, vault.copy(offerToSave = enabled))
    }

    fun isOfferToSave(context: Context): Boolean = load(context).offerToSave

    fun setNeverSave(context: Context, host: String, never: Boolean) {
        val vault = load(context)
        val set = vault.neverSave.toMutableSet()
        if (never) set.add(host) else set.remove(host)
        save(context, vault.copy(neverSave = set))
    }

    fun isNeverSave(context: Context, host: String): Boolean =
        load(context).neverSave.contains(host)

    /** True when a saved host matches a page host (exact or subdomain). */
    fun matchesHost(savedHost: String, pageHost: String): Boolean {
        if (savedHost.isBlank() || pageHost.isBlank()) return false
        val a = savedHost.lowercase()
        val b = pageHost.lowercase()
        return a == b || b.endsWith(".$a") || a.endsWith(".$b")
    }

    fun newId(): String = java.util.UUID.randomUUID().toString()

    fun generatePassword(length: Int = 16, symbols: Boolean = true): String {
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lower = "abcdefghijkmnopqrstuvwxyz"
        val digits = "23456789"
        val syms = "!@#\$%^&*()-_=+[]{}"
        val pool = upper + lower + digits + if (symbols) syms else ""
        val rnd = SecureRandom()
        val sb = StringBuilder(length)
        repeat(length) { sb.append(pool[rnd.nextInt(pool.length)]) }
        return sb.toString()
    }

    private fun file(context: Context): File =
        File(context.filesDir, FILE_NAME)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(ALIAS, null)?.let { return it as SecretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }

    private fun toJson(vault: PasswordVault): String {
        val arr = JSONArray()
        vault.logins.forEach { login ->
            arr.put(JSONObject().apply {
                put("id", login.id)
                put("host", login.host)
                put("username", login.username)
                put("password", login.password)
                put("updated", login.updatedAt)
            })
        }
        val never = JSONArray()
        vault.neverSave.forEach { never.put(it) }
        return JSONObject().apply {
            put("version", 1)
            put("autofillEnabled", vault.autofillEnabled)
            put("offerToSave", vault.offerToSave)
            put("logins", arr)
            put("neverSave", never)
        }.toString()
    }

    private fun fromJson(raw: String): PasswordVault {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("logins") ?: JSONArray()
        val logins = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    SavedLogin(
                        id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                        host = o.optString("host"),
                        username = o.optString("username"),
                        password = o.optString("password"),
                        updatedAt = o.optLong("updated", 0L),
                    )
                )
            }
        }
        val neverArr = root.optJSONArray("neverSave") ?: JSONArray()
        val never = buildSet {
            for (i in 0 until neverArr.length()) {
                val s = neverArr.optString(i)
                if (s.isNotBlank()) add(s)
            }
        }
        return PasswordVault(
            logins = logins,
            neverSave = never,
            autofillEnabled = root.optBoolean("autofillEnabled", true),
            offerToSave = root.optBoolean("offerToSave", true),
        )
    }
}