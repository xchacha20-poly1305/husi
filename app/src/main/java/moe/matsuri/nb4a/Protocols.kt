package moe.matsuri.nb4a

import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.isTLS
import io.nekohasekai.sagernet.ktx.app

// Settings for all protocols, built-in or plugin
object Protocols {
    // Mux

    fun isProfileNeedMux(bean: StandardV2RayBean): Boolean {
        return when (bean.type) {
            "tcp", "ws" -> true
            "http" -> !bean.isTLS()
            else -> false
        }
    }

    // Deduplication

    class Deduplication(
        val bean: AbstractBean, val type: String
    ) {

        fun hash(): String {
            return bean.serverAddress + bean.serverPort + type
        }

        override fun hashCode(): Int {
            return hash().toByteArray().contentHashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Deduplication

            return hash() == other.hash()
        }

    }

    // Test

    fun genFriendlyMsg(msg: String): String {
        val msgL = msg.lowercase()
        return when {
            msgL.contains("timeout") || msgL.contains("deadline") -> {
                app.getString(R.string.connection_test_timeout)
            }

            msgL.contains("refused") || msgL.contains("closed pipe") || msgL.contains("reset") -> {
                app.getString(R.string.connection_test_refused)
            }

            msgL.contains("via clientconn.close") -> {
                app.getString(R.string.connection_test_mux)
            }

            else -> msg
        }
    }

}