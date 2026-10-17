package com.nerdproject.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** Android Keystore AES-256-GCM vault. One key per provider. */
class ApiKeyVault(context: Context) {
    private val prefs = context.getSharedPreferences("nerd_secrets", Context.MODE_PRIVATE)
    private val alias = "nerd-provider-key"

    @Synchronized
    fun put(providerId: String, secret: String) {
        if (secret.isBlank()) return
        prefs.edit().putString(keyName(providerId), encrypt(secret)).apply()
    }

    @Synchronized
    fun get(providerId: String): String? {
        val enc = prefs.getString(keyName(providerId), null) ?: return null
        return runCatching { decrypt(enc) }.getOrNull()
    }

    @Synchronized
    fun remove(providerId: String) { prefs.edit().remove(keyName(providerId)).apply() }

    private fun keyName(providerId: String) = "key_$providerId"

    private fun getOrCreateKey(): javax.crypto.SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? javax.crypto.SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String {
        val raw = Base64.decode(enc, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, raw, 0, 12))
        return String(cipher.doFinal(raw, 12, raw.size - 12))
    }
}
