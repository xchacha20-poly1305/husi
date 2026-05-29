package fr.husi.fmt

import org.jetbrains.compose.resources.StringResource

sealed interface ValidateResult {
    class Secure private constructor(internal val continueChecking: Boolean) : ValidateResult {
        companion object {
            val Continue = Secure(continueChecking = true)
            val Stop = Secure(continueChecking = false)
        }
    }

    class Deprecated(val textRes: StringResource) : ValidateResult
    class Insecure(val textRes: StringResource) : ValidateResult
}
