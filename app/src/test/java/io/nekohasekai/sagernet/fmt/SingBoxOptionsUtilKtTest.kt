package io.nekohasekai.sagernet.fmt

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.nekohasekai.sagernet.fmt.SingBoxOptions.MyOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.DNSOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RouteOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.DNSRule_Logical
import io.nekohasekai.sagernet.fmt.SingBoxOptions.DNSRule_Default
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RULE_SET_TYPE_REMOTE
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RULE_SET_TYPE_LOCAL
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RuleSet_Remote
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RuleSet_Local
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Rule_Default
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Rule_Logical

class SingBoxOptionsUtilKtTest {

    private lateinit var myOptions: MyOptions

    private inline fun <reified T> buildRule(ruleSets: List<String>): T = when (T::class.java) {
        DNSRule_Default::class.java -> DNSRule_Default().apply {
            rule_set = ruleSets
        }

        Rule_Default::class.java -> Rule_Default().apply {
            rule_set = ruleSets
        }

        else -> throw IllegalArgumentException("Unsupported rule type")
    } as T

    @BeforeEach
    fun setUp() {
        myOptions = MyOptions()
    }

    // Test Case 1: No rules in DNS or Route, and `route` is initially null.
    @Test
    fun `buildRuleSets should do nothing if no rules are found and route is null`() {
        myOptions.dns = DNSOptions().apply { rules = emptyList() }
        myOptions.route = null

        myOptions.buildRuleSets(
            ipURL = "http://ip.example.com",
            domainURL = "http://domain.example.com",
            localPath = "/data/local"
        )

        assertNull(myOptions.route) // `route` should remain null
    }

    // Test Case 2: No rules, but `route` is not null initially (e.g., has an empty rule_set).
    @Test
    fun `buildRuleSets should keep route rule_set empty if no rules are found and route already exists`() {
        myOptions.dns = DNSOptions().apply { rules = emptyList() }
        myOptions.route = RouteOptions().apply { rule_set = emptyList() }

        myOptions.buildRuleSets(
            ipURL = "http://ip.example.com",
            domainURL = "http://domain.example.com",
            localPath = "/data/local"
        )

        assertNotNull(myOptions.route)
        assertTrue(myOptions.route!!.rule_set.isEmpty())
    }

    // Test Case 3: `route` is null initially, and rules are present (remote configuration).
    @Test
    fun `buildRuleSets should create RouteOptions and build remote rule sets if route is null and rules exist`() {
        myOptions.dns = DNSOptions().apply {
            rules = listOf(
                buildRule<DNSRule_Default>(listOf("geoip-cn", "geosite-youtube")),
                DNSRule_Logical().apply {
                    rules = listOf(buildRule<DNSRule_Default>(listOf("geosite-google")))
                }
            )
        }
        myOptions.route = null // Route is initially null

        val ipURL = "http://ip.remote.com"
        val domainURL = "http://domain.remote.com"

        myOptions.buildRuleSets(
            ipURL = ipURL,
            domainURL = domainURL,
            localPath = null // Not used for remote
        )

        assertNotNull(myOptions.route)
        val ruleSets = myOptions.route!!.rule_set
        val actualTags = ruleSets.map { it.tag }.toSet()

        val expectedTags = setOf("geoip-cn", "geosite-google", "geosite-youtube")
        assertEquals(expectedTags.size, ruleSets.size)
        assertEquals(expectedTags, actualTags)

        // Assert types and URLs for remote rule sets by tag
        val geoipCnRule = ruleSets.first { it.tag == "geoip-cn" }
        assertTrue(geoipCnRule is RuleSet_Remote)
        assertEquals(RULE_SET_TYPE_REMOTE, geoipCnRule.type)
        assertEquals("$ipURL/geoip-cn.srs", (geoipCnRule as RuleSet_Remote).url)

        val geositeGoogleRule = ruleSets.first { it.tag == "geosite-google" }
        assertTrue(geositeGoogleRule is RuleSet_Remote)
        assertEquals(RULE_SET_TYPE_REMOTE, geositeGoogleRule.type)
        assertEquals("$domainURL/geosite-google.srs", (geositeGoogleRule as RuleSet_Remote).url)

        val geositeYoutubeRule = ruleSets.first { it.tag == "geosite-youtube" }
        assertTrue(geositeYoutubeRule is RuleSet_Remote)
        assertEquals(RULE_SET_TYPE_REMOTE, geositeYoutubeRule.type)
        assertEquals("$domainURL/geosite-youtube.srs", (geositeYoutubeRule as RuleSet_Remote).url)
    }

    // Test Case 4: `route` is null initially, and rules are present (local configuration).
    @Test
    fun `buildRuleSets should create RouteOptions and build local rule sets if route is null and rules exist`() {
        myOptions.route = null
        myOptions.dns = DNSOptions().apply {
            rules = listOf(
                buildRule<DNSRule_Default>(listOf("geoip-us", "geosite-facebook"))
            )
        }
        val localPath = "/data/local_rules"

        myOptions.buildRuleSets(
            ipURL = null,    // Signifies local configuration
            domainURL = null, // Signifies local configuration
            localPath = localPath
        )

        assertNotNull(myOptions.route)
        val ruleSets = myOptions.route!!.rule_set
        val actualTags = ruleSets.map { it.tag }.toSet()

        val expectedTags = setOf("geosite-facebook", "geoip-us")
        assertEquals(expectedTags.size, ruleSets.size)
        assertEquals(expectedTags, actualTags)

        val geositeFacebookRule = ruleSets.first { it.tag == "geosite-facebook" }
        assertTrue(geositeFacebookRule is RuleSet_Local)
        assertEquals(RULE_SET_TYPE_LOCAL, geositeFacebookRule.type)
        assertEquals("$localPath/geosite-facebook.srs", (geositeFacebookRule as RuleSet_Local).path)

        val geoipUsRule = ruleSets.first { it.tag == "geoip-us" }
        assertTrue(geoipUsRule is RuleSet_Local)
        assertEquals(RULE_SET_TYPE_LOCAL, geoipUsRule.type)
        assertEquals("$localPath/geoip-us.srs", (geoipUsRule as RuleSet_Local).path)
    }

    // Test Case 5: `route` is not null, has existing `rule_set`, and new rules are found (remote).
    @Test
    fun `buildRuleSets should combine existing and new rule sets and refresh route rule_set (remote)`() {
        myOptions.dns = DNSOptions().apply {
            rules = listOf(
                buildRule<DNSRule_Default>(listOf("geoip-jp", "twitter"))
            )
        }
        myOptions.route = RouteOptions().apply {
            // Existing rule_set, their tags should be preserved and re-added
            rule_set = listOf(
                RuleSet_Remote().apply { tag = "existing-rule"; type = RULE_SET_TYPE_REMOTE },
                RuleSet_Remote().apply { tag = "geoip-kr"; type = RULE_SET_TYPE_REMOTE }
            )
            rules = emptyList() // For this test, route.rules is empty.
        }

        val ipURL = "http://ip.remote.com"
        val domainURL = "http://domain.remote.com"

        myOptions.buildRuleSets(
            ipURL = ipURL,
            domainURL = domainURL,
            localPath = null
        )

        assertNotNull(myOptions.route)
        val ruleSets = myOptions.route!!.rule_set
        val actualTags = ruleSets.map { it.tag }.toSet()

        val expectedTags = setOf("existing-rule", "geoip-kr", "geoip-jp", "twitter")
        assertEquals(expectedTags.size, ruleSets.size) // existing-rule, geoip-kr, geoip-jp, twitter
        assertEquals(expectedTags, actualTags)

        // Check types and URLs (all should be remote)
        expectedTags.forEach { tag ->
            val ruleSet = ruleSets.first { it.tag == tag }
            assertTrue(ruleSet is RuleSet_Remote)
            assertEquals(RULE_SET_TYPE_REMOTE, ruleSet.type)
            val remoteRuleSet = ruleSet as RuleSet_Remote
            if (tag.startsWith("geoip-")) {
                assertEquals("$ipURL/$tag.srs", remoteRuleSet.url)
            } else {
                assertEquals("$domainURL/$tag.srs", remoteRuleSet.url)
            }
        }
    }

    // Test Case 6: `rules` from both `dns` and `route` contain rule sets.
    @Test
    fun `buildRuleSets should collect rules from both dns and route options`() {
        myOptions.dns = DNSOptions().apply {
            rules = listOf(
                buildRule<DNSRule_Default>(listOf("dns-set-1", "geoip-dns-set-2")),
                DNSRule_Logical().apply {
                    rules = listOf(
                        buildRule<DNSRule_Default>(listOf("dns-set-3"))
                    )
                }
            )
        }
        myOptions.route = RouteOptions().apply {
            rules = listOf(
                buildRule<Rule_Default>(listOf("route-set-A", "geoip-route-set-B")),
                Rule_Logical().apply {
                    rules = listOf(
                        buildRule<Rule_Default>(listOf("route-set-C"))
                    )
                }
            )
        }

        val ipURL = "http://ip.test.com"
        val domainURL = "http://domain.test.com"

        myOptions.buildRuleSets(ipURL, domainURL, null)

        assertNotNull(myOptions.route)
        val ruleSets = myOptions.route!!.rule_set
        val actualTags = ruleSets.map { it.tag }.toSet()

        val expectedTags = setOf(
            "dns-set-1", "dns-set-3", "geoip-dns-set-2",
            "geoip-route-set-B", "route-set-A", "route-set-C"
        )
        assertEquals(expectedTags.size, ruleSets.size) // All unique sets
        assertEquals(expectedTags, actualTags)

        // Verify types and URLs for a few selected rule sets by tag
        val dnsSet1 = ruleSets.first { it.tag == "dns-set-1" }
        assertTrue(dnsSet1 is RuleSet_Remote)
        assertEquals("$domainURL/dns-set-1.srs", (dnsSet1 as RuleSet_Remote).url)

        val geoipDnsSet2 = ruleSets.first { it.tag == "geoip-dns-set-2" }
        assertTrue(geoipDnsSet2 is RuleSet_Remote)
        assertEquals("$ipURL/geoip-dns-set-2.srs", (geoipDnsSet2 as RuleSet_Remote).url)

        val routeSetC = ruleSets.first { it.tag == "route-set-C" }
        assertTrue(routeSetC is RuleSet_Remote)
        assertEquals("$domainURL/route-set-C.srs", (routeSetC as RuleSet_Remote).url)
    }

    // Test Case 7: Duplicate rule sets should only be added once.
    @Test
    fun `buildRuleSets should handle duplicate rule sets correctly`() {
        myOptions.dns = DNSOptions().apply {
            rules = listOf(
                buildRule<DNSRule_Default>(listOf("common-set", "geoip-common-set")),
                DNSRule_Logical().apply {
                    rules = listOf(
                        buildRule<DNSRule_Default>(listOf("common-set"))
                    )
                }
            )
        }
        myOptions.route = RouteOptions().apply {
            rules = listOf(
                buildRule<Rule_Default>(
                    listOf(
                        "common-set",
                        "another-set"
                    )
                ), // Duplicate 'common-set'
                Rule_Logical().apply {
                    rules = listOf(
                        buildRule<Rule_Default>(listOf("geoip-common-set")) // Duplicate
                    )
                }
            )
        }

        myOptions.buildRuleSets("ip", "domain", null)

        assertNotNull(myOptions.route)
        val ruleSets = myOptions.route!!.rule_set
        val actualTags = ruleSets.map { it.tag }.toSet()

        val expectedTags = setOf("another-set", "common-set", "geoip-common-set")
        assertEquals(
            expectedTags.size,
            ruleSets.size
        ) // Should only contain unique: common-set, geoip-common-set, another-set
        assertEquals(expectedTags, actualTags)
    }

    // Test Case 8: `rule_set` property being null in default rules.
    @Test
    fun `buildRuleSets should handle null rule_set in default rules gracefully`() {
        myOptions.dns = DNSOptions().apply {
            rules = listOf(
                DNSRule_Default(), // Null rule_set
                buildRule<DNSRule_Default>(listOf("good-set"))
            )
        }
        myOptions.route = null

        myOptions.buildRuleSets("ip", "domain", null)

        assertNotNull(myOptions.route)
        val ruleSets = myOptions.route!!.rule_set
        val actualTags = ruleSets.map { it.tag }.toSet()
        val expectedTags = setOf("good-set")
        assertEquals(expectedTags.size, ruleSets.size)
        assertEquals(expectedTags, actualTags)
    }

    // Test Case 9: Empty `rule_set` property in default rules.
    @Test
    fun `buildRuleSets should handle empty rule_set in default rules gracefully`() {
        myOptions.dns = DNSOptions().apply {
            rules = listOf(
                buildRule<DNSRule_Default>(emptyList()), // Empty rule_set
                buildRule<DNSRule_Default>(listOf("another-good-set"))
            )
        }
        myOptions.route = null

        myOptions.buildRuleSets("ip", "domain", null)

        assertNotNull(myOptions.route)
        val ruleSets = myOptions.route!!.rule_set
        val actualTags = ruleSets.map { it.tag }.toSet()
        val expectedTags = setOf("another-good-set")
        assertEquals(expectedTags.size, ruleSets.size)
        assertEquals(expectedTags, actualTags)
    }

    // Test Case 10: `rules` list being null in logical rules.
    @Test
    fun `buildRuleSets should handle null rules list in logical rules gracefully`() {
        myOptions.dns = DNSOptions().apply {
            rules = listOf(
                DNSRule_Logical(), // Null rules list
                DNSRule_Logical().apply {
                    rules = listOf(
                        buildRule<DNSRule_Default>(listOf("nested-set"))
                    )
                }
            )
        }
        myOptions.route = null

        myOptions.buildRuleSets("ip", "domain", null)

        assertNotNull(myOptions.route)
        val ruleSets = myOptions.route!!.rule_set
        val actualTags = ruleSets.map { it.tag }.toSet()
        val expectedTags = setOf("nested-set")
        assertEquals(expectedTags.size, ruleSets.size)
        assertEquals(expectedTags, actualTags)
    }

    // Test Case 11: `rules` list being empty in logical rules.
    @Test
    fun `buildRuleSets should handle empty rules list in logical rules gracefully`() {
        myOptions.dns = DNSOptions().apply {
            rules = listOf(
                DNSRule_Logical(), // Empty rules list
                DNSRule_Logical().apply {
                    rules = listOf(
                        buildRule<DNSRule_Default>(listOf("another-nested-set"))
                    )
                }
            )
        }
        myOptions.route = null

        myOptions.buildRuleSets("ip", "domain", null)

        assertNotNull(myOptions.route)
        val ruleSets = myOptions.route!!.rule_set
        val actualTags = ruleSets.map { it.tag }.toSet()
        val expectedTags = setOf("another-nested-set")
        assertEquals(expectedTags.size, ruleSets.size)
        assertEquals(expectedTags, actualTags)
    }

    // Test Case 12: Ensure rule_set tags are collected from an existing `route.rule_set` before building new ones.
    @Test
    fun `buildRuleSets should collect tags from existing route rule_set and combine with new ones`() {
        myOptions.dns = DNSOptions().apply {
            rules = listOf(
                buildRule<DNSRule_Default>(listOf("new-set"))
            )
        }
        myOptions.route = RouteOptions().apply {
            rule_set = listOf(
                RuleSet_Local().apply { tag = "existing-local"; type = RULE_SET_TYPE_LOCAL },
                RuleSet_Remote().apply { tag = "existing-remote"; type = RULE_SET_TYPE_REMOTE }
            )
            rules = emptyList() // No rules within route.rules for this test
        }

        val ipURL = "http://ip.com"
        val domainURL = "http://domain.com"
        val localPath = "/local" // Note: localPath is used by buildRuleSets but the specific test context uses remote URLs

        myOptions.buildRuleSets(ipURL, domainURL, localPath)

        assertNotNull(myOptions.route)
        val ruleSets = myOptions.route!!.rule_set
        val actualTags = ruleSets.map { it.tag }.toSet()

        val expectedTags = setOf("new-set", "existing-local", "existing-remote")
        assertEquals(expectedTags.size, ruleSets.size) // "new-set", "existing-local", "existing-remote"
        assertEquals(expectedTags, actualTags)

        // Verify the type and URL/path for the collected ones (re-created based on current configuration)
        val existingLocalRule = ruleSets.first { it.tag == "existing-local" }
        assertTrue(existingLocalRule is RuleSet_Remote) // existing-local is now remote
        assertEquals(RULE_SET_TYPE_REMOTE, existingLocalRule.type)
        assertEquals("$domainURL/existing-local.srs", (existingLocalRule as RuleSet_Remote).url)

        val existingRemoteRule = ruleSets.first { it.tag == "existing-remote" }
        assertTrue(existingRemoteRule is RuleSet_Remote) // existing-remote remains remote
        assertEquals(RULE_SET_TYPE_REMOTE, existingRemoteRule.type)
        assertEquals("$domainURL/existing-remote.srs", (existingRemoteRule as RuleSet_Remote).url)

        val newSetRule = ruleSets.first { it.tag == "new-set" }
        assertTrue(newSetRule is RuleSet_Remote) // new-set
        assertEquals(RULE_SET_TYPE_REMOTE, newSetRule.type)
        assertEquals("$domainURL/new-set.srs", (newSetRule as RuleSet_Remote).url)
    }

    // Test Case 13: `buildRuleSets` with only `route.rules` populated and `dns.rules` is null or empty.
    @Test
    fun `buildRuleSets should collect rules from route rules if dns rules are null or empty`() {
        myOptions.dns = null // DNS rules are null
        myOptions.route = RouteOptions().apply {
            rules = listOf(
                buildRule<Rule_Default>(listOf("route-only-set-1", "geoip-route-only-set-2"))
            )
        }

        val ipURL = "http://ip.only.com"
        val domainURL = "http://domain.only.com"

        myOptions.buildRuleSets(ipURL, domainURL, null)

        assertNotNull(myOptions.route)
        val ruleSets = myOptions.route!!.rule_set
        val actualTags = ruleSets.map { it.tag }.toSet()

        val expectedTags = setOf("geoip-route-only-set-2", "route-only-set-1")
        assertEquals(expectedTags.size, ruleSets.size)
        assertEquals(expectedTags, actualTags)

        val geoipRule = ruleSets.first { it.tag == "geoip-route-only-set-2" }
        assertTrue(geoipRule is RuleSet_Remote)
        assertEquals("$ipURL/geoip-route-only-set-2.srs", (geoipRule as RuleSet_Remote).url)

        val domainRule = ruleSets.first { it.tag == "route-only-set-1" }
        assertTrue(domainRule is RuleSet_Remote)
        assertEquals("$domainURL/route-only-set-1.srs", (domainRule as RuleSet_Remote).url)
    }
}