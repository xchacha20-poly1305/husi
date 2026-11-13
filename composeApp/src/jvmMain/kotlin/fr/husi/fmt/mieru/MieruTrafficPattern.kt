package fr.husi.fmt.mieru

import com.squareup.wire.WireEnum
import fr.husi.proto.mieru.LowEntropyMaskRotation
import fr.husi.proto.mieru.LowEntropyMode
import fr.husi.proto.mieru.LowEntropyPattern
import fr.husi.proto.mieru.NoncePattern
import fr.husi.proto.mieru.NonceType
import fr.husi.proto.mieru.PaddingPattern
import fr.husi.proto.mieru.TCPFragment
import fr.husi.proto.mieru.TrafficPattern
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okio.ByteString.Companion.decodeBase64

private const val WRAPPER_KEY = "trafficPattern"
private val trafficPatternJson = Json { explicitNulls = false }

fun decodeMieruTrafficPattern(encoded: String): JsonElement {
    val bytes = encoded.decodeBase64()
        ?: throw IllegalArgumentException("traffic pattern is not base64")
    return TrafficPattern.ADAPTER.decode(bytes).toJson()
}

fun encodeMieruTrafficPattern(json: String): String {
    val root = trafficPatternJson.parseToJsonElement(json) as? JsonObject
        ?: throw SerializationException("traffic pattern is not a JSON object")
    return trafficPattern(root[WRAPPER_KEY] ?: root).encodeByteString().base64()
}

private fun JsonElement.checkedObject(vararg fields: String): JsonObject {
    val result = this as? JsonObject
        ?: throw SerializationException("traffic pattern field is not an object")
    val unknown = result.keys - fields.toSet()
    if (unknown.isNotEmpty()) {
        throw SerializationException("unknown traffic pattern fields: $unknown")
    }
    return result
}

private fun <T> JsonObject.optional(name: String, decode: (JsonElement) -> T): T? =
    get(name)?.takeUnless { it == JsonNull }?.let(decode)

private inline fun <reified T> primitive(element: JsonElement): T =
    trafficPatternJson.decodeFromJsonElement<T>(element)

private fun <T : WireEnum> enumValue(element: JsonElement, values: Map<String, T>): T =
    values[primitive<String>(element)]
        ?: throw SerializationException("unknown traffic pattern enum: $element")

private fun <T : WireEnum> enumJson(value: T, values: Map<String, T>): JsonPrimitive {
    val name = values.entries.firstOrNull { it.value == value }?.key
        ?: throw IllegalArgumentException("undefined traffic pattern enum number: ${value.value}")
    return JsonPrimitive(name)
}

private val nonceTypeValues =
    listOf(
        NonceType.NONCE_TYPE_RANDOM,
        NonceType.NONCE_TYPE_PRINTABLE,
        NonceType.NONCE_TYPE_PRINTABLE_SUBSET,
        NonceType.NONCE_TYPE_FIXED,
    ).associateBy { it.toString() }

private val lowEntropyModeValues =
    listOf(
        LowEntropyMode.LOW_ENTROPY_MODE_OFF,
        LowEntropyMode.LOW_ENTROPY_MODE_32,
        LowEntropyMode.LOW_ENTROPY_MODE_40,
        LowEntropyMode.LOW_ENTROPY_MODE_48,
        LowEntropyMode.LOW_ENTROPY_MODE_56,
    ).associateBy { it.toString() }

private val lowEntropyMaskRotationValues =
    listOf(
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_NO_ROTATION,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_1,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_2,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_3,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_4,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_5,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_6,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_7,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_8,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_9,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_10,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_11,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_12,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_13,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_14,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_RIGHT_15,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_1,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_2,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_3,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_4,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_5,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_6,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_7,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_8,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_9,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_10,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_11,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_12,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_13,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_14,
        LowEntropyMaskRotation.LOW_ENTROPY_MASK_ROTATE_LEFT_15,
    ).associateBy { it.toString() }

private fun trafficPattern(element: JsonElement): TrafficPattern {
    val fields = element.checkedObject("seed", "unlockAll", "tcpFragment", "nonce", "padding", "lowEntropy")
    return TrafficPattern(
        seed = fields.optional("seed") { primitive<Int>(it) },
        unlockAll = fields.optional("unlockAll") { primitive<Boolean>(it) },
        tcpFragment = fields.optional("tcpFragment", ::tcpFragment),
        nonce = fields.optional("nonce", ::noncePattern),
        padding = fields.optional("padding", ::paddingPattern),
        lowEntropy = fields.optional("lowEntropy", ::lowEntropyPattern),
    )
}

private fun TrafficPattern.toJson(): JsonObject = buildJsonObject {
    seed?.let { put("seed", JsonPrimitive(it)) }
    unlockAll?.let { put("unlockAll", JsonPrimitive(it)) }
    tcpFragment?.let { put("tcpFragment", it.toJson()) }
    nonce?.let { put("nonce", it.toJson()) }
    padding?.let { put("padding", it.toJson()) }
    lowEntropy?.let { put("lowEntropy", it.toJson()) }
}

private fun tcpFragment(element: JsonElement): TCPFragment {
    val fields = element.checkedObject("enable", "maxSleepMs")
    return TCPFragment(
        enable = fields.optional("enable") { primitive<Boolean>(it) },
        maxSleepMs = fields.optional("maxSleepMs") { primitive<Int>(it) },
    )
}

private fun TCPFragment.toJson(): JsonObject = buildJsonObject {
    enable?.let { put("enable", JsonPrimitive(it)) }
    maxSleepMs?.let { put("maxSleepMs", JsonPrimitive(it)) }
}

private fun noncePattern(element: JsonElement): NoncePattern {
    val fields =
        element.checkedObject("type", "applyToAllUDPPacket", "minLen", "maxLen", "customHexStrings")
    return NoncePattern(
        type = fields.optional("type") { enumValue(it, nonceTypeValues) },
        applyToAllUDPPacket = fields.optional("applyToAllUDPPacket") { primitive<Boolean>(it) },
        minLen = fields.optional("minLen") { primitive<Int>(it) },
        maxLen = fields.optional("maxLen") { primitive<Int>(it) },
        customHexStrings = fields.optional("customHexStrings") {
            primitive<List<String>>(it)
        }.orEmpty(),
    )
}

private fun NoncePattern.toJson(): JsonObject = buildJsonObject {
    type?.let { put("type", enumJson(it, nonceTypeValues)) }
    applyToAllUDPPacket?.let { put("applyToAllUDPPacket", JsonPrimitive(it)) }
    minLen?.let { put("minLen", JsonPrimitive(it)) }
    maxLen?.let { put("maxLen", JsonPrimitive(it)) }
    if (customHexStrings.isNotEmpty()) {
        put("customHexStrings", JsonArray(customHexStrings.map { JsonPrimitive(it) }))
    }
}

private fun paddingPattern(element: JsonElement): PaddingPattern {
    val fields = element.checkedObject("maxMiddlePaddingLen", "maxEndPaddingLen")
    return PaddingPattern(
        maxMiddlePaddingLen = fields.optional("maxMiddlePaddingLen") { primitive<Int>(it) },
        maxEndPaddingLen = fields.optional("maxEndPaddingLen") { primitive<Int>(it) },
    )
}

private fun PaddingPattern.toJson(): JsonObject = buildJsonObject {
    maxMiddlePaddingLen?.let { put("maxMiddlePaddingLen", JsonPrimitive(it)) }
    maxEndPaddingLen?.let { put("maxEndPaddingLen", JsonPrimitive(it)) }
}

private fun lowEntropyPattern(element: JsonElement): LowEntropyPattern {
    val fields = element.checkedObject("mode", "maskRotation")
    return LowEntropyPattern(
        mode = fields.optional("mode") { enumValue(it, lowEntropyModeValues) },
        maskRotation = fields.optional("maskRotation") {
            enumValue(it, lowEntropyMaskRotationValues)
        },
    )
}

private fun LowEntropyPattern.toJson(): JsonObject = buildJsonObject {
    mode?.let { put("mode", enumJson(it, lowEntropyModeValues)) }
    maskRotation?.let { put("maskRotation", enumJson(it, lowEntropyMaskRotationValues)) }
}
