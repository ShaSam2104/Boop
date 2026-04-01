package com.shashsam.boop.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class BackupCryptoTest {

    @Test
    fun `encrypt and decrypt round trip`() {
        val plaintext = "Hello, Boop backup!".toByteArray()
        val password = "test-password-123"

        val encrypted = BackupCrypto.encrypt(plaintext, password)
        val decrypted = BackupCrypto.decrypt(encrypted, password)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypted output starts with magic and version`() {
        val encrypted = BackupCrypto.encrypt("test".toByteArray(), "pass")

        assertEquals('B'.code.toByte(), encrypted[0])
        assertEquals('O'.code.toByte(), encrypted[1])
        assertEquals('O'.code.toByte(), encrypted[2])
        assertEquals('P'.code.toByte(), encrypted[3])
        assertEquals(0x01.toByte(), encrypted[4])
    }

    @Test
    fun `encrypted output is larger than plaintext`() {
        val plaintext = "short".toByteArray()
        val encrypted = BackupCrypto.encrypt(plaintext, "pass")

        // Header: 4 (magic) + 1 (version) + 16 (salt) + 12 (IV) = 33 bytes minimum overhead
        assertTrue("encrypted size should be > plaintext + 33", encrypted.size > plaintext.size + 33)
    }

    @Test(expected = AEADBadTagException::class)
    fun `decrypt with wrong password throws AEADBadTagException`() {
        val encrypted = BackupCrypto.encrypt("secret data".toByteArray(), "correct-password")
        BackupCrypto.decrypt(encrypted, "wrong-password")
    }

    @Test(expected = BadMagicException::class)
    fun `decrypt invalid data throws BadMagicException`() {
        val garbage = "this is not a backup file".toByteArray()
        BackupCrypto.decrypt(garbage, "password")
    }

    @Test(expected = BadMagicException::class)
    fun `decrypt too-short data throws BadMagicException`() {
        val tiny = byteArrayOf(1, 2, 3)
        BackupCrypto.decrypt(tiny, "password")
    }

    @Test(expected = UnsupportedVersionException::class)
    fun `decrypt future version throws UnsupportedVersionException`() {
        val encrypted = BackupCrypto.encrypt("data".toByteArray(), "pass")
        // Patch version byte to a future version
        encrypted[4] = 0x99.toByte()
        BackupCrypto.decrypt(encrypted, "pass")
    }

    @Test
    fun `different passwords produce different ciphertext`() {
        val plaintext = "same data".toByteArray()
        val enc1 = BackupCrypto.encrypt(plaintext, "password1")
        val enc2 = BackupCrypto.encrypt(plaintext, "password2")

        // Different salt + different key → different ciphertext
        val ct1 = enc1.drop(33).toByteArray()
        val ct2 = enc2.drop(33).toByteArray()
        assertTrue("ciphertext should differ", !ct1.contentEquals(ct2))
    }

    @Test
    fun `empty plaintext round trip`() {
        val plaintext = byteArrayOf()
        val encrypted = BackupCrypto.encrypt(plaintext, "pass")
        val decrypted = BackupCrypto.decrypt(encrypted, "pass")
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `large plaintext round trip`() {
        val plaintext = ByteArray(1_000_000) { (it % 256).toByte() }
        val encrypted = BackupCrypto.encrypt(plaintext, "pass")
        val decrypted = BackupCrypto.decrypt(encrypted, "pass")
        assertArrayEquals(plaintext, decrypted)
    }
}
