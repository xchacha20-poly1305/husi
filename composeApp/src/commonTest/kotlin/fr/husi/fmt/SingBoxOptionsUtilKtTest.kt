package fr.husi.fmt

import fr.husi.fmt.SingBoxOptions.MyOptions
import fr.husi.fmt.SingBoxOptions.MyDNSOptions
import fr.husi.fmt.SingBoxOptions.MyRouteOptions
import fr.husi.fmt.SingBoxOptions.DNSRule_Default
import fr.husi.fmt.SingBoxOptions.RULE_SET_FORMAT_BINARY
import fr.husi.fmt.SingBoxOptions.RULE_SET_TYPE_REMOTE
import fr.husi.fmt.SingBoxOptions.RULE_SET_TYPE_LOCAL
import fr.husi.fmt.SingBoxOptions.RuleSet
import fr.husi.fmt.SingBoxOptions.RuleSet_Remote
import fr.husi.fmt.SingBoxOptions.RuleSet_Local
import fr.husi.fmt.SingBoxOptions.Rule_Default
import fr.husi.ktx.JSONMap
import fr.husi.ktx.asMap
import fr.husi.ktx.toJsonObjectKxs
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

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

    private fun logicalRule(vararg rules: JSONMap): JSONMap = mutableMapOf<String, Any?>(
        "type" to "logical",
        "rules" to rules.toMutableList(),
    )

    private fun MyOptions.requireRuleSets(): List<RuleSet> = requireNotNull(
        requireNotNull(route) { "route should not be null" }.rule_set
    ) { "route.rule_set should not be null" }

    private fun List<RuleSet>.assertTags(expected: Set<String>) {
        assertEquals(expected, flatMap { it.tag.orEmpty() }.toSet())
    }

    private fun List<RuleSet>.requireRemote(vararg tags: String): RuleSet_Remote {
        val expectedTags = tags.toSet()
        val rule = firstOrNull { it.tag.orEmpty().toSet() == expectedTags }
            ?: fail("Rule set $expectedTags not found")
        return rule as? RuleSet_Remote ?: fail("Rule set $expectedTags is not remote")
    }

    private fun List<RuleSet>.requireLocal(vararg tags: String): RuleSet_Local {
        val expectedTags = tags.toSet()
        val rule = firstOrNull { it.tag.orEmpty().toSet() == expectedTags }
            ?: fail("Rule set $expectedTags not found")
        return rule as? RuleSet_Local ?: fail("Rule set $expectedTags is not local")
    }

    @BeforeTest
    fun setUp() {
        options = MyOptions()
    }

    @Test
    fun `DNS rule should serialize named parallel evaluate options`() {
        val evaluateRule = DNSRule_Default().apply {
            action = SingBoxOptions.ACTION_EVALUATE
            server = "dns-remote"
            tag = "remote"
            speculative = true
        }.toJsonObjectKxs()
        val responseRule = DNSRule_Default().apply {
            match_response = JsonPrimitive("remote")
            action = SingBoxOptions.ACTION_ROUTE
            race = true
            server = "dns-direct"
        }.toJsonObjectKxs()

        assertEquals("evaluate", evaluateRule["action"]?.jsonPrimitive?.content)
        assertEquals("dns-remote", evaluateRule["server"]?.jsonPrimitive?.content)
        assertEquals("remote", evaluateRule["tag"]?.jsonPrimitive?.content)
        assertEquals(true, evaluateRule["speculative"]?.jsonPrimitive?.boolean)
        assertEquals("remote", responseRule["match_response"]?.jsonPrimitive?.content)
        assertEquals("route", responseRule["action"]?.jsonPrimitive?.content)
        assertEquals(true, responseRule["race"]?.jsonPrimitive?.boolean)
        assertEquals("dns-direct", responseRule["server"]?.jsonPrimitive?.content)
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

        assertTrue(requireNotNull(options.route).rule_set.isNullOrEmpty())
    }

    @Test
    fun `buildRuleSets should create RouteOptions and build remote rule sets if route is null and rules exist`() {
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                buildRule<DNSRule_Default>(listOf("geoip-cn", "geoip-us", "geosite-youtube")).asMap(),
                logicalRule(buildRule<DNSRule_Default>(listOf("geosite-google")).asMap()),
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

        val expectedTags = setOf("geoip-cn", "geoip-us", "geosite-google", "geosite-youtube")
        val ruleSets = options.requireRuleSets()
        ruleSets.assertTags(expectedTags)

        val geoipRule = ruleSets.requireRemote("geoip-cn", "geoip-us")
        assertEquals(RULE_SET_TYPE_REMOTE, geoipRule.type)
        assertEquals(RULE_SET_FORMAT_BINARY, geoipRule.format)
        assertEquals("$ipURL/{tag}.srs", geoipRule.url)

        val geositeRule = ruleSets.requireRemote("geosite-google", "geosite-youtube")
        assertEquals(RULE_SET_TYPE_REMOTE, geositeRule.type)
        assertEquals(RULE_SET_FORMAT_BINARY, geositeRule.format)
        assertEquals("$domainURL/{tag}.srs", geositeRule.url)
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

        val ruleSet = ruleSets.requireLocal("geosite-facebook", "geoip-us")
        assertEquals(RULE_SET_TYPE_LOCAL, ruleSet.type)
        assertEquals(RULE_SET_FORMAT_BINARY, ruleSet.format)
        assertEquals("$localPath/{tag}.srs", ruleSet.path)
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
                RuleSet_Remote().apply { tag = mutableListOf("existing-rule"); type = RULE_SET_TYPE_REMOTE },
                RuleSet_Remote().apply { tag = mutableListOf("geoip-kr"); type = RULE_SET_TYPE_REMOTE }
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

        val geoipRuleSet = ruleSets.requireRemote("geoip-jp", "geoip-kr")
        assertEquals(RULE_SET_TYPE_REMOTE, geoipRuleSet.type)
        assertEquals(RULE_SET_FORMAT_BINARY, geoipRuleSet.format)
        assertEquals("$ipURL/{tag}.srs", geoipRuleSet.url)

        val domainRuleSet = ruleSets.requireRemote("existing-rule", "twitter")
        assertEquals(RULE_SET_TYPE_REMOTE, domainRuleSet.type)
        assertEquals(RULE_SET_FORMAT_BINARY, domainRuleSet.format)
        assertEquals("$domainURL/{tag}.srs", domainRuleSet.url)
    }

    @Test
    fun `buildRuleSets should collect rules from both dns and route options`() {
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                buildRule<DNSRule_Default>(listOf("dns-set-1", "geoip-dns-set-2")).asMap(),
                logicalRule(buildRule<DNSRule_Default>(listOf("dns-set-3")).asMap()),
            )
        }
        options.route = MyRouteOptions().apply {
            rules = mutableListOf(
                buildRule<Rule_Default>(listOf("route-set-A", "geoip-route-set-B")).asMap(),
                logicalRule(buildRule<Rule_Default>(listOf("route-set-C")).asMap()),
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

        val domainRuleSet = ruleSets.requireRemote("dns-set-1", "dns-set-3", "route-set-A", "route-set-C")
        assertEquals("$domainURL/{tag}.srs", domainRuleSet.url)

        val geoipRuleSet = ruleSets.requireRemote("geoip-dns-set-2", "geoip-route-set-B")
        assertEquals("$ipURL/{tag}.srs", geoipRuleSet.url)
    }

    @Test
    fun `buildRuleSets should handle duplicate rule sets correctly`() {
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                buildRule<DNSRule_Default>(listOf("common-set", "geoip-common-set")).asMap(),
                logicalRule(buildRule<DNSRule_Default>(listOf("common-set")).asMap()),
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
                logicalRule(buildRule<Rule_Default>(listOf("geoip-common-set")).asMap()),
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
                mutableMapOf("type" to "logical"),
                logicalRule(buildRule<DNSRule_Default>(listOf("nested-set")).asMap()),
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
                mutableMapOf("type" to "logical"),
                logicalRule(buildRule<DNSRule_Default>(listOf("another-nested-set")).asMap()),
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
                RuleSet_Local().apply { tag = mutableListOf("existing-local"); type = RULE_SET_TYPE_LOCAL },
                RuleSet_Remote().apply { tag = mutableListOf("existing-remote"); type = RULE_SET_TYPE_REMOTE }
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

        val ruleSet = ruleSets.requireRemote("existing-local", "existing-remote", "new-set")
        assertEquals(RULE_SET_TYPE_REMOTE, ruleSet.type)
        assertEquals(RULE_SET_FORMAT_BINARY, ruleSet.format)
        assertEquals("$domainURL/{tag}.srs", ruleSet.url)
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
        assertEquals(RULE_SET_FORMAT_BINARY, geoipRule.format)
        assertEquals("$ipURL/{tag}.srs", geoipRule.url)

        val domainRule = ruleSets.requireRemote("route-only-set-1")
        assertEquals(RULE_SET_FORMAT_BINARY, domainRule.format)
        assertEquals("$domainURL/{tag}.srs", domainRule.url)
    }

    @Test
    fun `buildRuleSets should append file name to local path as provided`() {
        options.route = null
        options.dns = MyDNSOptions().apply {
            rules = mutableListOf(
                buildRule<DNSRule_Default>(listOf("geosite-facebook", "geosite-google")).asMap(),
            )
        }

        options.buildRuleSets(
            ipURL = null,
            domainURL = null,
            localPath = """C:\Users\demo\.config\husi\external\geo""",
        )

        val ruleSet = options.requireRuleSets().requireLocal("geosite-facebook", "geosite-google")
        assertEquals(RULE_SET_FORMAT_BINARY, ruleSet.format)
        assertEquals("""C:\Users\demo\.config\husi\external\geo/{tag}.srs""", ruleSet.path)
    }
}
