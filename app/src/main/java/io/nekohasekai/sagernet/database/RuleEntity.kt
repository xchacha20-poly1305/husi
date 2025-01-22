package io.nekohasekai.sagernet.database

import android.os.Parcelable
import androidx.room.*
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.app
import kotlinx.parcelize.Parcelize

@Entity(tableName = "rules")
@Parcelize
@TypeConverters(StringCollectionConverter::class)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var name: String = "",
    var userOrder: Long = 0L,
    var enabled: Boolean = false,
    var domains: String = "",
    var ip: String = "",
    var port: String = "",
    var sourcePort: String = "",
    var network: String = "",
    var source: String = "",
    var protocol: String = "",
    var outbound: Long = 0,
    var packages: Set<String> = emptySet(),
    var ssid: String = "",
    var bssid: String = "",
    @ColumnInfo(defaultValue = "") var clientType: String = "",
    @ColumnInfo(defaultValue = "") var clashMode: String = "",
    @ColumnInfo(defaultValue = "") var networkType: Set<String> = emptySet(),
    @ColumnInfo(defaultValue = "0") var networkIsExpensive: Boolean = false,
) : Parcelable {

    companion object {
        const val OUTBOUND_PROXY = 0L
        const val OUTBOUND_DIRECT = -1L
        const val OUTBOUND_BLOCK = -2L

        // Use lower case to adapt with clash dashboard
        const val MODE_RULE = "rule"
        const val MODE_DIRECT = "direct"
        const val MODE_GLOBAL = "global"
        const val MODE_BLOCK = "block"
    }

    fun displayName(): String {
        return name.takeIf { it.isNotBlank() } ?: "Rule $id"
    }

    fun mkSummary(): String {
        var summary = ""
        if (domains.isNotBlank()) summary += "$domains\n"
        if (ip.isNotBlank()) summary += "$ip\n"
        if (source.isNotBlank()) summary += "source: $source\n"
        if (sourcePort.isNotBlank()) summary += "sourcePort: $sourcePort\n"
        if (port.isNotBlank()) summary += "port: $port\n"
        if (network.isNotBlank()) summary += "network: $network\n"
        if (protocol.isNotBlank()) summary += "protocol: $protocol\n"
        if (packages.isNotEmpty()) summary += app.getString(
            R.string.apps_message, packages.size
        ) + "\n"
        if (ssid.isNotBlank()) summary += "ssid: $ssid\n"
        if (bssid.isNotBlank()) summary += "bssid: $bssid\n"
        if (clientType.isNotBlank()) summary += "client: $clientType\n"
        if (clashMode.isNotBlank()) summary += "clashMode: $clashMode\n"
        if (networkType.isNotEmpty()) summary += "networkType: $networkType\n"
        if (networkIsExpensive) summary += "networkIsExpensive\n"
        val lines = summary.trim().split("\n")
        return if (lines.size > 3) {
            lines.subList(0, 3).joinToString("\n", postfix = "\n...")
        } else {
            summary.trim()
        }
    }

    fun displayOutbound(): String {
        return when (outbound) {
            OUTBOUND_PROXY -> app.getString(R.string.route_proxy)
            OUTBOUND_DIRECT -> app.getString(R.string.route_bypass)
            OUTBOUND_BLOCK -> app.getString(R.string.route_block)
            else -> ProfileManager.getProfile(outbound)?.displayName()
                ?: app.getString(R.string.error_title)
        }
    }

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * from rules WHERE (packages != '') AND enabled = 1")
        fun checkVpnNeeded(): List<RuleEntity>

        @Query("SELECT * FROM rules ORDER BY userOrder")
        fun allRules(): List<RuleEntity>

        @Query("SELECT * FROM rules WHERE enabled = :enabled ORDER BY userOrder")
        fun enabledRules(enabled: Boolean = true): List<RuleEntity>

        @Query("SELECT MAX(userOrder) + 1 FROM rules")
        fun nextOrder(): Long?

        @Query("SELECT * FROM rules WHERE id = :ruleId")
        fun getById(ruleId: Long): RuleEntity?

        @Query("DELETE FROM rules WHERE id = :ruleId")
        fun deleteById(ruleId: Long): Int

        @Delete
        fun deleteRule(rule: RuleEntity)

        @Delete
        fun deleteRules(rules: List<RuleEntity>)

        @Insert
        fun createRule(rule: RuleEntity): Long

        @Update
        fun updateRule(rule: RuleEntity)

        @Update
        fun updateRules(rules: List<RuleEntity>)

        @Query("DELETE FROM rules")
        fun reset()

        @Insert
        fun insert(rules: List<RuleEntity>)

    }


}


