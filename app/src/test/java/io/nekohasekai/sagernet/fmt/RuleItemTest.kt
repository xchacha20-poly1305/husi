package io.nekohasekai.sagernet.fmt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuleItemTest {

    @Test
    fun testParseRuleWithPlusDns() {
        // Test with +dns suffix
        val rule1 = RuleItem.parseRule("full+dns:example.com", false)
        assertEquals("full", rule1.type, "Type should be 'full' after removing +dns")
        assertEquals("example.com", rule1.content, "Content should be 'example.com'")
        assertEquals(true, rule1.dns, "DNS should be true with +dns suffix")

        val rule2 = RuleItem.parseRule("set+dns:ip-list", false)
        assertEquals("set", rule2.type)
        assertEquals("ip-list", rule2.content)
        assertEquals(true, rule2.dns)
    }

    @Test
    fun testParseRuleWithMinusDns() {
        // Test with -dns suffix
        val rule1 = RuleItem.parseRule("full-dns:example.com", true)
        assertEquals("full", rule1.type, "Type should be 'full' after removing +dns")
        assertEquals("example.com", rule1.content, "Content should be 'example.com'")
        assertEquals(false, rule1.dns, "DNS should be true with +dns suffix")

        val rule2 = RuleItem.parseRule("set-dns:geoip-us", true)
        assertEquals("set", rule2.type)
        assertEquals("geoip-us", rule2.content)
        assertEquals(false, rule2.dns)
    }

    @Test
    fun testParseRuleWithValidTypeNoSuffix() {
        // Test valid type with defaultDNSBehavior = true
        val rule1 = RuleItem.parseRule("set:country-us", true)
        assertEquals("set", rule1.type, "Type should be 'set'")
        assertEquals("country-us", rule1.content, "Content should be 'country-us'")
        assertEquals(true, rule1.dns, "DNS should match defaultDNSBehavior (true)")

        // Test valid type with defaultDNSBehavior = false
        // In real situation, domain should always use apply defaultDNSBehavior=true.
        val rule2 = RuleItem.parseRule("full:www.example.org", false)
        assertEquals("full", rule2.type)
        assertEquals("www.example.org", rule2.content)
        assertEquals(false, rule2.dns, "DNS should match defaultDNSBehavior (false)")
    }

    @Test
    fun testParseRuleWithInvalidType() {
        // Test invalid type
        val rule1 = RuleItem.parseRule("unknown:test-rule", false)
        assertEquals("", rule1.type, "Type should be empty for invalid type")
        assertEquals("unknown:test-rule", rule1.content, "Content should be entire string")
        assertEquals(false, rule1.dns, "DNS should be false for invalid type")

        val rule2 = RuleItem.parseRule("xyz:abc", false)
        assertEquals("", rule2.type)
        assertEquals("xyz:abc", rule2.content)
        assertEquals(false, rule2.dns)
    }

    @Test
    fun testParseRuleWithNoColon() {
        // Test string without colon, including IPv6
        val rule1 = RuleItem.parseRule("[2001:db8::68]", false)
        assertEquals("", rule1.type, "Type should be empty with no colon")
        assertEquals("[2001:db8::68]", rule1.content, "Content should be entire IPv6 string")
        assertEquals(false, rule1.dns, "DNS should be false with no type")

        // Test string without colon, including IPv4
        val rule2 = RuleItem.parseRule("10.0.0.1", false)
        assertEquals("", rule2.type)
        assertEquals("10.0.0.1", rule2.content)
        assertEquals(false, rule2.dns)
    }

    @Test
    fun testParseRuleWithCustomType() {
        // Custom type should be invalid: entire raw string is content and dns=false
        val rule1 = RuleItem.parseRule("custom+dns:data123", false)
        assertEquals("", rule1.type, "Type should be empty for custom type")
        assertEquals("custom+dns:data123", rule1.content, "Content should be entire string")
        assertEquals(false, rule1.dns, "DNS should be false for invalid type")

        val rule2 = RuleItem.parseRule("custom+dns:filter", false)
        assertEquals("", rule2.type, "Type should be empty for custom type")
        assertEquals("custom+dns:filter", rule2.content, "Content should be entire string")
        assertEquals(false, rule2.dns, "DNS should be false for invalid type")
    }

    @Test
    fun testParseRuleWithIPv4() {
        // Test parsing with IPv4 address
        val rule = RuleItem.parseRule("full:172.16.254.1", false)
        assertEquals("full", rule.type, "Type should be 'full'")
        assertEquals("172.16.254.1", rule.content, "Content should be IPv4 address")
        assertEquals(false, rule.dns, "DNS should match defaultDNSBehavior (false)")
    }

    @Test
    fun testParseRuleWithIPv6() {
        // Test parsing with IPv6 address
        val rule = RuleItem.parseRule("domain:[2001:0db8:85a3::8a2e:0370:7334]", true)
        assertEquals("domain", rule.type, "Type should be 'domain'")
        assertEquals(
            "[2001:0db8:85a3::8a2e:0370:7334]",
            rule.content,
            "Content should be IPv6 address"
        )
        assertEquals(true, rule.dns, "DNS should match defaultDNSBehavior (true)")
    }

    @Test
    fun testParseRuleWithMultipleColons() {
        // Test string with multiple colons
        val rule = RuleItem.parseRule("set:us-east:server1", true)
        assertEquals("set", rule.type, "Type should be 'set'")
        assertEquals(
            "us-east:server1",
            rule.content,
            "Content includes remaining parts after first colon"
        )
        assertEquals(true, rule.dns, "DNS should match defaultDNSBehavior (true)")
    }

    @Test
    fun testParseRuleEdgeCases() {
        // Test empty string
        val rule1 = RuleItem.parseRule("", false)
        assertEquals("", rule1.type)
        assertEquals("", rule1.content)
        assertEquals(false, rule1.dns)

        // Test only colon
        val rule2 = RuleItem.parseRule(":", true)
        assertEquals("", rule2.type)
        assertEquals("", rule2.content)
        assertEquals(true, rule2.dns)

        // Test type with empty content
        val rule3 = RuleItem.parseRule("set:", false)
        assertEquals("set", rule3.type)
        assertEquals("", rule3.content)
        assertEquals(false, rule3.dns)

        // Test empty type with content
        val rule4 = RuleItem.parseRule(":data", true)
        assertEquals("", rule4.type)
        assertEquals("data", rule4.content)
        assertEquals(true, rule4.dns)
    }

    @Test
    fun testParseDomainRules() {
        // Test parsing a list of rules
        val list = listOf(
            "set+dns:region-asia",
            "regexp+dns:.*\\.net",
            "domain:test.org",
            "full-dns:api.github.com",
        )
        val rules = RuleItem.parseRules(list, true)
        assertEquals(list.size, rules.size, "Should parse all ${list.size} rules")

        assertEquals("set", rules[0].type)
        assertEquals("region-asia", rules[0].content)
        assertEquals(true, rules[0].dns)

        assertEquals("regexp", rules[1].type)
        assertEquals(".*\\.net", rules[1].content)
        assertEquals(true, rules[1].dns)

        assertEquals("domain", rules[2].type)
        assertEquals("test.org", rules[2].content)
        assertEquals(true, rules[2].dns)

        assertEquals("full", rules[3].type)
        assertEquals("api.github.com", rules[3].content)
        assertEquals(false, rules[3].dns, "DNS should be false for full rule without +dns suffix")
    }

    @Test
    fun testParseIPRules() {
        // Test parsing IP rules
        val ipRules = listOf(
            "+dns:8.8.8.8",
            "1.1.1.1",
            "set+dns:geoip-cn",
            "-dns:[2001:db8::1]",
        )
        val rules = RuleItem.parseRules(ipRules, false)
        assertEquals(ipRules.size, rules.size, "Should parse all ${ipRules.size} IP rules")
        assertEquals("", rules[0].type, "Type should be empty for +dns rule")

        assertEquals("", rules[0].type)
        assertEquals("8.8.8.8", rules[0].content)
        assertEquals(true, rules[0].dns)

        assertEquals("", rules[1].type)
        assertEquals("1.1.1.1", rules[1].content)
        assertEquals(false, rules[1].dns)

        assertEquals("set", rules[2].type)
        assertEquals("geoip-cn", rules[2].content)
        assertEquals(true, rules[2].dns)

        assertEquals("", rules[3].type)
        assertEquals("[2001:db8::1]", rules[3].content)
        assertEquals(false, rules[3].dns)
    }
}