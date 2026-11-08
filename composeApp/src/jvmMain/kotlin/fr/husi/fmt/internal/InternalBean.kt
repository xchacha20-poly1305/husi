package fr.husi.fmt.internal

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.fmt.AbstractBean

@KxsSerializable
abstract class InternalBean : AbstractBean() {

    override fun displayAddress(): String {
        return ""
    }

    override val canICMPing get() = false
    override val canTCPing get() = false
    override val canMapping get() = false
}
