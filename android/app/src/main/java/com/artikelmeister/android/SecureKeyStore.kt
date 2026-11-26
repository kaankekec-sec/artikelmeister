package com.artikelmeister.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed AES-GCM secret storage using AndroidKeyStore.
 * Used to persist user-supplied Mistral API keys and optional proxy client tokens
 * without embedding any secrets in source code.
 */
class SecureKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun putSecret(key: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            removeSecret(key)
            return
        }
        val encrypted = encrypt(trimmed)
        if (encrypted != null) {
            prefs.edit().putString(key, encrypted).apply()
        }
    }

    fun getSecret(key: String): String {
        val encoded = prefs.getString(key, null) ?: return ""
        return decrypt(encoded) ?: ""
    }

    fun removeSecret(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
    }.getOrNull()

    private fun encrypt(plaintext: String): String? = runCatching {
        val secretKey = getOrCreateKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        "$ivB64:$ctB64"
    }.getOrNull()

    private fun decrypt(payload: String): String? = runCatching {
        val parts = payload.split(":")
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val secretKey = getOrCreateKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "artikelmeister_mistral_aes_key"
        const val PREFS_NAME = "com.artikelmeister.secure_prefs"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
