package fr.husi.core

import fr.husi.bg.ServiceAlert
import fr.husi.bg.ServiceState
import fr.husi.bg.SpeedStats
import fr.husi.proto.v1.AlertKind
import fr.husi.proto.v1.ServiceRunState
import fr.husi.proto.v1.SubscribeServiceEventsResponse
import fr.husi.proto.v1.serviceAlert
import fr.husi.proto.v1.serviceStateUpdate
import fr.husi.proto.v1.speedUpdate
import fr.husi.proto.v1.subscribeServiceEventsResponse
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
    ServiceRunState.SERVICE_RUN_STATE_UNSPECIFIED,
    ServiceRunState.UNRECOGNIZED,
    -> ServiceState.Idle
}

fun ServiceAlert.toProto(): ProtoServiceAlert {
    val alert = this
    return serviceAlert {
        when (alert) {
            is ServiceAlert.Common -> {
                kind = AlertKind.ALERT_KIND_COMMON
                message = alert.message
            }
            is ServiceAlert.MissingPlugin -> {
                kind = AlertKind.ALERT_KIND_MISSING_PLUGIN
                message = alert.pluginName
            }
            is ServiceAlert.NeedWifiPermission -> {
                kind = AlertKind.ALERT_KIND_NEED_WIFI_PERMISSION
            }
        }
    }
}

fun ProtoServiceAlert.toServiceAlert(): ServiceAlert = when (kind) {
    AlertKind.ALERT_KIND_COMMON -> ServiceAlert.Common(message)
    AlertKind.ALERT_KIND_MISSING_PLUGIN -> ServiceAlert.MissingPlugin(message)
    AlertKind.ALERT_KIND_NEED_WIFI_PERMISSION -> ServiceAlert.NeedWifiPermission
    AlertKind.ALERT_KIND_UNSPECIFIED,
    AlertKind.UNRECOGNIZED,
    -> ServiceAlert.Common(message)
}

fun SubscribeServiceEventsResponse.toServiceEvent(): ServiceEvent? = when (eventCase) {
    SubscribeServiceEventsResponse.EventCase.STATE -> ServiceEvent.State(
        state = state.state.toServiceState(),
        profileName = state.profileName.ifEmpty { null },
    )
    SubscribeServiceEventsResponse.EventCase.SPEED -> ServiceEvent.Speed(
        SpeedStats(
            txRateProxy = speed.txRateProxy,
            rxRateProxy = speed.rxRateProxy,
            txRateDirect = speed.txRateDirect,
            rxRateDirect = speed.rxRateDirect,
            txTotal = speed.txTotal,
            rxTotal = speed.rxTotal,
        ),
    )
    SubscribeServiceEventsResponse.EventCase.ALERT -> ServiceEvent.Alert(alert.toServiceAlert())
    SubscribeServiceEventsResponse.EventCase.EVENT_NOT_SET -> null
}

fun ServiceEvent.toProto(): SubscribeServiceEventsResponse {
    val event = this
    return subscribeServiceEventsResponse {
        when (event) {
            is ServiceEvent.State -> {
                state = serviceStateUpdate {
                    this.state = event.state.toProto()
                    profileName = event.profileName.orEmpty()
                }
            }
            is ServiceEvent.Speed -> {
                speed = speedUpdate {
                    txRateProxy = event.stats.txRateProxy
                    rxRateProxy = event.stats.rxRateProxy
                    txRateDirect = event.stats.txRateDirect
                    rxRateDirect = event.stats.rxRateDirect
                    txTotal = event.stats.txTotal
                    rxTotal = event.stats.rxTotal
                }
            }
            is ServiceEvent.Alert -> {
                alert = event.alert.toProto()
            }
        }
    }
}
