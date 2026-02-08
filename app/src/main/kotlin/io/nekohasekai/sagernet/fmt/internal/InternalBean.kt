package io.nekohasekai.sagernet.fmt.internal

import io.nekohasekai.sagernet.fmt.AbstractBean

abstract class InternalBean : AbstractBean() {

    override fun displayAddress(): String {
        return ""
    }

    override val canICMPing = false
    override val canTCPing = false
    override val canMapping = false
}
