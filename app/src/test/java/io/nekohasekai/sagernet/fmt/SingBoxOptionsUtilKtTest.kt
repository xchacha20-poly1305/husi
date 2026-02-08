package io.nekohasekai.sagernet.fmt

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.nekohasekai.sagernet.fmt.SingBoxOptions.MyOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.MyDNSOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.MyRouteOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.DNSRule_Logical
import io.nekohasekai.sagernet.fmt.SingBoxOptions.DNSRule_Default
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RULE_SET_TYPE_REMOTE
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RULE_SET_TYPE_LOCAL
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RuleSet
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RuleSet_Remote
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RuleSet_Local
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Rule_Default
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Rule_Logical
import io.nekohasekai.sagernet.ktx.asMap

class SingBoxOptionsUtilKtTest {

    private lateinit var options: MyOptions

    private inline fun <reified T> buildRule(ruleSets: List<String>): T = when (T::class.java) {
        DNSRule_Default::class.java -> DNSRule_Default().apply {
            rule_set = ruleSets.toMutableList()
        }

        Rule_Default::class.java -> Rule_Default().apply {
            rule_set = ruleSets.toMutableList()
        }

        else -> throw IllegalArgumentException("Unsupported rule type")
    } as T

    private fun MyOptions.requireRuleSets(): List<RuleSet> = requireNotNull(
        requireNotNull(route) { "route should not be null" }.rule_set
    ) { "route.rule_set should not be null" }

    private fun List<RuleSet>.assertTags(expected: Set<String>) {
        assertEquals(expected.size, size)
        assertEquals(expected, mapNotNull { it.tag }.toSet())
    }

    private fun List<RuleSet>.requireRemote(tag: String): RuleSet_Remote {
        val rule = firstOrNull { it.tag == tag } ?: fail("Rule set '$tag' not found")
        return rule as? RuleSet_Remote ?: fail("Rule set '$tag' is not remote")
    }

    private fun List<RuleSet>.requireLocal(tag: String): RuleSet_Local {
        val rule = firstOrNull { it.tag == tag } ?: fail("Rule set '$tag' not found")
        return rule as? RuleSet_Local ?: fail("Rule set '$tag' is not local")
    }

    @BeforeEach
    fun setUp() {
        options = MyOptions()
    }

    @Test
    fun `buildRuleSets should do nothing if no rules are found and route is null`() {
        options.dns = MyDNSOptions().apply { rules = mutableListOf() }
        options.route = null

        options.buildRuleSets(
            ipURL = "http://ip.example.com",
            domainURL = "http://domain.example.com",
            localPath = "/data/local"
        )

        assertNull(options.route)
    }

    @Test
    fun `buildRuleSets should keep route rule_set empty if no rules are found and route already exists`() {
        options.dns = MyDNSOptions().apply { rules = mutableListOf() }
        options.route = MyRouteOptions().apply { rule_set = mutableListOf() }

        options.buildRuleSets(
            ipURL = "http://ip.example.com",
            domainURL = "http://domain.example.com",
            localPath = "/data/local"
        )

        assertEquals(emptyList<RuleSet>(), requireNotNull(options.route).rule_set)
    }

    @Test
    fun `buildRuleSets should create RouteOptions and build remote rule sets if route is null and rules exist`() {
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                buildRule<DNSRule_Default>(listOf("geoip-cn", "geosite-youtube")).asMap(),
                DNSRule_Logical().apply {
                    rules = mutableListOf(buildRule<DNSRule_Default>(listOf("geosite-google")))
                }.asMap(),
            )
        }
        options.route = null

        val ipURL = "http://ip.remote.com"
        val domainURL = "http://domain.remote.com"

        options.buildRuleSets(
            ipURL = ipURL,
            domainURL = domainURL,
            localPath = null
        )

        val expectedTags = setOf("geoip-cn", "geosite-google", "geosite-youtube")
        val ruleSets = options.requireRuleSets()
        ruleSets.assertTags(expectedTags)

        val geoipCnRule = ruleSets.requireRemote("geoip-cn")
        assertEquals(RULE_SET_TYPE_REMOTE, geoipCnRule.type)
        assertEquals("$ipURL/geoip-cn.srs", geoipCnRule.url)

        val geositeGoogleRule = ruleSets.requireRemote("geosite-google")
        assertEquals(RULE_SET_TYPE_REMOTE, geositeGoogleRule.type)
        assertEquals("$domainURL/geosite-google.srs", geositeGoogleRule.url)

        val geositeYoutubeRule = ruleSets.requireRemote("geosite-youtube")
        assertEquals(RULE_SET_TYPE_REMOTE, geositeYoutubeRule.type)
        assertEquals("$domainURL/geosite-youtube.srs", geositeYoutubeRule.url)
    }

    @Test
    fun `buildRuleSets should create RouteOptions and build local rule sets if route is null and rules exist`() {
        options.route = null
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                buildRule<DNSRule_Default>(listOf("geoip-us", "geosite-facebook")).asMap(),
            )
        }
        val localPath = "/data/local_rules"

        options.buildRuleSets(
            ipURL = null,
            domainURL = null,
            localPath = localPath
        )

        val expectedTags = setOf("geosite-facebook", "geoip-us")
        val ruleSets = options.requireRuleSets()
        ruleSets.assertTags(expectedTags)

        val geositeFacebookRule = ruleSets.requireLocal("geosite-facebook")
        assertEquals(RULE_SET_TYPE_LOCAL, geositeFacebookRule.type)
        assertEquals("$localPath/geosite-facebook.srs", geositeFacebookRule.path)

        val geoipUsRule = ruleSets.requireLocal("geoip-us")
        assertEquals(RULE_SET_TYPE_LOCAL, geoipUsRule.type)
        assertEquals("$localPath/geoip-us.srs", geoipUsRule.path)
    }

    @Test
    fun `buildRuleSets should combine existing and new rule sets and refresh route rule_set (remote)`() {
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                buildRule<DNSRule_Default>(listOf("geoip-jp", "twitter")).asMap(),
            )
        }
        options.route = MyRouteOptions().apply {
            rule_set = mutableListOf(
                RuleSet_Remote().apply { tag = "existing-rule"; type = RULE_SET_TYPE_REMOTE },
                RuleSet_Remote().apply { tag = "geoip-kr"; type = RULE_SET_TYPE_REMOTE }
            )
            rules = mutableListOf()
        }

        val ipURL = "http://ip.remote.com"
        val domainURL = "http://domain.remote.com"

        options.buildRuleSets(
            ipURL = ipURL,
            domainURL = domainURL,
            localPath = null
        )

        val expectedTags = setOf("existing-rule", "geoip-kr", "geoip-jp", "twitter")
        val ruleSets = options.requireRuleSets()
        ruleSets.assertTags(expectedTags)

        expectedTags.forEach { tag ->
            val remoteRuleSet = ruleSets.requireRemote(tag)
            assertEquals(RULE_SET_TYPE_REMOTE, remoteRuleSet.type)
            if (tag.startsWith("geoip-")) {
                assertEquals("$ipURL/$tag.srs", remoteRuleSet.url)
            } else {
                assertEquals("$domainURL/$tag.srs", remoteRuleSet.url)
            }
        }
    }

    @Test
    fun `buildRuleSets should collect rules from both dns and route options`() {
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                buildRule<DNSRule_Default>(listOf("dns-set-1", "geoip-dns-set-2")).asMap(),
                DNSRule_Logical().apply {
                    rules = mutableListOf(
                        buildRule<DNSRule_Default>(listOf("dns-set-3"))
                    )
                }.asMap(),
            )
        }
        options.route = MyRouteOptions().apply {
            rules = mutableListOf(
                buildRule<Rule_Default>(listOf("route-set-A", "geoip-route-set-B")).asMap(),
                Rule_Logical().apply {
                    rules = mutableListOf(
                        buildRule<Rule_Default>(listOf("route-set-C"))
                    )
                }.asMap(),
            )
        }

        val ipURL = "http://ip.test.com"
        val domainURL = "http://domain.test.com"

        options.buildRuleSets(ipURL, domainURL, null)

        val expectedTags = setOf(
            "dns-set-1", "dns-set-3", "geoip-dns-set-2",
            "geoip-route-set-B", "route-set-A", "route-set-C"
        )
        val ruleSets = options.requireRuleSets()
        ruleSets.assertTags(expectedTags)

        val dnsSet1 = ruleSets.requireRemote("dns-set-1")
        assertEquals("$domainURL/dns-set-1.srs", dnsSet1.url)

        val geoipDnsSet2 = ruleSets.requireRemote("geoip-dns-set-2")
        assertEquals("$ipURL/geoip-dns-set-2.srs", geoipDnsSet2.url)

        val routeSetC = ruleSets.requireRemote("route-set-C")
        assertEquals("$domainURL/route-set-C.srs", routeSetC.url)
    }

    @Test
    fun `buildRuleSets should handle duplicate rule sets correctly`() {
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                buildRule<DNSRule_Default>(listOf("common-set", "geoip-common-set")).asMap(),
                DNSRule_Logical().apply {
                    rules = mutableListOf(
                        buildRule<DNSRule_Default>(listOf("common-set"))
                    )
                }.asMap(),
            )
        }
        options.route = MyRouteOptions().apply {
            rules = mutableListOf(
                buildRule<Rule_Default>(
                    listOf(
                        "common-set",
                        "another-set"
                    )
                ).asMap(),
                Rule_Logical().apply {
                    rules = mutableListOf(
                        buildRule<Rule_Default>(listOf("geoip-common-set"))
                    )
                }.asMap(),
            )
        }

        options.buildRuleSets("ip", "domain", null)

        val expectedTags = setOf("another-set", "common-set", "geoip-common-set")
        options.requireRuleSets().assertTags(expectedTags)
    }

    @Test
    fun `buildRuleSets should handle null rule_set in default rules gracefully`() {
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                DNSRule_Default().asMap(),
                buildRule<DNSRule_Default>(listOf("good-set")).asMap(),
            )
        }
        options.route = null

        options.buildRuleSets("ip", "domain", null)

        val expectedTags = setOf("good-set")
        options.requireRuleSets().assertTags(expectedTags)
    }

    @Test
    fun `buildRuleSets should handle empty rule_set in default rules gracefully`() {
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                buildRule<DNSRule_Default>(emptyList()).asMap(),
                buildRule<DNSRule_Default>(listOf("another-good-set")).asMap(),
            )
        }
        options.route = null

        options.buildRuleSets("ip", "domain", null)

        val expectedTags = setOf("another-good-set")
        options.requireRuleSets().assertTags(expectedTags)
    }

    @Test
    fun `buildRuleSets should handle null rules list in logical rules gracefully`() {
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                DNSRule_Logical().asMap(),
                DNSRule_Logical().apply {
                    rules = mutableListOf(
                        buildRule<DNSRule_Default>(listOf("nested-set"))
                    )
                }.asMap(),
            )
        }
        options.route = null

        options.buildRuleSets("ip", "domain", null)

        val expectedTags = setOf("nested-set")
        options.requireRuleSets().assertTags(expectedTags)
    }

    @Test
    fun `buildRuleSets should handle empty rules list in logical rules gracefully`() {
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                DNSRule_Logical().asMap(),
                DNSRule_Logical().apply {
                    rules = mutableListOf(
                        buildRule<DNSRule_Default>(listOf("another-nested-set"))
                    )
                }.asMap(),
            )
        }
        options.route = null

        options.buildRuleSets("ip", "domain", null)

        val expectedTags = setOf("another-nested-set")
        options.requireRuleSets().assertTags(expectedTags)
    }

    @Test
    fun `buildRuleSets should collect tags from existing route rule_set and combine with new ones`() {
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                buildRule<DNSRule_Default>(listOf("new-set")).asMap(),
            )
        }
        options.route = MyRouteOptions().apply {
            rule_set = mutableListOf(
                RuleSet_Local().apply { tag = "existing-local"; type = RULE_SET_TYPE_LOCAL },
                RuleSet_Remote().apply { tag = "existing-remote"; type = RULE_SET_TYPE_REMOTE }
            )
            rules = mutableListOf()
        }

        val ipURL = "http://ip.com"
        val domainURL = "http://domain.com"
        val localPath = "/local"

        options.buildRuleSets(ipURL, domainURL, localPath)

        val expectedTags = setOf("new-set", "existing-local", "existing-remote")
        val ruleSets = options.requireRuleSets()
        ruleSets.assertTags(expectedTags)

        val existingLocalRule = ruleSets.requireRemote("existing-local")
        assertEquals(RULE_SET_TYPE_REMOTE, existingLocalRule.type)
        assertEquals("$domainURL/existing-local.srs", existingLocalRule.url)

        val existingRemoteRule = ruleSets.requireRemote("existing-remote")
        assertEquals(RULE_SET_TYPE_REMOTE, existingRemoteRule.type)
        assertEquals("$domainURL/existing-remote.srs", existingRemoteRule.url)

        val newSetRule = ruleSets.requireRemote("new-set")
        assertEquals(RULE_SET_TYPE_REMOTE, newSetRule.type)
        assertEquals("$domainURL/new-set.srs", newSetRule.url)
    }

    @Test
    fun `buildRuleSets should collect rules from route rules if dns rules are null or empty`() {
        options.dns = null
        options.route = MyRouteOptions().apply {
            rules = mutableListOf(
                buildRule<Rule_Default>(listOf("route-only-set-1", "geoip-route-only-set-2")).asMap(),
            )
        }

        val ipURL = "http://ip.only.com"
        val domainURL = "http://domain.only.com"

        options.buildRuleSets(ipURL, domainURL, null)

        val expectedTags = setOf("geoip-route-only-set-2", "route-only-set-1")
        val ruleSets = options.requireRuleSets()
        ruleSets.assertTags(expectedTags)

        val geoipRule = ruleSets.requireRemote("geoip-route-only-set-2")
        assertEquals("$ipURL/geoip-route-only-set-2.srs", geoipRule.url)

        val domainRule = ruleSets.requireRemote("route-only-set-1")
        assertEquals("$domainURL/route-only-set-1.srs", domainRule.url)
    }
}
