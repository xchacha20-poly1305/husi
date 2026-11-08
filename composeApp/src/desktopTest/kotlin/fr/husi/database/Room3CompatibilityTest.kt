package fr.husi.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import fr.husi.fmt.BeanConverters
import fr.husi.fmt.socks.SOCKSBean
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class Room3CompatibilityTest {
    @Test
    fun `room bean converters round trip null and populated beans on insert and update`() = runBlocking {
        val directory = createTempDirectory("husi-room3-beans-").toFile()
        val file = directory.resolve("profiles.db")
        try {
            val database = Room.databaseBuilder<SagerDatabase>(file.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .build()
            try {
                suspend fun verifyStoredBeans(proxy: ProxyEntity, group: ProxyGroup) {
                    val storedProxy = assertNotNull(database.proxyDao().getById(proxy.id))
                    val storedGroup = assertNotNull(database.groupDao().getById(group.id).first())
                    assertEquals(proxy.socksBean?.serverAddress, storedProxy.socksBean?.serverAddress)
                    assertEquals(group.subscription?.link, storedGroup.subscription?.link)

                    BundledSQLiteDriver().open(file.absolutePath).use { connection ->
                        connection.prepare(
                            "SELECT socksBean, subscription FROM proxy_entities " +
                                "JOIN proxy_groups ON proxy_entities.groupId = proxy_groups.id " +
                                "WHERE proxy_entities.id = ?",
                        ).use { statement ->
                            statement.bindLong(1, proxy.id)
                            assertTrue(statement.step())
                            assertEquals(proxy.socksBean == null, statement.isNull(0))
                            assertEquals(group.subscription == null, statement.isNull(1))
                            proxy.socksBean?.let { bean ->
                                assertContentEquals(BeanConverters.serialize(bean), statement.getBlob(0))
                            }
                            group.subscription?.let { bean ->
                                assertContentEquals(BeanConverters.serialize(bean), statement.getBlob(1))
                            }
                        }
                    }
                }

                val socks = SOCKSBean().also { it.serverAddress = "proxy.example.com" }
                val subscription = SubscriptionBean().also { it.link = "https://example.com/sub" }
                for (startWithNull in listOf(true, false)) {
                    val group = ProxyGroup(subscription = if (startWithNull) null else subscription)
                    group.id = database.groupDao().createGroup(group)
                    val proxy = ProxyEntity(
                        groupId = group.id,
                        socksBean = if (startWithNull) null else socks,
                    )
                    proxy.id = database.proxyDao().addProxy(proxy)
                    verifyStoredBeans(proxy, group)

                    proxy.socksBean = if (startWithNull) socks else null
                    group.subscription = if (startWithNull) subscription else null
                    database.proxyDao().updateProxy(proxy)
                    database.groupDao().updateGroup(group)
                    verifyStoredBeans(proxy, group)
                }

                val legacyGroup = ProxyGroup()
                legacyGroup.id = database.groupDao().createGroup(legacyGroup)
                val legacyProxy = ProxyEntity(groupId = legacyGroup.id)
                legacyProxy.id = database.proxyDao().addProxy(legacyProxy)
                BundledSQLiteDriver().open(file.absolutePath).use { connection ->
                    connection.prepare("UPDATE proxy_entities SET socksBean = X'' WHERE id = ?")
                        .use { statement ->
                            statement.bindLong(1, legacyProxy.id)
                            statement.step()
                        }
                    connection.prepare("UPDATE proxy_groups SET subscription = X'' WHERE id = ?")
                        .use { statement ->
                            statement.bindLong(1, legacyGroup.id)
                            statement.step()
                        }
                }
                val storedProxy = assertNotNull(database.proxyDao().getById(legacyProxy.id))
                val storedGroup = assertNotNull(database.groupDao().getById(legacyGroup.id).first())
                assertNull(storedProxy.socksBean)
                assertNull(storedGroup.subscription)
                database.proxyDao().updateProxy(storedProxy)
                database.groupDao().updateGroup(storedGroup)
                verifyStoredBeans(storedProxy, storedGroup)
            } finally {
                database.close()
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `room 3 opens the existing version 25 schema and preserves remote servers`() = runBlocking {
        val directory = createTempDirectory("husi-room3-").toFile()
        val file = directory.resolve("profiles.db")
        try {
            val schema = Json.parseToJsonElement(
                File("schemas/fr.husi.database.SagerDatabase/25.json").readText(),
            ).jsonObject.getValue("database").jsonObject
            BundledSQLiteDriver().open(file.absolutePath).use { connection ->
                for (item in schema.getValue("entities").jsonArray) {
                    val entity = item.jsonObject
                    val table = entity.getValue("tableName").jsonPrimitive.content
                    connection.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace($$"${TABLE_NAME}", table))
                    for (index in entity["indices"]?.jsonArray.orEmpty()) {
                        connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace($$"${TABLE_NAME}", table))
                    }
                }
                for (query in schema.getValue("setupQueries").jsonArray) {
                    connection.execSQL(query.jsonPrimitive.content)
                }
                connection.execSQL("PRAGMA user_version = 25")
                connection.execSQL(
                    "INSERT INTO remote_servers (id, userOrder, name, url, secret) " +
                        "VALUES (7, 3, 'existing', 'https://example.com', 'saved-token')",
                )
            }
            fun open() = Room.databaseBuilder<SagerDatabase>(file.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .build()
            val database = open()
            try {
                val server = assertNotNull(database.remoteServerDao().getById(7L))
                assertEquals("saved-token", server.secret)
                assertEquals(3L, server.userOrder)
                server.name = "updated"
                database.remoteServerDao().upsert(server)
            } finally {
                database.close()
            }
            val reopened = open()
            try {
                assertEquals("updated", reopened.remoteServerDao().getById(7L)?.name)
                assertEquals("saved-token", reopened.remoteServerDao().getById(7L)?.secret)
            } finally {
                reopened.close()
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
