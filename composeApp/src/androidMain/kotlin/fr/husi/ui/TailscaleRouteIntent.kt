package fr.husi.ui

import android.content.Context
import android.content.Intent
import fr.husi.tailscale.TailscaleNotice

internal object TailscaleRouteIntent {
    private const val EXTRA_ROUTE = "fr.husi.extra.TAILSCALE_ROUTE"
    private const val EXTRA_ENDPOINT_TAG = "fr.husi.extra.TAILSCALE_ENDPOINT_TAG"
    private const val ROUTE_ENDPOINT = "endpoint"
    private const val ROUTE_TAILDROP_INBOX = "taildrop_inbox"

    fun forNotice(context: Context, notice: TailscaleNotice): Intent {
        val route = when (notice) {
            is TailscaleNotice.FilesReceived -> ROUTE_TAILDROP_INBOX
            is TailscaleNotice.NeedsLogin -> ROUTE_ENDPOINT
        }
        return Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_ROUTE, route)
            .putExtra(EXTRA_ENDPOINT_TAG, notice.endpointTag)
    }

    fun routeOf(intent: Intent): NavRoutes? {
        val endpointTag = intent.getStringExtra(EXTRA_ENDPOINT_TAG) ?: return null
        return when (intent.getStringExtra(EXTRA_ROUTE)) {
            ROUTE_ENDPOINT -> NavRoutes.Tailscale.Endpoint(endpointTag)
            ROUTE_TAILDROP_INBOX -> NavRoutes.Tailscale.TaildropInbox(endpointTag)
            else -> null
        }
    }
}
