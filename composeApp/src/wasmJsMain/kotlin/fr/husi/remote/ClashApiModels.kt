package fr.husi.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ClashVersion(
    val version: String,
)

@Serializable
internal data class ClashProxy(
    val name: String,
    val type: String,
    @SerialName("all") val members: List<String>? = null,
    @SerialName("now") val selected: String = "",
    val history: List<ClashDelayHistory> = emptyList(),
) {
    val selectable: Boolean
        get() = type == "Selector"

    val delay: Int
        get() = history.lastOrNull()?.delay ?: -1
}

@Serializable
internal data class ClashDelayHistory(
    val time: String,
    val delay: Int,
)

@Serializable
internal data class ClashDelay(
    val delay: Int,
)

@Serializable
internal data class ClashConfig(
    val mode: String,
    @SerialName("mode-list") val modeList: List<String> = emptyList(),
    val modes: List<String> = emptyList(),
) {
    val availableModes: List<String>
        get() = modeList.ifEmpty { modes }.ifEmpty { listOf("rule", "global", "direct") }
}

@Serializable
internal data class ClashLog(
    @SerialName("type") val level: String,
    @SerialName("payload") val message: String,
)

@Serializable
internal data class ClashTraffic(
    val up: Long,
    val down: Long,
)

@Serializable
internal data class ClashMemory(
    val inuse: Long,
)

@Serializable
internal data class ClashConnections(
    val uploadTotal: Long,
    val downloadTotal: Long,
    val memory: Long? = null,
    val connections: List<ClashConnection> = emptyList(),
)

@Serializable
internal data class ClashConnection(
    val id: String,
    val upload: Long,
    val download: Long,
    val start: String,
    val chains: List<String>,
    val rule: String,
    val rulePayload: String,
    val metadata: ClashConnectionMetadata,
)

@Serializable
internal data class ClashConnectionMetadata(
    val network: String,
    val type: String,
    val sourceIP: String,
    @Serializable(with = PortSerializer::class) val sourcePort: String,
    val destinationIP: String,
    @Serializable(with = PortSerializer::class) val destinationPort: String,
    val host: String,
    val processPath: String = "",
)

@Serializable
internal data class ClashRule(
    val type: String,
    val payload: String,
    @SerialName("proxy") val target: String,
    val uuid: String = "",
    val toggleable: Boolean = false,
    val extra: ClashRuleExtra? = null,
    @SerialName("disabled") private val uuidDisabled: Boolean = false,
) {
    val disabled: Boolean
        get() = extra?.disabled ?: uuidDisabled
}

@Serializable
internal data class ClashRuleExtra(
    val disabled: Boolean = false,
)

@Serializable
internal data class ClashSubscriptionInfo(
    @SerialName("Upload") val upload: Long,
    @SerialName("Download") val download: Long,
    @SerialName("Total") val total: Long,
    @SerialName("Expire") val expire: Long,
)

@Serializable
internal data class ClashProxyProvider(
    val name: String,
    val type: String,
    val vehicleType: String,
    val updatedAt: String = "",
    @SerialName("subscriptionInfo") val subscription: ClashSubscriptionInfo? = null,
    val proxies: List<ClashProxy> = emptyList(),
)

@Serializable
internal data class ClashRuleProvider(
    val name: String,
    val type: String,
    val behavior: String,
    val format: String = "",
    val ruleCount: Long,
    val updatedAt: String,
)

@Serializable
internal data class ClashDnsQuestion(
    @SerialName("Name") val name: String,
    @SerialName("Qtype") val type: Int,
)

@Serializable
internal data class ClashDnsRecord(
    val name: String,
    val type: Int,
    @SerialName("TTL") val ttl: Long,
    val data: String,
)

@Serializable
internal data class ClashDnsResponse(
    @SerialName("Status") val status: Int,
    @SerialName("Question") val questions: List<ClashDnsQuestion>,
    @SerialName("Answer") val answers: List<ClashDnsRecord> = emptyList(),
    @SerialName("Authority") val authority: List<ClashDnsRecord> = emptyList(),
    @SerialName("Additional") val additional: List<ClashDnsRecord> = emptyList(),
)
