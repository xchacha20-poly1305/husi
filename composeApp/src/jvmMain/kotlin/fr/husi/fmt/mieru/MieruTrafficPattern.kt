@file:OptIn(ExperimentalSerializationApi::class)

package fr.husi.fmt.mieru

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/** Key under which mieru's client config, and some share links, nest the pattern. */
private const val WRAPPER_KEY = "trafficPattern"

/** Unlike the shared `kxs`, rejects what mieru would reject instead of dropping it. */
private val trafficPatternJson = Json {
    explicitNulls = false
}

/**
 * Decodes the base64 protobuf form a `mierus://` link carries into the JSON form mieru's
 * client config takes.
 */
fun decodeMieruTrafficPattern(encoded: String): JsonElement {
    val protobuf = encoded.decodeBase64()
        ?: throw IllegalArgumentException("traffic pattern is not base64")
    val pattern = ProtoBuf.decodeFromByteArray(TrafficPattern.serializer(), protobuf.toByteArray())
    return trafficPatternJson.encodeToJsonElement(TrafficPattern.serializer(), pattern)
}

/**
 * Encodes a traffic pattern in JSON, bare or nested under `trafficPattern`, into the base64
 * protobuf form of a `mierus://` link.
 */
fun encodeMieruTrafficPattern(json: String): String {
    val root = trafficPatternJson.parseToJsonElement(json) as? JsonObject
        ?: throw SerializationException("traffic pattern is not a JSON object")
    val payload = root[WRAPPER_KEY] ?: root
    val pattern = trafficPatternJson.decodeFromJsonElement(TrafficPattern.serializer(), payload)
    return ProtoBuf.encodeToByteArray(TrafficPattern.serializer(), pattern).toByteString().base64()
}

// Mirrors mieru's pkg/appctl/appctlpb/traffic_pattern.proto. Every field is proto3 `optional`,
// so null means absent and a zero value is still sent. Property and enum entry names are the
// proto names, which mieru's JSON uses as is.

@Serializable
private data class TrafficPattern(
    @ProtoNumber(1) val seed: Int? = null,
    @ProtoNumber(2) val unlockAll: Boolean? = null,
    @ProtoNumber(3) val tcpFragment: TcpFragment? = null,
    @ProtoNumber(4) val nonce: NoncePattern? = null,
    @ProtoNumber(5) val padding: PaddingPattern? = null,
    @ProtoNumber(6) val lowEntropy: LowEntropyPattern? = null,
)

@Serializable
private data class TcpFragment(
    @ProtoNumber(1) val enable: Boolean? = null,
    @ProtoNumber(2) val maxSleepMs: Int? = null,
)

@Serializable
private data class NoncePattern(
    @ProtoNumber(1) val type: NonceType? = null,
    @ProtoNumber(2) val applyToAllUDPPacket: Boolean? = null,
    @ProtoNumber(3) val minLen: Int? = null,
    @ProtoNumber(4) val maxLen: Int? = null,
    @ProtoNumber(5) val customHexStrings: List<String> = emptyList(),
)

@Serializable
private enum class NonceType {
    @ProtoNumber(0) NONCE_TYPE_RANDOM,
    @ProtoNumber(1) NONCE_TYPE_PRINTABLE,
    @ProtoNumber(2) NONCE_TYPE_PRINTABLE_SUBSET,
    @ProtoNumber(3) NONCE_TYPE_FIXED,
}

@Serializable
private data class PaddingPattern(
    @ProtoNumber(1) val maxMiddlePaddingLen: Int? = null,
    @ProtoNumber(2) val maxEndPaddingLen: Int? = null,
)

@Serializable
private data class LowEntropyPattern(
    @ProtoNumber(1) val mode: LowEntropyMode? = null,
    @ProtoNumber(2) val maskRotation: LowEntropyMaskRotation? = null,
)

@Serializable
private enum class LowEntropyMode {
    @ProtoNumber(0) LOW_ENTROPY_MODE_OFF,
    @ProtoNumber(1) LOW_ENTROPY_MODE_32,
    @ProtoNumber(2) LOW_ENTROPY_MODE_40,
    @ProtoNumber(3) LOW_ENTROPY_MODE_48,
    @ProtoNumber(4) LOW_ENTROPY_MODE_56,
}

/** Right rotations count up from 1; left rotations are the same counts shifted into the high nibble. */
@Serializable
private enum class LowEntropyMaskRotation {
    @ProtoNumber(0) LOW_ENTROPY_MASK_NO_ROTATION,
    @ProtoNumber(1) LOW_ENTROPY_MASK_ROTATE_RIGHT_1,
    @ProtoNumber(2) LOW_ENTROPY_MASK_ROTATE_RIGHT_2,
    @ProtoNumber(3) LOW_ENTROPY_MASK_ROTATE_RIGHT_3,
    @ProtoNumber(4) LOW_ENTROPY_MASK_ROTATE_RIGHT_4,
    @ProtoNumber(5) LOW_ENTROPY_MASK_ROTATE_RIGHT_5,
    @ProtoNumber(6) LOW_ENTROPY_MASK_ROTATE_RIGHT_6,
    @ProtoNumber(7) LOW_ENTROPY_MASK_ROTATE_RIGHT_7,
    @ProtoNumber(8) LOW_ENTROPY_MASK_ROTATE_RIGHT_8,
    @ProtoNumber(9) LOW_ENTROPY_MASK_ROTATE_RIGHT_9,
    @ProtoNumber(10) LOW_ENTROPY_MASK_ROTATE_RIGHT_10,
    @ProtoNumber(11) LOW_ENTROPY_MASK_ROTATE_RIGHT_11,
    @ProtoNumber(12) LOW_ENTROPY_MASK_ROTATE_RIGHT_12,
    @ProtoNumber(13) LOW_ENTROPY_MASK_ROTATE_RIGHT_13,
    @ProtoNumber(14) LOW_ENTROPY_MASK_ROTATE_RIGHT_14,
    @ProtoNumber(15) LOW_ENTROPY_MASK_ROTATE_RIGHT_15,
    @ProtoNumber(16) LOW_ENTROPY_MASK_ROTATE_LEFT_1,
    @ProtoNumber(32) LOW_ENTROPY_MASK_ROTATE_LEFT_2,
    @ProtoNumber(48) LOW_ENTROPY_MASK_ROTATE_LEFT_3,
    @ProtoNumber(64) LOW_ENTROPY_MASK_ROTATE_LEFT_4,
    @ProtoNumber(80) LOW_ENTROPY_MASK_ROTATE_LEFT_5,
    @ProtoNumber(96) LOW_ENTROPY_MASK_ROTATE_LEFT_6,
    @ProtoNumber(112) LOW_ENTROPY_MASK_ROTATE_LEFT_7,
    @ProtoNumber(128) LOW_ENTROPY_MASK_ROTATE_LEFT_8,
    @ProtoNumber(144) LOW_ENTROPY_MASK_ROTATE_LEFT_9,
    @ProtoNumber(160) LOW_ENTROPY_MASK_ROTATE_LEFT_10,
    @ProtoNumber(176) LOW_ENTROPY_MASK_ROTATE_LEFT_11,
    @ProtoNumber(192) LOW_ENTROPY_MASK_ROTATE_LEFT_12,
    @ProtoNumber(208) LOW_ENTROPY_MASK_ROTATE_LEFT_13,
    @ProtoNumber(224) LOW_ENTROPY_MASK_ROTATE_LEFT_14,
    @ProtoNumber(240) LOW_ENTROPY_MASK_ROTATE_LEFT_15,
}
