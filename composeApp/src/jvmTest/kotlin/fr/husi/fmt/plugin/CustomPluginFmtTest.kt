package fr.husi.fmt.plugin

import fr.husi.fmt.BeanConverters
import fr.husi.fmt.LOCALHOST4
import fr.husi.ktx.applyDefaultValues
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class CustomPluginFmtTest {

    @Test
    fun `parseCommandArgs parses simple whitespace separated args`() {
        assertEquals(
            listOf("--listen", "127.0.0.1:1080", "-v"),
            parseCommandArgs("--listen 127.0.0.1:1080 -v"),
        )
    }

    @Test
    fun `parseCommandArgs handles single and double quotes with spaces and empty strings`() {
        assertEquals(
            listOf("--msg", "hello world", "--empty", "", "--flag=a b", "--sq", "foo bar", "--sq-empty", ""),
            parseCommandArgs("""--msg "hello world" --empty "" --flag="a b" --sq 'foo bar' --sq-empty ''"""),
        )
    }

    @Test
    fun `parseCommandArgs handles escaped quotes backslashes and spaces`() {
        assertEquals(
            listOf("""a"b""", """c\d""", "hello world", """in"double\quote""", """in\single\quote"""),
            parseCommandArgs("""a\"b c\\d hello\ world "in\"double\\quote" 'in\single\quote'"""),
        )
    }

    @Test
    fun `parseCommandArgs treats newlines and tabs as argument separators`() {
        assertEquals(
            listOf("--one", "--two", "--three", "--four"),
            parseCommandArgs("--one\n--two\r\n  --three\t--four"),
        )
    }

    @Test
    fun `parseCommandArgs returns empty list for empty or blank input`() {
        assertEquals(emptyList(), parseCommandArgs(""))
        assertEquals(emptyList(), parseCommandArgs("   \n\t\r\n  "))
    }

    @Test
    fun `parseEnvironment parses key values preserves equals ignores comments and blank lines`() {
        val raw = """
            # leading comment
            
              # indented comment
              FOO  =bar
            URL=https://example.com?a=1&b=2
            TOKEN=abc==
            INVALID_LINE_WITHOUT_EQUALS
            =missing_key
            EMPTY_VAL=
        """.trimIndent()

        assertEquals(
            mapOf(
                "FOO" to "bar",
                "URL" to "https://example.com?a=1&b=2",
                "TOKEN" to "abc==",
                "EMPTY_VAL" to "",
            ),
            parseEnvironment(raw),
        )
        assertEquals(emptyMap(), parseEnvironment("   \n  "))
    }

    @Test
    fun `expandPlaceholders substitutes known tokens preserves unknown and handles triple braces`() {
        val values = mapOf(
            PLACEHOLDER_LISTEN_HOST to LOCALHOST4,
            PLACEHOLDER_LISTEN_PORT to "12345",
        )
        val template = "host={{listen_host}} port={{listen_port}} unknown={{unknown}} triple={{{listen_host}}}"
        assertEquals(
            "host=127.0.0.1 port=12345 unknown={{unknown}} triple={{{listen_host}}}",
            expandPlaceholders(template, values),
        )
    }

    @Test
    fun `renderCommandArgs and renderEnvironment expand all 15 placeholders`() {
        val pem = "-----BEGIN CERTIFICATE-----\nMIIB\"test\\line\n-----END CERTIFICATE-----"
        val bean = CustomPluginBean().apply {
            serverAddress = "2001:db8::1"
            serverPort = 443
            finalAddress = LOCALHOST4
            finalPort = 23456
            commandArgs = """
                --lh {{listen_host}}
                --lp {{listen_port}}
                --la {{listen_addr}}
                --fh {{final_host}}
                --fp {{final_port}}
                --fa {{final_addr}}
                --sh {{server_host}}
                --sp {{server_port}}
                --cfg {{config}}
                --prot {{protect_path}}
                --cap {{ca_path}}
                --cac {{ca_content}}
                --cacj {{ca_content_json}}
                --log {{log_level}}
                --cache {{cache_dir}}
                --unknown {{unknown}}
            """.trimIndent()
            environment = """
                LISTEN_ADDR={{listen_addr}}
                FINAL_ADDR={{final_addr}}
                SERVER_HOST={{server_host}}
                CONFIG_FILE={{config}}
                PROTECT={{protect_path}}
                CA_PATH={{ca_path}}
                LOG={{log_level}}
                CACHE={{cache_dir}}
            """.trimIndent()
        }
        val context = CustomPluginRenderContext(
            port = 12345,
            shouldProtect = true,
            logLevel = 3,
            protectPath = "protect_path",
            caPath = "/tmp/my certs/plugin-ca.pem",
            caContent = pem,
            cacheDir = "/tmp/my cache/dir",
            configFileToken = $$"${file:custom_12345_config.json}",
        )

        val args = bean.renderCommandArgs(context)
        assertEquals(
            listOf(
                "--lh", LOCALHOST4,
                "--lp", "12345",
                "--la", "127.0.0.1:12345",
                "--fh", LOCALHOST4,
                "--fp", "23456",
                "--fa", "127.0.0.1:23456",
                "--sh", "2001:db8::1",
                "--sp", "443",
                "--cfg", $$"${file:custom_12345_config.json}",
                "--prot", "protect_path",
                "--cap", "/tmp/my certs/plugin-ca.pem",
                "--cac", pem,
                "--cacj", escapeJsonStringContent(pem),
                "--log", "warn",
                "--cache", "/tmp/my cache/dir",
                "--unknown", "{{unknown}}",
            ),
            args,
        )

        val env = bean.renderEnvironment(context)
        assertEquals(
            mapOf(
                "LISTEN_ADDR" to "127.0.0.1:12345",
                "FINAL_ADDR" to "127.0.0.1:23456",
                "SERVER_HOST" to "2001:db8::1",
                "CONFIG_FILE" to $$"${file:custom_12345_config.json}",
                "PROTECT" to "protect_path",
                "CA_PATH" to "/tmp/my certs/plugin-ca.pem",
                "LOG" to "warn",
                "CACHE" to "/tmp/my cache/dir",
            ),
            env,
        )
    }

    @Test
    fun `ca_content_json produces valid JSON when embedded inside a JSON string`() {
        val pem = "-----BEGIN CERTIFICATE-----\nline1\"quote\"\\slash\n-----END CERTIFICATE-----"
        val bean = CustomPluginBean().apply {
            config = """{"ca": "{{ca_content_json}}"}"""
        }
        val rendered = bean.renderConfig(
            CustomPluginRenderContext(
                port = 1080,
                caContent = pem,
            ),
        )
        val parsed = Json.parseToJsonElement(rendered).jsonObject
        assertEquals(pem, parsed["ca"]?.jsonPrimitive?.content)
    }

    @Test
    fun `renderConfig leaves config placeholder untouched while commandArgs and env expand it and preserve spaces as single arg`() {
        val bean = CustomPluginBean().apply {
            commandArgs = "--config {{config}} --cache {{cache_dir}}"
            config = """{"self": "{{config}}", "listen": "{{listen_addr}}"}"""
            environment = "CFG={{config}}"
        }
        val context = CustomPluginRenderContext(
            port = 1080,
            cacheDir = "/tmp/dir with spaces/cache",
            configFileToken = "/tmp/dir with spaces/config.json",
        )

        assertEquals(
            """{"self": "{{config}}", "listen": "127.0.0.1:1080"}""",
            bean.renderConfig(context),
        )
        assertEquals(
            listOf("--config", "/tmp/dir with spaces/config.json", "--cache", "/tmp/dir with spaces/cache"),
            bean.renderCommandArgs(context),
        )
        assertEquals(
            mapOf("CFG" to "/tmp/dir with spaces/config.json"),
            bean.renderEnvironment(context),
        )
    }

    @Test
    fun `sanitizedConfigFileName defaults to json preserves extensions and strips path separators`() {
        val bean = CustomPluginBean()

        bean.configFileName = ""
        assertEquals("custom_1080.json", bean.sanitizedConfigFileName(1080))

        bean.configFileName = "   "
        assertEquals("custom_1080.json", bean.sanitizedConfigFileName(1080))

        bean.configFileName = "config.yaml"
        assertEquals("custom_1080_config.yaml", bean.sanitizedConfigFileName(1080))

        bean.configFileName = "client.toml"
        assertEquals("custom_1080_client.toml", bean.sanitizedConfigFileName(1080))

        bean.configFileName = "../evil/a.yaml"
        val sanitizedTraversal = bean.sanitizedConfigFileName(1080)
        assertFalse(sanitizedTraversal.contains('/'))
        assertFalse(sanitizedTraversal.contains('\\'))
        assertEquals("custom_1080_..evila.yaml", sanitizedTraversal)

        bean.configFileName = "."
        assertEquals("custom_1080.json", bean.sanitizedConfigFileName(1080))

        bean.configFileName = ".."
        assertEquals("custom_1080.json", bean.sanitizedConfigFileName(1080))

        bean.configFileName = "../"
        assertEquals("custom_1080.json", bean.sanitizedConfigFileName(1080))
    }

    @Test
    fun `CustomPluginBean defaults IPv6 unwrapping capabilities and serialization round-trip`() {
        val blankServerBean = CustomPluginBean().apply {
            serverAddress = ""
            serverPort = 70000
            configFileName = ""
        }.applyDefaultValues()
        assertEquals("", blankServerBean.serverAddress)
        assertEquals(0, blankServerBean.serverPort)
        assertEquals("", blankServerBean.finalAddress)
        assertEquals(0, blankServerBean.finalPort)
        assertEquals(CustomPluginBean.DEFAULT_CONFIG_FILE_NAME, blankServerBean.configFileName)
        assertFalse(blankServerBean.canMapping)
        assertFalse(blankServerBean.canSelfProtect)

        val ipv6Bean = CustomPluginBean().apply {
            name = "my-plugin"
            pluginId = "fr.husi.plugin.test"
            executablePath = "/usr/local/bin/my-plugin"
            commandArgs = "-c {{config}}"
            configFileName = "client.toml"
            config = "listen = \"{{listen_addr}}\""
            environment = "KEY=VAL"
            serverAddress = "[::1]"
            serverPort = 443
            supportProtect = true
        }.applyDefaultValues()
        assertEquals("::1", ipv6Bean.serverAddress)
        assertEquals("::1", ipv6Bean.finalAddress)
        assertEquals(443, ipv6Bean.finalPort)
        assertTrue(ipv6Bean.canMapping)
        assertTrue(ipv6Bean.canSelfProtect)

        // Blank host with valid port cannot map; valid host with port 0 cannot map.
        assertFalse(
            CustomPluginBean().apply {
                serverAddress = ""
                serverPort = 443
            }.canMapping,
        )
        assertFalse(
            CustomPluginBean().apply {
                serverAddress = "1.1.1.1"
                serverPort = 0
            }.canMapping,
        )

        val bytes = BeanConverters.serialize(ipv6Bean)
        val restored = assertNotNull(BeanConverters.customPluginDeserialize(bytes))
        assertEquals(ipv6Bean.name, restored.name)
        assertEquals(ipv6Bean.pluginId, restored.pluginId)
        assertEquals(ipv6Bean.executablePath, restored.executablePath)
        assertEquals(ipv6Bean.commandArgs, restored.commandArgs)
        assertEquals(ipv6Bean.configFileName, restored.configFileName)
        assertEquals(ipv6Bean.config, restored.config)
        assertEquals(ipv6Bean.environment, restored.environment)
        assertEquals(ipv6Bean.supportProtect, restored.supportProtect)
        assertEquals(ipv6Bean.serverAddress, restored.serverAddress)
        assertEquals(ipv6Bean.serverPort, restored.serverPort)

        val cloned = ipv6Bean.clone()
        assertNotSame(ipv6Bean, cloned)
        cloned.name = "mutated"
        cloned.supportProtect = false
        assertEquals("my-plugin", ipv6Bean.name)
        assertTrue(ipv6Bean.supportProtect)
    }
}
