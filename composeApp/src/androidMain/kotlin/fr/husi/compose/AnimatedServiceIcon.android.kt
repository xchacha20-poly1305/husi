package fr.husi.compose

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.husi.bg.ServiceState
import fr.husi.lib.R

@Composable
actual fun AnimatedServiceIcon(state: ServiceState, contentDescription: String) {
    val animRes = when (state) {
        ServiceState.Connecting -> R.drawable.ic_service_connecting
        ServiceState.Stopping -> R.drawable.ic_service_stopping
        else -> R.drawable.ic_service_stopped
    }
    val animatedVector = AnimatedImageVector.animatedVectorResource(animRes)
    var atEnd by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { atEnd = true }
    Icon(
        rememberAnimatedVectorPainter(animatedVector, atEnd),
        contentDescription,
    )
}
