package fr.husi.tailscale

import fr.husi.database.DataStore
import fr.husi.test.HusiKoinTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TailscaleSSHLoginsTest : HusiKoinTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    @Test
    fun `a peer never connected to gets the default login`() = runTest {
        assertEquals(TailscaleSSHLogin(), TailscaleSSHLogins.get("peer"))
    }

    @Test
    fun `put remembers the login per peer`() = runTest {
        val login = TailscaleSSHLogin(username = "alice", terminalType = "xterm-ghostty")

        TailscaleSSHLogins.put("peer", login)

        assertEquals(login, TailscaleSSHLogins.get("peer"))
        assertEquals(TailscaleSSHLogin(), TailscaleSSHLogins.get("other"))
    }

    @Test
    fun `putting the default login forgets the peer`() = runTest {
        TailscaleSSHLogins.put("peer", TailscaleSSHLogin(username = "alice"))

        TailscaleSSHLogins.put("peer", TailscaleSSHLogin())

        assertEquals("{}", DataStore.tailscaleSSHLogins.get())
    }

    @Test
    fun `an unreadable store reads as empty`() = runTest {
        DataStore.tailscaleSSHLogins.set("not json")

        assertEquals(TailscaleSSHLogin(), TailscaleSSHLogins.get("peer"))
    }
}
