package fr.husi.net

import io.github.xchacha20_poly1305.yan.Age
import io.github.xchacha20_poly1305.yan.Identity
import io.github.xchacha20_poly1305.yan.KeyFormatException
import java.io.Closeable

internal fun isValidAgeIdentityInput(text: String): Boolean {
    if (text.isBlank()) return true
    return try {
        withAgeIdentities(text) { true }
    } catch (_: KeyFormatException) {
        false
    }
}

internal inline fun <T> withAgeIdentities(text: String, use: (List<Identity>) -> T): T {
    val identities = try {
        Age.parseIdentities(text)
    } catch (_: KeyFormatException) {
        throw KeyFormatException("Invalid age identities")
    }
    try {
        return use(identities)
    } finally {
        for (identity in identities) {
            (identity as? Closeable)?.close()
        }
    }
}
