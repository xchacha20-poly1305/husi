package fr.husi.database

import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import fr.husi.GroupOrder
import fr.husi.GroupType
import fr.husi.fmt.Serializable
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput
import fr.husi.ktx.applyDefaultValues
import fr.husi.ktx.blankAsNull
import fr.husi.repository.resolveRepository
import fr.husi.resources.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

/**
 * A group is a container of proxies.
 *
 * A group-level option is only allowed if it acts on a proxy's own server address. Every proxy
 * belongs to exactly one group, so such an option stays unambiguous even when a chain or proxy
 * set imports proxies from other groups. [SubscriptionBean.forceResolve] and [outboundDns] are
 * examples of this kind.
 *
 * NEVER add an option that acts on the traffic passing through a proxy: front/landing proxy,
 * group-level uTLS fingerprint, or making a group a selector. That traffic can belong to whoever
 * referenced the proxy through a chain or proxy set in another group, so there is no single
 * group whose option should apply.
 */
@Entity(tableName = "proxy_groups")
data class ProxyGroup(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var userOrder: Long = 0L,
    var ungrouped: Boolean = false,
    var name: String? = null,
    var type: Int = GroupType.BASIC,
    var subscription: SubscriptionBean? = null,
    var order: Int = GroupOrder.ORIGIN,
    var outboundDns: String? = null,
) : Serializable() {

    @Transient
    var export = false

    override fun initializeDefaultValues() {
        subscription?.applyDefaultValues()
    }

    override fun serializeToBuffer(output: BinaryOutput) {
        if (export) {

            output.writeInt(0)
            output.writeString(name)
            output.writeInt(type)
            val subscription = subscription!!
            subscription.serializeForShare(output)

        } else {
            output.writeInt(1)
            output.writeLong(id)
            output.writeLong(userOrder)
            output.writeBoolean(ungrouped)
            output.writeString(name)
            output.writeInt(type)

            if (type == GroupType.SUBSCRIPTION) {
                subscription?.serializeToBuffer(output)
            }
            output.writeInt(order)
            output.writeString(outboundDns)
        }
    }

    override fun deserializeFromBuffer(input: BinaryInput) {
        if (export) {
            val version = input.readInt()

            name = input.readNullableString()
            type = input.readInt()
            val subscription = SubscriptionBean()
            this.subscription = subscription

            subscription.deserializeFromShare(input)
        } else {
            val version = input.readInt()

            id = input.readLong()
            userOrder = input.readLong()
            ungrouped = input.readBoolean()
            name = input.readNullableString()
            type = input.readInt()

            if (type == GroupType.SUBSCRIPTION) {
                val subscription = SubscriptionBean()
                this.subscription = subscription

                subscription.deserializeFromBuffer(input)
            }
            order = input.readInt()
            if (version >= 1) {
                outboundDns = input.readNullableString()
            }
        }
    }

    fun displayName(): String {
        return name.blankAsNull() ?: runBlocking {
            resolveRepository().getString(Res.string.group_default)
        }
    }

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * FROM proxy_groups ORDER BY userOrder, id")
        fun allGroups(): Flow<List<ProxyGroup>>

        @Query("SELECT * FROM proxy_groups WHERE type = ${GroupType.SUBSCRIPTION}")
        suspend fun subscriptions(): List<ProxyGroup>

        @Query("SELECT MAX(userOrder) + 1 FROM proxy_groups")
        suspend fun nextOrder(): Long?

        @Query("SELECT id FROM proxy_groups ORDER BY userOrder, id LIMIT 1")
        suspend fun firstGroupId(): Long?

        @Query("SELECT id FROM proxy_groups WHERE ungrouped = 1 LIMIT 1")
        suspend fun ungroupedId(): Long?

        @Query("SELECT * FROM proxy_groups WHERE id = :groupId")
        fun getById(groupId: Long): Flow<ProxyGroup?>

        @Query("DELETE FROM proxy_groups WHERE id = :groupId")
        suspend fun deleteById(groupId: Long): Int

        @Query("DELETE FROM proxy_groups WHERE id IN (:groupIDs)")
        suspend fun deleteByIds(groupIDs: List<Long>): Int

        @Delete
        suspend fun deleteGroup(group: ProxyGroup)

        @Delete
        suspend fun deleteGroup(groupList: List<ProxyGroup>)

        @Insert
        suspend fun createGroup(group: ProxyGroup): Long

        @Update
        suspend fun updateGroup(group: ProxyGroup)

        @Update
        suspend fun updateGroups(rules: List<ProxyGroup>)

        @Query("DELETE FROM proxy_groups")
        suspend fun reset()

        @Insert
        suspend fun insert(groupList: List<ProxyGroup>)

    }

    companion object {
        @JvmField
        val CREATOR = object : Serializable.CREATOR<ProxyGroup>() {

            override fun newInstance(): ProxyGroup {
                return ProxyGroup()
            }

            override fun newArray(size: Int): Array<ProxyGroup?> {
                return arrayOfNulls(size)
            }
        }
    }

}
