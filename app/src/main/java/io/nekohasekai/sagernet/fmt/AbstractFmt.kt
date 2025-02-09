package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.fmt.direct.DirectBean
import io.nekohasekai.sagernet.fmt.direct.buildSingBoxOutboundDirectBean
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.buildSingBoxOutboundHysteriaBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.buildSingBoxOutboundShadowsocksBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.socks.buildSingBoxOutboundSocksBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.ssh.buildSingBoxOutboundSSHBean
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.tuic.buildSingBoxOutboundTuicBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.buildSingBoxOutboundStandardV2RayBean
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.fmt.wireguard.buildSingBoxEndpointWireGuardBean
import io.nekohasekai.sagernet.fmt.config.ConfigBean
import moe.matsuri.nb4a.utils.JavaUtil.gson

fun buildSingBoxOutbound(bean: AbstractBean): String {
    val map = when (bean) {
        is ConfigBean -> return bean.config // What if full config?
        is DirectBean -> buildSingBoxOutboundDirectBean(bean)
        is StandardV2RayBean -> buildSingBoxOutboundStandardV2RayBean(bean)
        is HysteriaBean -> buildSingBoxOutboundHysteriaBean(bean)
        is ShadowsocksBean -> buildSingBoxOutboundShadowsocksBean(bean)
        is SOCKSBean -> buildSingBoxOutboundSocksBean(bean)
        is SSHBean -> buildSingBoxOutboundSSHBean(bean)
        is TuicBean -> buildSingBoxOutboundTuicBean(bean)
        is WireGuardBean -> buildSingBoxEndpointWireGuardBean(bean) // is it outbound?
        else -> error("invalid bean: ${bean.javaClass.simpleName}")
    }
    map.type = bean.outboundType()
    map.tag = bean.name
    return gson.toJson(map)
}