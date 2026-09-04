package fr.husi.utils

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendLogTest {

    private val target = RemoteLogTarget(
        name = "Home / NAS",
        url = "http://192.168.1.2:9090",
        version = "husi 2.1.0, sing-box 1.13.0, go1.25 linux/amd64",
        logLevel = "DEBUG",
    )

    @Test
    fun `buildRemoteLog names the file after the target`() {
        val export = SendLog.buildRemoteLog(target, emptyList())

        assertTrue(export.fileName.startsWith("husi-Home---NAS-"), export.fileName)
        assertTrue(export.fileName.endsWith(".log"), export.fileName)
    }

    @Test
    fun `buildRemoteLog falls back to a placeholder name when the target is unnamed`() {
        val export = SendLog.buildRemoteLog(target.copy(name = " "), emptyList())

        assertTrue(export.fileName.startsWith("husi-remote-"), export.fileName)
        assertContains(export.content, "Target: remote (${target.url})")
    }

    @Test
    fun `buildRemoteLog describes the target and keeps every received line`() {
        val logLines = listOf("first line", "second line")

        val content = SendLog.buildRemoteLog(target, logLines).content

        assertContains(content, "Target: Home / NAS (${target.url})")
        assertContains(content, "Version: ${target.version}")
        assertContains(content, "Log level: DEBUG")
        for (line in logLines) {
            assertContains(content, line)
        }
    }

    @Test
    fun `buildRemoteLog reports the client environment without local settings or logcat`() {
        val content = SendLog.buildRemoteLog(target, emptyList()).content

        assertContains(content, "Client: ")
        assertFalse(content.contains("Settings: "), content)
        assertFalse(content.contains("Logcat: "), content)
    }
}
