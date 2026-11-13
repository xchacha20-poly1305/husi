package fr.husi.core

import fr.husi.bg.ServiceAlert
import fr.husi.bg.ServiceState
import fr.husi.bg.SpeedStats
import fr.husi.proto.v1.AlertKind
import fr.husi.proto.v1.ServiceRunState
import fr.husi.proto.v1.ServiceStateUpdate
import fr.husi.proto.v1.SpeedUpdate
import fr.husi.proto.v1.SubscribeServiceEventsResponse
import fr.husi.proto.v1.ServiceAlert as ProtoServiceAlert

fun ServiceState.toProto(): ServiceRunState = when (this) {
    ServiceState.Idle -> ServiceRunState.SERVICE_RUN_STATE_IDLE
    ServiceState.Connecting -> ServiceRunState.SERVICE_RUN_STATE_CONNECTING
    ServiceState.Connected -> ServiceRunState.SERVICE_RUN_STATE_CONNECTED
    ServiceState.Stopping -> ServiceRunState.SERVICE_RUN_STATE_STOPPING
    ServiceState.Stopped -> ServiceRunState.SERVICE_RUN_STATE_STOPPED
}

fun ServiceRunState.toServiceState(): ServiceState = when (this) {
    ServiceRunState.SERVICE_RUN_STATE_IDLE -> ServiceState.Idle
    ServiceRunState.SERVICE_RUN_STATE_CONNECTING -> ServiceState.Connecting
    ServiceRunState.SERVICE_RUN_STATE_CONNECTED -> ServiceState.Connected
    ServiceRunState.SERVICE_RUN_STATE_STOPPING -> ServiceState.Stopping
    ServiceRunState.SERVICE_RUN_STATE_STOPPED -> ServiceState.Stopped
    ServiceRunState.SERVICE_RUN_STATE_UNSPECIFIED -> ServiceState.Idle
}

fun ServiceAlert.toProto(): ProtoServiceAlert = when (this) {
    is ServiceAlert.Common -> ProtoServiceAlert(kind = AlertKind.ALERT_KIND_COMMON, message = message)
    is ServiceAlert.MissingPlugin -> ProtoServiceAlert(kind = AlertKind.ALERT_KIND_MISSING_PLUGIN, message = pluginName)
    is ServiceAlert.NeedWifiPermission -> ProtoServiceAlert(kind = AlertKind.ALERT_KIND_NEED_WIFI_PERMISSION)
}

fun ProtoServiceAlert.toServiceAlert(): ServiceAlert = when (kind) {
    AlertKind.ALERT_KIND_MISSING_PLUGIN -> ServiceAlert.MissingPlugin(message)
    AlertKind.ALERT_KIND_NEED_WIFI_PERMISSION -> ServiceAlert.NeedWifiPermission
    else -> ServiceAlert.Common(message)
}

fun SubscribeServiceEventsResponse.toServiceEvent(): ServiceEvent? {
    state?.let {
        return ServiceEvent.State(it.state.toServiceState(), it.profile_name.ifEmpty { null })
    }
    speed?.let {
        return ServiceEvent.Speed(
            SpeedStats(
                txRateProxy = it.tx_rate_proxy,
                rxRateProxy = it.rx_rate_proxy,
                txRateDirect = it.tx_rate_direct,
                rxRateDirect = it.rx_rate_direct,
                txTotal = it.tx_total,
                rxTotal = it.rx_total,
            ),
        )
    }
    return alert?.let { ServiceEvent.Alert(it.toServiceAlert()) }
}

fun ServiceEvent.toProto(): SubscribeServiceEventsResponse = when (this) {
    is ServiceEvent.State ->
        SubscribeServiceEventsResponse(
            state = ServiceStateUpdate(
                state = state.toProto(),
                profile_name = profileName.orEmpty(),
            ),
        )

    is ServiceEvent.Speed ->
        SubscribeServiceEventsResponse(
            speed = SpeedUpdate(
                tx_rate_proxy = stats.txRateProxy,
                rx_rate_proxy = stats.rxRateProxy,
                tx_rate_direct = stats.txRateDirect,
                rx_rate_direct = stats.rxRateDirect,
                tx_total = stats.txTotal,
                rx_total = stats.rxTotal,
            ),
        )

    is ServiceEvent.Alert -> SubscribeServiceEventsResponse(alert = alert.toProto())
}
