package fr.husi.database.preference

import fr.husi.test.FakePreferencesDataStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataStorePreferenceDataStoreBackupTest {

    @Test
    fun `exportToJson with excludeKeys omits specified keys and retains others`() = runTest {
        val store = FakePreferencesDataStore().preferences
        val kept = store.string("keptKey")
        val excluded = store.long("excludedKey")
        kept.set("hello")
        excluded.set(42L)

        val json = store.exportToJson(excludeKeys = setOf("excludedKey"))

        assertTrue("keptKey" in json)
        assertFalse("excludedKey" in json)
        assertEquals(
            JsonPrimitive(DataStorePreferenceDataStore.BACKUP_VERSION),
            json[DataStorePreferenceDataStore.KEY_BACKUP_VERSION],
        )
    }

    @Test
    fun `importFromJson with preserveKeys restores entries while keeping preserved keys untouched`() = runTest {
        val source = FakePreferencesDataStore().preferences
        source.string("theme").set("blue")
        source.boolean("enabled").set(true)
        source.long("activeServer").set(999L)
        val backup = source.exportToJson()

        val target = FakePreferencesDataStore().preferences
        val targetTheme = target.string("theme")
        val targetOld = target.string("obsolete")
        val targetActive = target.long("activeServer")
        targetTheme.set("red")
        targetOld.set("stale")
        targetActive.set(7L)

        target.importFromJson(backup, preserveKeys = setOf("activeServer"))

        assertEquals("blue", targetTheme.get())
        assertTrue(target.boolean("enabled").get())
        assertNull(targetOld.getOrNull())
        assertEquals(7L, targetActive.get())
    }

    @Test
    fun `importFromJson keeps preserved key even when absent from backup`() = runTest {
        val source = FakePreferencesDataStore().preferences
        source.int("interval").set(500)
        val backup = source.exportToJson(excludeKeys = setOf("activeServer"))

        val target = FakePreferencesDataStore().preferences
        val targetActive = target.long("activeServer")
        targetActive.set(12L)

        target.importFromJson(backup, preserveKeys = setOf("activeServer"))

        assertEquals(500, target.int("interval").get())
        assertEquals(12L, targetActive.get())
    }

    @Test
    fun `validateBackupJson accepts valid backup and rejects invalid payloads`() = runTest {
        val store = FakePreferencesDataStore().preferences
        store.stringSet("tags").set(setOf("a", "b"))
        val valid = store.exportToJson()

        DataStorePreferenceDataStore.validateBackupJson(valid)

        assertFailsWith<IllegalArgumentException> {
            DataStorePreferenceDataStore.validateBackupJson(
                buildJsonObject {
                    put(DataStorePreferenceDataStore.KEY_BACKUP_VERSION, 99L)
                },
            )
        }

        assertFailsWith<IllegalStateException> {
            DataStorePreferenceDataStore.validateBackupJson(
                buildJsonObject {
                    put(DataStorePreferenceDataStore.KEY_BACKUP_VERSION, DataStorePreferenceDataStore.BACKUP_VERSION)
                    put("broken", "not-an-object")
                },
            )
        }

        assertFailsWith<IllegalStateException> {
            DataStorePreferenceDataStore.validateBackupJson(
                buildJsonObject {
                    put(DataStorePreferenceDataStore.KEY_BACKUP_VERSION, DataStorePreferenceDataStore.BACKUP_VERSION)
                    putJsonObject("missingType") {
                        put("value", "abc")
                    }
                },
            )
        }
    }

    @Test
    fun `failed importFromJson validation leaves existing preferences untouched`() = runTest {
        val store = FakePreferencesDataStore().preferences
        val existing = store.string("existing")
        existing.set("keep-me")

        assertFailsWith<IllegalStateException> {
            store.importFromJson(
                buildJsonObject {
                    put(DataStorePreferenceDataStore.KEY_BACKUP_VERSION, DataStorePreferenceDataStore.BACKUP_VERSION)
                    put("invalid", "bad")
                },
            )
        }

        assertEquals("keep-me", existing.get())
    }
}
