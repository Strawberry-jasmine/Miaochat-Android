package com.example.relaychat.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ApiKeyVault {
    suspend fun read(): String

    suspend fun write(value: String)
}

class KeystoreFileApiKeyVault(
    context: Context,
) : ApiKeyVault {
    private val storageFile = File(context.noBackupFilesDir, "relaychat-api-key.bin")

    override suspend fun read(): String = withContext(Dispatchers.IO) {
        if (!storageFile.exists()) {
            return@withContext ""
        }

        runCatching {
            val bytes = storageFile.readBytes()
            if (bytes.size <= GCM_IV_LENGTH_BYTES) {
                ""
            } else {
                val iv = bytes.copyOfRange(0, GCM_IV_LENGTH_BYTES)
                val cipherText = bytes.copyOfRange(GCM_IV_LENGTH_BYTES, bytes.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
            }
        }.getOrElse { "" }
    }

    override suspend fun write(value: String) = withContext(Dispatchers.IO) {
        if (value.isBlank()) {
            storageFile.delete()
            return@withContext
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        storageFile.writeBytes(cipher.iv + encrypted)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply {
            load(null)
        }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "relaychat_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
