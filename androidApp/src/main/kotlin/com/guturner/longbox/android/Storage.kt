package com.guturner.longbox.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.guturner.longbox.data.local.KeyValueStore
import com.guturner.longbox.data.session.StoredTokens
import com.guturner.longbox.data.session.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * App-private files (MASVS-STORAGE: only this app's sandbox can read them). Writes are atomic,
 * so a crash mid-write never corrupts the outbox.
 */
class FileKeyValueStore(private val dir: File) : KeyValueStore {
    init { dir.mkdirs() }

    private fun file(key: String) = AtomicFile(File(dir, "$key.json"))

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { file(key).readFully().toString(Charsets.UTF_8) }.getOrNull()
    }

    override suspend fun write(key: String, value: String) = withContext(Dispatchers.IO) {
        val f = file(key)
        val out = f.startWrite()
        try {
            out.write(value.toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
            throw e
        }
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) { file(key).delete() }
}

/**
 * Server tokens encrypted with an AES-256-GCM key that lives in the Android Keystore and never
 * leaves it (Android "Hardcoded cryptographic secrets" guidance; OWASP MASVS-STORAGE/CRYPTO).
 */
class KeystoreTokenStore(context: Context) : TokenStore {

    private val file = AtomicFile(File(context.noBackupFilesDir, "tokens.bin"))

    override suspend fun load(): StoredTokens? = withContext(Dispatchers.IO) {
        val bytes = runCatching { file.readFully() }.getOrNull() ?: return@withContext null
        runCatching {
            val buffer = ByteBuffer.wrap(bytes)
            val iv = ByteArray(buffer.get().toInt()).also { buffer.get(it) }
            val cipherText = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            val (access, refresh) = cipher.doFinal(cipherText).toString(Charsets.UTF_8).split('\n', limit = 2)
            StoredTokens(access, refresh)
        }.getOrNull()
    }

    override suspend fun save(tokens: StoredTokens) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val cipherText = cipher.doFinal("${tokens.accessToken}\n${tokens.refreshToken}".toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val out = file.startWrite()
        try {
            out.write(byteArrayOf(iv.size.toByte()) + iv + cipherText)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val ALIAS = "longbox-session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
