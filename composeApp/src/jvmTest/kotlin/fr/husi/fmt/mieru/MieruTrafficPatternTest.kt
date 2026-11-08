package fr.husi.fmt.mieru

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Patterns from mieru's own tests (`apis/trafficpattern/config_test.go` and
 * `pkg/appctl/url_test.go`). Every base64 string is the output of mieru's `trafficpattern.Encode`.
 */
class MieruTrafficPatternTest {

    private fun assertRoundTrip(encoded: String, json: String) {
        assertEquals(Json.parseToJsonElement(json), decodeMieruTrafficPattern(encoded))
        assertEquals(encoded, encodeMieruTrafficPattern(json))
    }

    // url_test.go: the pattern carried by a mierus:// link.
    private val urlEncoded = "CCoQARoECAEQCiIYCAMQASoIMDAwMTAyMDMqCDA0MDUwNjA3"
    private val urlJson = """
        {
          "seed": 42,
          "unlockAll": true,
          "tcpFragment": {"enable": true, "maxSleepMs": 10},
          "nonce": {
            "type": "NONCE_TYPE_FIXED",
            "applyToAllUDPPacket": true,
            "customHexStrings": ["00010203", "04050607"]
          }
        }
    """.trimIndent()

    @Test
    fun `link pattern round trips`() {
        assertRoundTrip(urlEncoded, urlJson)
    }

    @Test
    fun `encode accepts a pattern wrapped in trafficPattern`() {
        assertEquals(urlEncoded, encodeMieruTrafficPattern("""{"trafficPattern": $urlJson}"""))
    }

    // TestExplicitValuesPreserved
    @Test
    fun `every field round trips`() {
        assertRoundTrip(
            "CLlgEAEaBAgBEDIiCAgBEAEYBSAKKgUIQBCAATIECAMQMA==",
            """
                {
                  "seed": 12345,
                  "unlockAll": true,
                  "tcpFragment": {"enable": true, "maxSleepMs": 50},
                  "nonce": {
                    "type": "NONCE_TYPE_PRINTABLE",
                    "applyToAllUDPPacket": true,
                    "minLen": 5,
                    "maxLen": 10
                  },
                  "padding": {"maxMiddlePaddingLen": 64, "maxEndPaddingLen": 128},
                  "lowEntropy": {"mode": "LOW_ENTROPY_MODE_48", "maskRotation": "LOW_ENTROPY_MASK_ROTATE_LEFT_3"}
                }
            """.trimIndent(),
        )
    }

    // TestEncodeDecode
    @Test
    fun `pattern without seed round trips`() {
        assertRoundTrip(
            "EAEaBAgBEDIiCAgBEAEYBiAIKgUIQBCAAQ==",
            """
                {
                  "unlockAll": true,
                  "tcpFragment": {"enable": true, "maxSleepMs": 50},
                  "nonce": {
                    "type": "NONCE_TYPE_PRINTABLE",
                    "applyToAllUDPPacket": true,
                    "minLen": 6,
                    "maxLen": 8
                  },
                  "padding": {"maxMiddlePaddingLen": 64, "maxEndPaddingLen": 128}
                }
            """.trimIndent(),
        )
    }

    // TestPartialLowEntropyPatternPreserved
    @Test
    fun `unset fields stay absent`() {
        assertRoundTrip("CCoyAggC", """{"seed": 42, "lowEntropy": {"mode": "LOW_ENTROPY_MODE_40"}}""")
    }

    @Test
    fun `explicitly set zero values round trip`() {
        assertRoundTrip(
            "GgIQBSIGCAIYBCAI",
            """{"tcpFragment": {"maxSleepMs": 5}, "nonce": {"type": "NONCE_TYPE_PRINTABLE_SUBSET", "minLen": 4, "maxLen": 8}}""",
        )
        // TestValidate: valid_padding_pattern
        assertRoundTrip("KgUIABD/AQ==", """{"padding": {"maxMiddlePaddingLen": 0, "maxEndPaddingLen": 255}}""")
    }

    // TestValidate: valid_low_entropy_pattern
    @Test
    fun `highest right mask rotation round trips`() {
        assertRoundTrip(
            "MgQIBBAP",
            """{"lowEntropy": {"mode": "LOW_ENTROPY_MODE_56", "maskRotation": "LOW_ENTROPY_MASK_ROTATE_RIGHT_15"}}""",
        )
    }

    // TestDecodeEmptyString
    @Test
    fun `empty pattern encodes to nothing`() {
        assertEquals("", encodeMieruTrafficPattern("""{"trafficPattern": {}}"""))
        assertEquals(Json.parseToJsonElement("{}"), decodeMieruTrafficPattern(""))
    }

    // TestValidate: low_entropy_mode_invalid, low_entropy_mask_rotation_invalid
    @Test
    fun `decode rejects an undefined enum number`() {
        assertFailsWith<IllegalArgumentException> { decodeMieruTrafficPattern("MgIIBQ==") }
        assertFailsWith<IllegalArgumentException> { decodeMieruTrafficPattern("MgIQEQ==") }
    }

    @Test
    fun `encode rejects an unknown enum name`() {
        assertFailsWith<IllegalArgumentException> {
            encodeMieruTrafficPattern("""{"nonce": {"type": "NONCE_TYPE_SHOUTING"}}""")
        }
    }
}
