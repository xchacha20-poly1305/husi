package fr.husi.compose

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import fr.husi.ktx.parseGoDuration
import fr.husi.ktx.readableMessage
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.cleartext_http_warning
import fr.husi.resources.duration_minimum
import io.github.xchacha20_poly1305.kpuri.Url
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

@Composable
fun LinkOrContentTextField(
    state: TextFieldState,
    onOk: () -> Unit,
    allowMultiLine: Boolean = false,
) {
    suspend fun validate(text: String): String? {
        if (text.isBlank()) {
            return null
        }

        val lines = text.lines()
        if (lines.size > 1 && !allowMultiLine) {
            return "Unexpected new line"
        }

        val errors = linkedSetOf<String>()
        for (link in lines) try {
            val url = Url.parse(link)
            when (url.scheme) {
                "content" -> continue
                "http" -> errors.add(resolveRepository().getString(Res.string.cleartext_http_warning))
            }
        } catch (e: Exception) {
            errors.add(e.readableMessage)
        }

        return if (errors.isNotEmpty()) {
            errors.joinToString("\n")
        } else {
            null
        }
    }
    ValidatedTextField(state, onOk, false, validator = ::validate)
}

@Composable
fun DurationTextField(
    state: TextFieldState,
    onOk: () -> Unit,
    minimum: Duration? = null,
) {
    val minimumError = minimum?.let { stringResource(Res.string.duration_minimum, it.toString()) }

    fun validate(text: String): String? {
        if (text.isBlank()) {
            return null
        }

        if (text.lines().size > 1) {
            return "Unexpected new line"
        }

        return try {
            val duration = parseGoDuration(text)
            if (minimum != null && duration < minimum) {
                minimumError
            } else {
                null
            }
        } catch (e: Exception) {
            e.readableMessage
        }
    }
    ValidatedTextField(state, onOk, true, validator = ::validate)
}

