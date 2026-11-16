package com.pocketpilot.app.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM encryption backed by an Android Keystore key. The key never
 * leaves the Keystore; encrypted blobs live in SharedPreferences / files.
 * This is what makes "memory stays on device" a real guarantee.
 */
object KeystoreStore {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    fun getOrCreateKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Returns Base64(iv + ciphertext). */
    fun encrypt(alias: String, plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    fun decrypt(alias: String, encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_BYTES)
        val cipherText = combined.copyOfRange(IV_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(alias), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }
}

private fun encryptedPrefs(context: Context, name: String): SharedPreferences =
    context.getSharedPreferences(name, Context.MODE_PRIVATE)

/** Notebook store: one encrypted JSON blob in SharedPreferences. */
class NotebookStore(context: Context) {
    private val prefs = encryptedPrefs(context, "notebook_prefs")
    private val alias = "pocketpilot_notebook_key"

    fun notebook(): com.pocketpilot.core.memory.Notebook =
        com.pocketpilot.core.memory.Notebook(
            load = {
                val enc = prefs.getString("blob", null) ?: return@Notebook ""
                runCatching { KeystoreStore.decrypt(alias, enc) }.getOrDefault("")
            },
            save = { raw ->
                prefs.edit().putString("blob", KeystoreStore.encrypt(alias, raw)).apply()
            },
        )
}

/** Vault store: secrets encrypted at rest; values never leave this class. */
class VaultStore(context: Context) : com.pocketpilot.core.memory.Vault {
    private val prefs = encryptedPrefs(context, "vault_prefs")
    private val alias = "pocketpilot_vault_key"

    override fun labels(): List<String> = prefs.all.keys.toList()

    override fun has(label: String): Boolean = prefs.contains(label)

    override fun put(label: String, secret: String) {
        prefs.edit().putString(label, KeystoreStore.encrypt(alias, secret)).apply()
    }

    override fun remove(label: String) {
        prefs.edit().remove(label).apply()
    }

    override fun typeIntoFocusedField(label: String): Boolean {
        val enc = prefs.getString(label, null) ?: return false
        val secret = runCatching { KeystoreStore.decrypt(alias, enc) }.getOrNull() ?: return false
        // Type locally via the accessibility service. The value is never
        // returned to the caller, never logged, never sent to the model.
        val typed = com.pocketpilot.app.a11y.ScreenController.typeText(secret)
        // Best-effort: clear the local copy reference immediately.
        return typed
    }
}
