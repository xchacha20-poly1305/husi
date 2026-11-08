package fr.husi.core

import fr.husi.bg.ServiceState
import fr.husi.proto.daemon.ServiceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreStateReconciliationTest {

    @Test
    fun `a running core is adopted by a UI that shows nothing running`() {
        for (local in listOf(ServiceState.Idle, ServiceState.Stopped, ServiceState.Connecting)) {
            assertEquals(
                CoreStateReconciliation.Adopt,
                reconciliationFor(ServiceStatus.Type.STARTED, local),
                "local state $local",
            )
        }
    }

    @Test
    fun `a running core needs nothing from a UI that already shows it connected`() {
        assertNull(reconciliationFor(ServiceStatus.Type.STARTED, ServiceState.Connected))
    }

    @Test
    fun `a starting core moves a resting UI to connecting`() {
        for (local in listOf(ServiceState.Idle, ServiceState.Stopped)) {
            assertEquals(
                CoreStateReconciliation.MarkStarting,
                reconciliationFor(ServiceStatus.Type.STARTING, local),
                "local state $local",
            )
        }
    }

    @Test
    fun `a restarting core does not blink a connected UI back to connecting`() {
        assertNull(reconciliationFor(ServiceStatus.Type.STARTING, ServiceState.Connected))
        assertNull(reconciliationFor(ServiceStatus.Type.STARTING, ServiceState.Connecting))
    }

    @Test
    fun `an idle core abandons what the UI still shows as running`() {
        for (local in listOf(ServiceState.Connecting, ServiceState.Connected)) {
            assertEquals(
                CoreStateReconciliation.Abandon,
                reconciliationFor(ServiceStatus.Type.IDLE, local),
                "local state $local",
            )
        }
    }

    @Test
    fun `a fatal core abandons what the UI still shows as running`() {
        assertEquals(
            CoreStateReconciliation.Abandon,
            reconciliationFor(ServiceStatus.Type.FATAL, ServiceState.Connected),
        )
    }

    @Test
    fun `an idle core needs nothing from a UI that shows nothing running`() {
        for (local in listOf(ServiceState.Idle, ServiceState.Stopped, ServiceState.Stopping)) {
            assertNull(
                reconciliationFor(ServiceStatus.Type.IDLE, local),
                "local state $local",
            )
        }
    }

    @Test
    fun `a stopping core is left alone until it reports what it settled on`() {
        for (local in ServiceState.entries) {
            assertNull(
                reconciliationFor(ServiceStatus.Type.STOPPING, local),
                "local state $local",
            )
        }
    }

    @Test
    fun `an unrecognized status from a newer core changes nothing`() {
        for (local in ServiceState.entries) {
            assertNull(
                reconciliationFor(ServiceStatus.Type.UNRECOGNIZED, local),
                "local state $local",
            )
        }
    }
}
