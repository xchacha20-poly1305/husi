package fr.husi.fmt.internal

import fr.husi.fmt.AbstractBean

abstract class InternalBean : AbstractBean() {

    override fun displayAddress(): String {
        return ""
    }

    override val canICMPing = false
    override val canTCPing = false
    override val canMapping = false
}
