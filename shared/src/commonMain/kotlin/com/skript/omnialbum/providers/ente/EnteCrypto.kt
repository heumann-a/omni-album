package com.skript.omnialbum.providers.ente

import com.ionspin.kotlin.crypto.secretbox.SecretBox
import com.ionspin.kotlin.crypto.secretstream.SecretStream

object EnteCrypto {

    private const val base58Alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun decodeBase58(string: String): ByteArray? {
        val result = mutableListOf<Byte>(0)
        for (char in string) {
            val index = base58Alphabet.indexOf(char)
            if (index < 0) return null
            var carry = index
            for (j in result.indices.reversed()) {
                carry += (result[j].toInt() and 0xFF) * 58
                result[j] = (carry and 0xFF).toByte()
                carry = carry shr 8
            }
            while (carry > 0) {
                result.add(0, (carry and 0xFF).toByte())
                carry = carry shr 8
            }
        }
        for (char in string) {
            if (char != '1') break
            result.add(0, 0)
        }
        return result.toByteArray()
    }

    fun decryptSecretBox(ciphertext: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray? {
        if (nonce.size != 24 || key.size != 32) return null
        return runCatching {
            SecretBox.openEasy(
                ciphertext = ciphertext.toUByteArray(),
                key = key.toUByteArray(),
                nonce = nonce.toUByteArray()
            )?.toByteArray()
        }.getOrNull()
    }

    fun decryptStream(encryptedData: ByteArray, header: ByteArray, key: ByteArray): ByteArray? {
        if (header.size != 24 || key.size != 32) return null
        return runCatching {
            val stateAndHeader = SecretStream.xChaCha20Poly1305InitPull(
                key = key.toUByteArray(),
                header = header.toUByteArray()
            )
            val decryptionChunkSize = 4 * 1024 * 1024 + 17
            val out = ByteArrayOutputStreamCompat(encryptedData.size)
            var offset = 0
            while (offset < encryptedData.size) {
                val end = minOf(offset + decryptionChunkSize, encryptedData.size)
                val chunk = encryptedData.copyOfRange(offset, end)
                val pulled = SecretStream.xChaCha20Poly1305Pull(
                    state = stateAndHeader.state,
                    ciphertext = chunk.toUByteArray()
                )
                out.write(pulled.decryptedData.toByteArray())
                offset = end
            }
            out.toByteArray()
        }.getOrNull()
    }

    private class ByteArrayOutputStreamCompat(initialSize: Int) {
        private var buffer = ByteArray(maxOf(initialSize, 32))
        private var size = 0

        fun write(bytes: ByteArray) {
            if (size + bytes.size > buffer.size) {
                buffer = buffer.copyOf(maxOf(buffer.size * 2, size + bytes.size))
            }
            bytes.copyInto(buffer, size)
            size += bytes.size
        }

        fun toByteArray(): ByteArray = buffer.copyOfRange(0, size)
    }
}
