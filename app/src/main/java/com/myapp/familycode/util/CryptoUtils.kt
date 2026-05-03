package com.myapp.familycode.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    /**
     * Derives a 256-bit AES key from the provided API key string using SHA-256.
     */
    private fun getSecretKey(apiKey: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(apiKey.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts the plaintext using AES/GCM and returns a Base64 encoded string
     * containing the IV prepended to the ciphertext.
     */
    fun encrypt(plainText: String, apiKey: String): String {
        try {
            if (plainText.isBlank()) return plainText

            val secretKey = getSecretKey(apiKey)
            val cipher = Cipher.getInstance(ALGORITHM)

            val iv = ByteArray(IV_LENGTH_BYTE)
            SecureRandom().nextBytes(iv)
            val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return plainText // Fallback to plaintext if encryption fails
        }
    }

    /**
     * Decrypts a Base64 encoded string (with prepended IV) back to plaintext.
     * If decryption fails (e.g., incorrect key or data is actually plaintext),
     * it returns the original string as a fallback.
     */
    fun decrypt(cipherTextString: String, apiKey: String): String {
        try {
            if (cipherTextString.isBlank()) return cipherTextString

            // Quick check: if it's not base64, decoding will throw or return weird bytes.
            // But if it is plaintext, it will throw an exception during decryption.
            val combined = Base64.decode(cipherTextString, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH_BYTE) return cipherTextString

            val iv = combined.copyOfRange(0, IV_LENGTH_BYTE)
            val cipherText = combined.copyOfRange(IV_LENGTH_BYTE, combined.size)

            val secretKey = getSecretKey(apiKey)
            val cipher = Cipher.getInstance(ALGORITHM)
            val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
            val plainTextBytes = cipher.doFinal(cipherText)

            return String(plainTextBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // E.g., IllegalArgumentException from Base64, or AEADBadTagException from Cipher
            return cipherTextString // Fallback to plaintext
        }
    }
}
