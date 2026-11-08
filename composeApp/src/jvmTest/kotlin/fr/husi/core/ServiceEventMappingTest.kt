package fr.husi.core

import fr.husi.bg.ServiceAlert
import fr.husi.bg.ServiceState
import fr.husi.bg.SpeedStats
import fr.husi.proto.v1.AlertKind
import fr.husi.proto.v1.ServiceRunState
import fr.husi.proto.v1.serviceAlert
import fr.husi.proto.v1.serviceStateUpdate
import fr.husi.proto.v1.subscribeServiceEventsResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ServiceEventMappingTest {

    @Test
    fun `ServiceState round trips through proto`() {
        for (state in ServiceState.entries) {
            assertEquals(state, state.toProto().toServiceState())
        }
    }

    @Test
    fun `unspecified and unrecognized ServiceRunState fall back to Idle`() {
        assertEquals(
            ServiceState.Idle,
            ServiceRunState.SERVICE_RUN_STATE_UNSPECIFIED.toServiceState(),
        )
        assertEquals(
            ServiceState.Idle,
            serviceStateUpdate { stateValue = 99 }.state.toServiceState(),
        )
    }

    @Test
    fun `ServiceAlert variants round trip through proto`() {
        val alerts = listOf(
            ServiceAlert.Common("daemon failed"),
            ServiceAlert.MissingPlugin("hysteria2"),
            ServiceAlert.NeedWifiPermission,
        )
        for (alert in alerts) {
            assertEquals(alert, alert.toProto().toServiceAlert())
        }
    }

    @Test
    fun `unspecified and unrecognized AlertKind fall back to Common`() {
        assertEquals(
            ServiceAlert.Common("fallback"),
            serviceAlert {
                kind = AlertKind.ALERT_KIND_UNSPECIFIED
                message = "fallback"
            }.toServiceAlert(),
        )
        assertEquals(
            ServiceAlert.Common("unknown"),
            serviceAlert {
                kindValue = 99
                message = "unknown"
            }.toServiceAlert(),
        )
    }

    @Test
    fun `ServiceEvent arms round trip through proto`() {
        val events = listOf(
            ServiceEvent.State(ServiceState.Connected, "home"),
            ServiceEvent.State(ServiceState.Idle, null),
            ServiceEvent.Speed(
                SpeedStats(
                    txRateProxy = 1,
                    rxRateProxy = 2,
                    txRateDirect = 3,
                    rxRateDirect = 4,
                    txTotal = 5,
                    rxTotal = 6,
                ),
            ),
            ServiceEvent.Alert(ServiceAlert.Common("oops")),
            ServiceEvent.Alert(ServiceAlert.MissingPlugin("naive")),
            ServiceEvent.Alert(ServiceAlert.NeedWifiPermission),
        )
        for (event in events) {
            assertEquals(event, event.toProto().toServiceEvent())
        }
    }

    @Test
    fun `empty SubscribeServiceEventsResponse maps to null`() {
        assertNull(subscribeServiceEventsResponse { }.toServiceEvent())
    }

    @Test
    fun `unspecified state event maps to Idle`() {
        val event = subscribeServiceEventsResponse {
            state = serviceStateUpdate {
                this.state = ServiceRunState.SERVICE_RUN_STATE_UNSPECIFIED
                profileName = "home"
            }
        }.toServiceEvent()

        val state = assertIs<ServiceEvent.State>(event)
        assertEquals(ServiceState.Idle, state.state)
        assertEquals("home", state.profileName)
    }
}
