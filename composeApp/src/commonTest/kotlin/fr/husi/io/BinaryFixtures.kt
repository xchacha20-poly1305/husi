package fr.husi.io

import fr.husi.GroupOrder
import fr.husi.GroupType
import fr.husi.SubscriptionType
import fr.husi.database.ProxyEntity
import fr.husi.database.ProxyGroup
import fr.husi.database.SubscriptionBean
import fr.husi.fmt.Serializable
import fr.husi.fmt.anytls.AnyTLSBean
import fr.husi.fmt.config.ConfigBean
import fr.husi.fmt.direct.DirectBean
import fr.husi.fmt.http.HttpBean
import fr.husi.fmt.hysteria.HysteriaBean
import fr.husi.fmt.internal.ChainBean
import fr.husi.fmt.internal.ProxySetBean
import fr.husi.fmt.juicity.JuicityBean
import fr.husi.fmt.mieru.MieruBean
import fr.husi.fmt.naive.NaiveBean
import fr.husi.fmt.openconnect.OpenConnectBean
import fr.husi.fmt.openvpn.OpenVPNBean
import fr.husi.fmt.shadowquic.ShadowQUICBean
import fr.husi.fmt.shadowsocks.ShadowsocksBean
import fr.husi.fmt.shadowtls.ShadowTLSBean
import fr.husi.fmt.snell.SnellBean
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.fmt.ssh.SSHBean
import fr.husi.fmt.trojan.TrojanBean
import fr.husi.fmt.trusttunnel.TrustTunnelBean
import fr.husi.fmt.tuic.TuicBean
import fr.husi.fmt.v2ray.StandardV2RayBean
import fr.husi.fmt.v2ray.VLESSBean
import fr.husi.fmt.v2ray.VMessBean
import fr.husi.fmt.wireguard.WireGuardBean
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.math.absoluteValue

object BinaryFixtures {

    /** Strings of 2..32 pure-ASCII characters are written without a length prefix. */
    const val ASCII_FAST_PATH_MAX_LENGTH = 32

    class Fixture(
        val name: String,
        val build: () -> Serializable,
        val newInstance: () -> Serializable,
    )

    val fixtures: List<Fixture> = listOf(
        simple("AnyTLSBean", ::AnyTLSBean),
        simple("ChainBean", ::ChainBean),
        simple("ConfigBean", ::ConfigBean),
        simple("DirectBean", ::DirectBean),
        simple("HttpBean", ::HttpBean),
        simple("HysteriaBean", ::HysteriaBean),
        simple("JuicityBean", ::JuicityBean),
        mieru("MieruBean", MieruBean.PROTOCOL_TCP),
        mieru("MieruBean-udp", MieruBean.PROTOCOL_UDP),
        simple("NaiveBean", ::NaiveBean),
        simple("OpenConnectBean", ::OpenConnectBean),
        Fixture("OpenVPNBean", {
            OpenVPNBean().also {
                populate(it)
                it.network = "tcp" // Anything else is rewritten to "udp" when decoding.
            }
        }, ::OpenVPNBean),
        Fixture("ShadowQUICBean", {
            ShadowQUICBean().also {
                populate(it)
                it.congestionControl = "bbr" // An underscore is rewritten to a dash when decoding.
            }
        }, ::ShadowQUICBean),
        simple("ShadowsocksBean", ::ShadowsocksBean),
        simple("ShadowTLSBean", ::ShadowTLSBean),
        snell("SnellBean", SnellBean.VERSION_4),
        snell("SnellBean-v6", SnellBean.VERSION_6),
        simple("SOCKSBean", ::SOCKSBean),
        simple("SSHBean", ::SSHBean),
        simple("SubscriptionBean", ::SubscriptionBean),
        simple("TrojanBean", ::TrojanBean),
        simple("TrustTunnelBean", ::TrustTunnelBean),
        simple("TuicBean", ::TuicBean),
        simple("VLESSBean", ::VLESSBean),
        simple("VMessBean", ::VMessBean),
        simple("WireGuardBean", ::WireGuardBean),

        // A string is the only field whose encoding branches on its own content, so one bean gets
        // a second fixture that walks every branch of `writeString`.
        Fixture("VMessBean-strings", {
            VMessBean().also {
                populate(it)
                it.name = "a" // Length 1 takes the UTF-8 branch, not the ASCII fast path.
                it.serverAddress = "x".repeat(ASCII_FAST_PATH_MAX_LENGTH)
                it.customOutboundJson = "y".repeat(ASCII_FAST_PATH_MAX_LENGTH + 1)
                it.customConfigJson = "中文 emoji 😀 tail"
                it.uuid = "߿ࠀ" // The two-byte / three-byte boundary of the encoding.
                it.encryption = "auto"
            }
        }, ::VMessBean),

        Fixture("VLESSBean-grpc", {
            VLESSBean().also {
                populate(it)
                it.v2rayTransport = "grpc"
            }
        }, ::VLESSBean),

        Fixture("TrojanBean-plain", {
            TrojanBean().also {
                populate(it)
                it.v2rayTransport = ""
                it.security = ""
            }
        }, ::TrojanBean),

        Fixture("HttpBean-httpupgrade", {
            HttpBean().also {
                populate(it)
                it.v2rayTransport = "httpupgrade"
            }
        }, ::HttpBean),

        Fixture("ProxySetBean", {
            ProxySetBean().also {
                populate(it)
                it.management = ProxySetBean.MANAGEMENT_URLTEST
                it.providers = listOf(
                    ProxySetBean.Provider.Single(7L),
                    ProxySetBean.Provider.Group(9L, "^premium"),
                    ProxySetBean.Provider.Single(11L),
                )
            }
        }, ::ProxySetBean),

        Fixture("ProxyGroup", {
            ProxyGroup(
                id = 12L,
                userOrder = 34L,
                ungrouped = true,
                name = "group name",
                type = GroupType.SUBSCRIPTION,
                subscription = SubscriptionBean().also {
                    populate(it)
                    it.type = SubscriptionType.RAW
                },
                order = GroupOrder.BY_NAME,
                frontProxy = 56L,
                landingProxy = 78L,
            )
        }, ::ProxyGroup),

        Fixture("ProxyGroup-nullName", {
            ProxyGroup(id = 1L, name = null, type = GroupType.BASIC)
        }, ::ProxyGroup),

        Fixture("ProxyEntity", {
            ProxyEntity(
                id = 101L,
                groupId = 202L,
                type = ProxyEntity.TYPE_VMESS,
                userOrder = 303L,
                tx = 404L,
                rx = 505L,
                status = ProxyEntity.STATUS_AVAILABLE,
                ping = 42,
                error = "boom",
                vmessBean = VMessBean().also { populate(it) },
            ).also { it.dirty = true }
        }, ::ProxyEntity),

        Fixture("ProxyEntity-nullError", {
            ProxyEntity(
                id = 1L,
                type = ProxyEntity.TYPE_PROXY_SET,
                error = null,
                proxySetBean = ProxySetBean().also {
                    populate(it)
                    it.management = ProxySetBean.MANAGEMENT_SELECTOR
                    it.providers = listOf(ProxySetBean.Provider.Group(3L, ""))
                },
            )
        }, ::ProxyEntity),
    )

    private fun mieru(name: String, protocol: String) = Fixture(
        name = name,
        build = {
            MieruBean().also {
                populate(it)
                it.protocol = protocol
            }
        },
        newInstance = ::MieruBean,
    )

    private fun snell(name: String, version: Int) = Fixture(
        name = name,
        build = {
            SnellBean().also {
                populate(it)
                it.version = version
            }
        },
        newInstance = ::SnellBean,
    )

    private fun simple(name: String, newInstance: () -> Serializable) = Fixture(
        name = name,
        build = { newInstance().also { populate(it) } },
        newInstance = newInstance,
    )

    /**
     * Fills every mutable field of [target] with a value derived from its name, so the fixture
     * covers every field without hand-writing a setter per field.
     *
     * Numbers stay inside the ranges `initializeDefaultValues` accepts; otherwise decoding would
     * rewrite them and the re-encoded bytes would no longer match the golden ones.
     */
    fun populate(target: Any) {
        for (field in fieldsOf(target.javaClass)) {
            if (Modifier.isFinal(field.modifiers)) continue
            if (field.name in UNSERIALIZED_FIELDS) continue
            val seed = field.name.hashCode().absoluteValue
            when (field.type) {
                String::class.java -> field.set(target, "v_" + field.name)
                Int::class.javaPrimitiveType -> field.setInt(target, seed % 60000 + 1)
                Long::class.javaPrimitiveType -> field.setLong(target, seed.toLong() + 1L)
                Boolean::class.javaPrimitiveType -> field.setBoolean(target, true)
            }
        }
        // A few strings are read back as an enumeration rather than as free text, and both the
        // encoder and `initializeDefaultValues` branch on them.
        if (target is StandardV2RayBean) {
            target.v2rayTransport = "ws"
            target.security = "tls"
        }
    }

    private val UNSERIALIZED_FIELDS = setOf("dirty", "export")

    /** A stable, human-readable dump of every field, used to detect silent decoding drift. */
    fun snapshot(value: Any?): String = when (value) {
        null -> "null"
        is String, is Int, is Long, is Boolean, is Byte -> value.toString()
        is List<*> -> value.joinToString(prefix = "[", postfix = "]") { snapshot(it) }
        else -> fieldsOf(value.javaClass).joinToString(
            separator = ", ",
            prefix = value.javaClass.simpleName + "{",
            postfix = "}",
        ) { field -> field.name + "=" + snapshot(field.get(value)) }
    }

    private fun fieldsOf(type: Class<*>): List<Field> {
        val fields = mutableListOf<Field>()
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (field.isSynthetic) continue
                if (Modifier.isStatic(field.modifiers)) continue
                field.isAccessible = true
                fields += field
            }
            current = current.superclass
        }
        fields.sortBy { it.declaringClass.name + "#" + it.name }
        return fields
    }
}
