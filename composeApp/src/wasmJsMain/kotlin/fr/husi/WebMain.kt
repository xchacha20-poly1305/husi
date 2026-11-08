@file:OptIn(ExperimentalComposeUiApi::class)

package fr.husi

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import fr.husi.di.browserPlatformModule
import fr.husi.di.remoteApplicationModule
import fr.husi.ui.WebApp
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

fun main() {
    ComposeViewport {
        KoinApplication(
            configuration = koinConfiguration(
                declaration = {
                    modules(
                        browserPlatformModule,
                        remoteApplicationModule
                    )
                }
            ),
            content = {
                WebApp()
            }
        )
    }
}
