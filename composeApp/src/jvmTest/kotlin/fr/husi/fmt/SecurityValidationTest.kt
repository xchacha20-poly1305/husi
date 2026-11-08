package fr.husi.fmt

import fr.husi.fmt.http.HttpBean
import fr.husi.fmt.hysteria.HysteriaBean
import fr.husi.fmt.shadowsocks.ShadowsocksBean
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.fmt.trojan.TrojanBean
import fr.husi.fmt.v2ray.VMessBean
import fr.husi.resources.Res
import fr.husi.resources.warn_vmess_md5_auth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SecurityValidationTest {

    @Test
    fun `local address skips protocol security checks`() {
        val bean = SOCKSBean().also {
            it.serverAddress = "127.0.0.1"
        }

        assertIs<ValidateResult.Secure>(bean.isInsecure())
    }

    @Test
    fun `socks is insecure for remote address`() {
        val bean = SOCKSBean().also {
            it.serverAddress = "example.com"
        }

        assertIs<ValidateResult.Insecure>(bean.isInsecure())
    }

    @Test
    fun `shadowsocks stream cipher is insecure without protecting plugin`() {
        val bean = ShadowsocksBean().also {
            it.serverAddress = "example.com"
            it.method = "aes-256-cfb"
            it.plugin = ""
        }

        assertIs<ValidateResult.Insecure>(bean.isInsecure())
    }

    @Test
    fun `hysteria legacy protocol is deprecated`() {
        val bean = HysteriaBean().also {
            it.serverAddress = "example.com"
            it.protocolVersion = HysteriaBean.PROTOCOL_VERSION_1
        }

        assertIs<ValidateResult.Deprecated>(bean.isInsecure())
    }

    @Test
    fun `http ignores allow insecure warning`() {
        val bean = HttpBean().also {
            it.serverAddress = "example.com"
            it.security = "tls"
            it.allowInsecure = true
        }

        assertIs<ValidateResult.Secure>(bean.isInsecure())
    }

    @Test
    fun `trojan requires tls`() {
        val bean = TrojanBean().also {
            it.serverAddress = "example.com"
            it.security = ""
        }

        assertIs<ValidateResult.Insecure>(bean.isInsecure())
    }

    @Test
    fun `vmess reports alter id before tls settings`() {
        val bean = VMessBean().also {
            it.serverAddress = "example.com"
            it.alterId = 1
            it.encryption = "none"
            it.allowInsecure = true
        }

        val result = assertIs<ValidateResult.Insecure>(bean.isInsecure())
        assertEquals(Res.string.warn_vmess_md5_auth, result.textRes)
    }
}
