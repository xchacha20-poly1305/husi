package fr.husi.ui.tools

import fr.husi.net.withAgeIdentities
import io.github.xchacha20_poly1305.yan.Age
import io.github.xchacha20_poly1305.yan.Armor
import io.github.xchacha20_poly1305.yan.KeyFormatException
import io.github.xchacha20_poly1305.yan.Recipient

internal class BackupEncryption(val recipient: Recipient, val armor: Boolean)

internal class EncryptedBackup private constructor(
    private val bytes: ByteArray,
    private val armor: Boolean,
) {
    fun decrypt(text: String): ByteArray = withAgeIdentities(text) { identities ->
        if (identities.size != 1) throw KeyFormatException("Expected one age identity")
        Age.decryptBytes(bytes, identities, armor)
    }

    companion object {
        private val binaryHeader = "age-encryption.org/v1\n".encodeToByteArray()
        private val armorHeader = Armor.BEGIN_LINE.encodeToByteArray()

        fun detect(bytes: ByteArray): EncryptedBackup? {
            if (bytes.startsWith(binaryHeader)) return EncryptedBackup(bytes, armor = false)
            // yan allows ASCII whitespace before the armor header.
            val start = bytes.indexOfFirst { byte -> byte != 32.toByte() && byte !in 9..13 }
            if (start >= 0 && bytes.startsWith(armorHeader, start)) {
                return EncryptedBackup(bytes, armor = true)
            }
            return null
        }

        private fun ByteArray.startsWith(prefix: ByteArray, offset: Int = 0): Boolean =
            size - offset >= prefix.size && prefix.indices.all { this[offset + it] == prefix[it] }
    }
}
