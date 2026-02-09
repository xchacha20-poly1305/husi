package fr.husi.fmt

import androidx.room.TypeConverter
import com.esotericsoftware.kryo.KryoException
import fr.husi.database.SubscriptionBean
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
import fr.husi.fmt.shadowquic.ShadowQUICBean
import fr.husi.fmt.shadowsocks.ShadowsocksBean
import fr.husi.fmt.shadowtls.ShadowTLSBean
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.fmt.ssh.SSHBean
import fr.husi.fmt.trojan.TrojanBean
import fr.husi.fmt.trusttunnel.TrustTunnelBean
import fr.husi.fmt.tuic.TuicBean
import fr.husi.fmt.v2ray.VLESSBean
import fr.husi.fmt.v2ray.VMessBean
import fr.husi.fmt.wireguard.WireGuardBean
import fr.husi.ktx.Logs
import fr.husi.ktx.byteBuffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class KryoConverters {

    companion object {
        private val NULL = ByteArray(0)

        @JvmStatic
        fun serialize(bean: Serializable?): ByteArray {
            if (bean == null) return NULL
            val out = ByteArrayOutputStream()
            val buffer = out.byteBuffer()
            bean.serializeToBuffer(buffer)
            buffer.flush()
            buffer.close()
            return out.toByteArray()
        }

        @TypeConverter
        @JvmStatic
        fun serializeForRoom(bean: Serializable?): ByteArray? = serialize(bean)

        @JvmStatic
        fun <T : Serializable> deserialize(bean: T, bytes: ByteArray?): T {
            if (bytes == null) return bean
            val input = ByteArrayInputStream(bytes)
            val buffer = input.byteBuffer()
            try {
                bean.deserializeFromBuffer(buffer)
            } catch (e: KryoException) {
                Logs.w(e)
            }
            bean.initializeDefaultValues()
            return bean
        }

        @TypeConverter
        @JvmStatic
        fun socksDeserialize(bytes: ByteArray?): SOCKSBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(SOCKSBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun httpDeserialize(bytes: ByteArray?): HttpBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(HttpBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun shadowsocksDeserialize(bytes: ByteArray?): ShadowsocksBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ShadowsocksBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun configDeserialize(bytes: ByteArray?): ConfigBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ConfigBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun vmessDeserialize(bytes: ByteArray?): VMessBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(VMessBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun vlessDeserialize(bytes: ByteArray?): VLESSBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(VLESSBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun trojanDeserialize(bytes: ByteArray?): TrojanBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(TrojanBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun mieruDeserialize(bytes: ByteArray?): MieruBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(MieruBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun naiveDeserialize(bytes: ByteArray?): NaiveBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(NaiveBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun hysteriaDeserialize(bytes: ByteArray?): HysteriaBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(HysteriaBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun sshDeserialize(bytes: ByteArray?): SSHBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(SSHBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun wireguardDeserialize(bytes: ByteArray?): WireGuardBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(WireGuardBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun tuicDeserialize(bytes: ByteArray?): TuicBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(TuicBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun juicityDeserialize(bytes: ByteArray?): JuicityBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(JuicityBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun directDeserialize(bytes: ByteArray?): DirectBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(DirectBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun anyTLSDeserialize(bytes: ByteArray?): AnyTLSBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(AnyTLSBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun shadowTLSDeserialize(bytes: ByteArray?): ShadowTLSBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ShadowTLSBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun shadowQUICDeserialize(bytes: ByteArray?): ShadowQUICBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ShadowQUICBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun proxySetDeserialize(bytes: ByteArray?): ProxySetBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ProxySetBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun trustTunnelDeserialize(bytes: ByteArray?): TrustTunnelBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(TrustTunnelBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun chainDeserialize(bytes: ByteArray?): ChainBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(ChainBean(), bytes)
        }

        @TypeConverter
        @JvmStatic
        fun subscriptionDeserialize(bytes: ByteArray?): SubscriptionBean? {
            if (bytes?.isNotEmpty() != true) return null
            return deserialize(SubscriptionBean(), bytes)
        }
    }
}
