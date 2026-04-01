package com.shashsam.boop.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption for Boop backup files.
 *
 * Wire format: [4B magic "BOOP"][1B version=0x01][16B salt][12B IV][ciphertext+tag]
 */
object BackupCrypto {

    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_FACTORY = "PBKDF2WithHmacSHA256"

    val MAGIC = byteArrayOf('B'.code.toByte(), 'O'.code.toByte(), 'O'.code.toByte(), 'P'.code.toByte())
    const val VERSION: Byte = 0x01

    /**
     * Encrypts [plaintext] with [password] using AES-256-GCM.
     * Returns the full wire-format byte array (magic + version + salt + IV + ciphertext).
     */
    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        val random = SecureRandom()

        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        // Wire format: MAGIC(4) + VERSION(1) + salt(16) + IV(12) + ciphertext
        val result = ByteArray(MAGIC.size + 1 + SALT_LENGTH + IV_LENGTH + ciphertext.size)
        var offset = 0
        System.arraycopy(MAGIC, 0, result, offset, MAGIC.size); offset += MAGIC.size
        result[offset] = VERSION; offset += 1
        System.arraycopy(salt, 0, result, offset, SALT_LENGTH); offset += SALT_LENGTH
        System.arraycopy(iv, 0, result, offset, IV_LENGTH); offset += IV_LENGTH
        System.arraycopy(ciphertext, 0, result, offset, ciphertext.size)

        return result
    }

    /**
     * Decrypts a wire-format byte array produced by [encrypt].
     * @throws BadMagicException if the magic bytes don't match
     * @throws UnsupportedVersionException if the version is not supported
     * @throws javax.crypto.AEADBadTagException if the password is wrong
     */
    fun decrypt(data: ByteArray, password: String): ByteArray {
        if (data.size < MAGIC.size + 1 + SALT_LENGTH + IV_LENGTH) {
            throw BadMagicException("File too small to be a valid backup")
        }

        var offset = 0

        // Verify magic
        val magic = data.copyOfRange(offset, offset + MAGIC.size); offset += MAGIC.size
        if (!magic.contentEquals(MAGIC)) {
            throw BadMagicException("Not a Boop backup file")
        }

        // Verify version (unsigned comparison — bytes are signed in JVM)
        val version = data[offset].toInt() and 0xFF; offset += 1
        if (version > (VERSION.toInt() and 0xFF)) {
            throw UnsupportedVersionException("Backup was created by a newer version of Boop (v$version)")
        }

        val salt = data.copyOfRange(offset, offset + SALT_LENGTH); offset += SALT_LENGTH
        val iv = data.copyOfRange(offset, offset + IV_LENGTH); offset += IV_LENGTH
        val ciphertext = data.copyOfRange(offset, data.size)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(KEY_FACTORY)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}

class BadMagicException(message: String) : Exception(message)
class UnsupportedVersionException(message: String) : Exception(message)
