package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.ktx.b64Decode
import io.nekohasekai.sagernet.ktx.b64EncodeUrlSafe
import io.nekohasekai.sagernet.ktx.zlibCompress
import io.nekohasekai.sagernet.ktx.zlibDecompress

fun parseUniversal(link: String): AbstractBean {
    return if (link.contains("?")) {
        val type = link.substringAfter("husi://").substringBefore("?")
        ProxyEntity(type = TypeMap[type] ?: error("Type $type not found")).apply {
            putByteArray(link.substringAfter("?").b64Decode().zlibDecompress())
        }.requireBean()
    } else {
        val type = link.substringAfter("husi://").substringBefore(":")
        ProxyEntity(type = TypeMap[type] ?: error("Type $type not found")).apply {
            putByteArray(link.substringAfter(":").substringAfter(":").b64Decode())
        }.requireBean()
    }
}

fun AbstractBean.toUniversalLink(): String {
    var link = "husi://"
    link += TypeMap.reversed[ProxyEntity().putBean(this).type]
    link += "?"
    link += KryoConverters.serialize(this).zlibCompress(9).b64EncodeUrlSafe()
    return link
}


fun ProxyGroup.toUniversalLink(): String {
    var link = "husi://subscription?"
    export = true
    link += KryoConverters.serialize(this).zlibCompress(9).b64EncodeUrlSafe()
    export = false
    return link
}