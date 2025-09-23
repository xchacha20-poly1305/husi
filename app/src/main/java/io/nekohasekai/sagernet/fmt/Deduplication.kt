package io.nekohasekai.sagernet.fmt

 class Deduplication(
    val bean: AbstractBean,
    val type: String,
) {

    private fun hash(): String {
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