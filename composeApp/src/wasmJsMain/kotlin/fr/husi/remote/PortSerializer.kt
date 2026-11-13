package fr.husi.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.intOrNull

object PortSerializer : JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val primitive = element as? JsonPrimitive
            ?: throw SerializationException("Clash connection port must be a string or integer")
        if (primitive.isString) return primitive
        val port = primitive.intOrNull?.takeIf { it in 0..65535 }
            ?: throw SerializationException("Clash connection port must be a string or valid port number")
        return JsonPrimitive(port.toString())
    }
}
