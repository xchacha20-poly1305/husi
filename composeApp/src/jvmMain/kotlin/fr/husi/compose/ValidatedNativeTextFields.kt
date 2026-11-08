package fr.husi.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Text
import fr.husi.ktx.parseGoDuration
import fr.husi.ktx.readableMessage
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.cleartext_http_warning
import io.github.xchacha20_poly1305.kpuri.Url

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
) {
    fun validate(text: String): String? {
        if (text.isBlank()) {
            return null
        }

        if (text.lines().size > 1) {
            return "Unexpected new line"
        }

        return try {
            parseGoDuration(text)
            null
        } catch (e: Exception) {
            e.readableMessage
        }
    }
    ValidatedTextField(state, onOk, true, validator = ::validate)
}

