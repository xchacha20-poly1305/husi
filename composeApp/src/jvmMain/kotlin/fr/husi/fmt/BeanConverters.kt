package fr.husi.fmt

import androidx.room3.ColumnTypeConverter
import fr.husi.database.SubscriptionBean
import fr.husi.fmt.anytls.AnyTLSBean
import fr.husi.fmt.config.ConfigBean
import fr.husi.fmt.direct.DirectBean
import fr.husi.fmt.http.HttpBean
import fr.husi.fmt.hysteria.HysteriaBean
import fr.husi.fmt.internal.ChainBean
import fr.husi.fmt.internal.ProxySetBean
import fr.husi.fmt.juicity.JuicityBean
import fr.husi.fmt.masque.MASQUEBean
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
import fr.husi.fmt.tailscale.TailcatBean
import fr.husi.fmt.tailscale.TailscaleBean
import fr.husi.fmt.trojan.TrojanBean
import fr.husi.fmt.trusttunnel.TrustTunnelBean
import fr.husi.fmt.tuic.TuicBean
import fr.husi.fmt.v2ray.VLESSBean
import fr.husi.fmt.v2ray.VMessBean
import fr.husi.fmt.wireguard.WireGuardBean
import fr.husi.io.BinaryFormatException
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput
import fr.husi.ktx.Logs

/** Turns the beans stored in Room `BLOB` columns into bytes and back. */
class BeanConverters {

    companion object {
        private val NULL = ByteArray(0)

        @JvmStatic
        fun serialize(bean: Serializable?): ByteArray {
            if (bean == null) return NULL
            val output = BinaryOutput()
            bean.serializeToBuffer(output)
            return output.toByteArray()
        }

        // Room handles null beans as SQL NULL; readers also accept legacy empty blobs.
        @ColumnTypeConverter
        @JvmStatic
        fun serializeForRoom(bean: Serializable): ByteArray = serialize(bean)

        @JvmStatic
        fun <T : Serializable> deserialize(bean: T, bytes: ByteArray?): T {
            if (bytes == null) return bean
            try {
                bean.deserializeFromBuffer(BinaryInput(bytes))
            } catch (e: BinaryFormatException) {
                // A payload written by an older husi version simply ends early; keep what it held.
                Logs.w(e)
            }
            bean.initializeDefaultValues()
            return bean
        }

        @ColumnTypeConverter
        @JvmStatic
        fun socksDeserialize(bytes: ByteArray?): SOCKSBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(SOCKSBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun httpDeserialize(bytes: ByteArray?): HttpBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(HttpBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun shadowsocksDeserialize(bytes: ByteArray?): ShadowsocksBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ShadowsocksBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun snellDeserialize(bytes: ByteArray?): SnellBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(SnellBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun configDeserialize(bytes: ByteArray?): ConfigBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ConfigBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun vmessDeserialize(bytes: ByteArray?): VMessBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(VMessBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun vlessDeserialize(bytes: ByteArray?): VLESSBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(VLESSBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun trojanDeserialize(bytes: ByteArray?): TrojanBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(TrojanBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun mieruDeserialize(bytes: ByteArray?): MieruBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(MieruBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun naiveDeserialize(bytes: ByteArray?): NaiveBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(NaiveBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun hysteriaDeserialize(bytes: ByteArray?): HysteriaBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(HysteriaBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun sshDeserialize(bytes: ByteArray?): SSHBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(SSHBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun wireguardDeserialize(bytes: ByteArray?): WireGuardBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(WireGuardBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun openConnectDeserialize(bytes: ByteArray?): OpenConnectBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(OpenConnectBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun openVPNDeserialize(bytes: ByteArray?): OpenVPNBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(OpenVPNBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun masqueDeserialize(bytes: ByteArray?): MASQUEBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(MASQUEBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun tailscaleDeserialize(bytes: ByteArray?): TailscaleBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(TailscaleBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun tailcatDeserialize(bytes: ByteArray?): TailcatBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(TailcatBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun tuicDeserialize(bytes: ByteArray?): TuicBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(TuicBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun juicityDeserialize(bytes: ByteArray?): JuicityBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(JuicityBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun directDeserialize(bytes: ByteArray?): DirectBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(DirectBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun anyTLSDeserialize(bytes: ByteArray?): AnyTLSBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(AnyTLSBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun shadowTLSDeserialize(bytes: ByteArray?): ShadowTLSBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ShadowTLSBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun shadowQUICDeserialize(bytes: ByteArray?): ShadowQUICBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ShadowQUICBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun proxySetDeserialize(bytes: ByteArray?): ProxySetBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ProxySetBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun trustTunnelDeserialize(bytes: ByteArray?): TrustTunnelBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(TrustTunnelBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun chainDeserialize(bytes: ByteArray?): ChainBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ChainBean(), bytes)
        }

        @ColumnTypeConverter
        @JvmStatic
        fun subscriptionDeserialize(bytes: ByteArray?): SubscriptionBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(SubscriptionBean(), bytes)
        }
    }
}
