package io.nekohasekai.sagernet.database

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Update
import io.nekohasekai.sagernet.fmt.SingBoxOptions
import kotlinx.parcelize.Parcelize

@Entity(tableName = "rules")
@Parcelize
@TypeConverters(StringCollectionConverter::class)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var name: String = "",
    var userOrder: Long = 0L,
    var enabled: Boolean = false,

    // common rules
    var domains: String = "",
    var ip: String = "",
    var port: String = "",
    var sourcePort: String = "",
    var network: Set<String> = emptySet(),
    var source: String = "",
    var protocol: Set<String> = emptySet(),
    @ColumnInfo(defaultValue = "") var clientType: String = "",
    var packages: Set<String> = emptySet(),
    var ssid: String = "",
    var bssid: String = "",
    @ColumnInfo(defaultValue = "") var clashMode: String = "",
    @ColumnInfo(defaultValue = "") var networkType: Set<String> = emptySet(),
    @ColumnInfo(defaultValue = "0") var networkIsExpensive: Boolean = false,
    @ColumnInfo(defaultValue = "") var networkInterfaceAddress: LinkedHashMap<String, String> = LinkedHashMap(),

    // Rule action

    @ColumnInfo(defaultValue = "") var action: String = SingBoxOptions.ACTION_ROUTE,

    // action: route
    var outbound: Long = 0,

    // action: route-options
    @ColumnInfo(defaultValue = "") var overrideAddress: String = "",
    @ColumnInfo(defaultValue = "0") var overridePort: Int = 0,
    @ColumnInfo(defaultValue = "0") var tlsFragment: Boolean = false,
    @ColumnInfo(defaultValue = "0") var tlsRecordFragment: Boolean = false,
    @ColumnInfo(defaultValue = "") var tlsFragmentFallbackDelay: String = "",

    // action: resolve
    @ColumnInfo(defaultValue = "") var resolveStrategy: String = "",
    @ColumnInfo(defaultValue = "0") var resolveDisableCache: Boolean = false,
    @ColumnInfo(defaultValue = "-1") var resolveRewriteTTL: Int = -1,
    @ColumnInfo(defaultValue = "") var resolveClientSubnet: String = "",

    // action: sniff
    @ColumnInfo(defaultValue = "") var sniffTimeout: String = "",
    @ColumnInfo(defaultValue = "") var sniffers: Set<String> = emptySet(),

    @ColumnInfo(defaultValue = "") var customConfig: String = "",
    @ColumnInfo(defaultValue = "") var customDnsConfig: String = "",
) : Parcelable {

    companion object {
        const val OUTBOUND_PROXY = 0L
        const val OUTBOUND_DIRECT = -1L
        const val OUTBOUND_BLOCK = -2L

        // Clash Modes
        // Use lower case to adapt with clash dashboard
        const val MODE_RULE = "rule"
        const val MODE_DIRECT = "direct"
        const val MODE_GLOBAL = "global"
        const val MODE_BLOCK = "block"
    }

    fun displayName(): String {
        return name.takeIf { it.isNotBlank() } ?: "Rule $id"
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


