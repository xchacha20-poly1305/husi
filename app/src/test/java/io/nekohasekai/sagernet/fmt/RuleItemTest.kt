package io.nekohasekai.sagernet.fmt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuleItemTest {

    @Test
    fun testParseRuleWithPlusDns() {
        // Test with +dns suffix
        val rule1 = RuleItem.parseRule("full+dns:example.com")
        assertEquals("full", rule1.type, "Type should be 'full' after removing +dns")
        assertEquals("example.com", rule1.content, "Content should be 'example.com'")
        assertEquals(true, rule1.dns, "DNS should be true with +dns suffix")

        val rule2 = RuleItem.parseRule("set+dns:ip-list")
        assertEquals("set", rule2.type)
        assertEquals("ip-list", rule2.content)
        assertEquals(true, rule2.dns)
    }

    @Test
    fun testParseRuleWithValidTypeNoSuffix() {
        // Test valid type with defaultDNSBehavior = true
        val rule1 = RuleItem.parseRule("set:country-us")
        assertEquals("set", rule1.type, "Type should be 'set'")
        assertEquals("country-us", rule1.content, "Content should be 'country-us'")
        assertEquals(false, rule1.dns, "DNS should match defaultDNSBehavior (true)")

        // Test valid type with defaultDNSBehavior = false
        val rule2 = RuleItem.parseRule("full:www.example.org")
        assertEquals("full", rule2.type)
        assertEquals("www.example.org", rule2.content)
        assertEquals(false, rule2.dns, "DNS should match defaultDNSBehavior (false)")
    }

    @Test
    fun testParseRuleWithInvalidType() {
        // Test invalid type
        val rule1 = RuleItem.parseRule("unknown:test-rule")
        assertEquals("", rule1.type, "Type should be empty for invalid type")
        assertEquals("unknown:test-rule", rule1.content, "Content should be entire string")
        assertEquals(false, rule1.dns, "DNS should be false for invalid type")

        val rule2 = RuleItem.parseRule("xyz:abc")
        assertEquals("", rule2.type)
        assertEquals("xyz:abc", rule2.content)
        assertEquals(false, rule2.dns)
    }

    @Test
    fun testParseRuleWithNoColon() {
        // Test string without colon, including IPv6
        val rule1 = RuleItem.parseRule("[2001:db8::68]")
        assertEquals("", rule1.type, "Type should be empty with no colon")
        assertEquals("[2001:db8::68]", rule1.content, "Content should be entire IPv6 string")
        assertEquals(false, rule1.dns, "DNS should be false with no type")

        // Test string without colon, including IPv4
        val rule2 = RuleItem.parseRule("10.0.0.1")
        assertEquals("", rule2.type)
        assertEquals("10.0.0.1", rule2.content)
        assertEquals(false, rule2.dns)
    }

    @Test
    fun testParseRuleWithCustomType() {
        // Custom type should be invalid: entire raw string is content and dns=false
        val rule1 = RuleItem.parseRule("custom+dns:data123")
        assertEquals("", rule1.type, "Type should be empty for custom type")
        assertEquals("custom+dns:data123", rule1.content, "Content should be entire string")
        assertEquals(false, rule1.dns, "DNS should be false for invalid type")

        val rule2 = RuleItem.parseRule("custom+dns:filter")
        assertEquals("", rule2.type, "Type should be empty for custom type")
        assertEquals("custom+dns:filter", rule2.content, "Content should be entire string")
        assertEquals(false, rule2.dns, "DNS should be false for invalid type")
    }

    @Test
    fun testParseRuleWithIPv4() {
        // Test parsing with IPv4 address
        val rule = RuleItem.parseRule("full:172.16.254.1")
        assertEquals("full", rule.type, "Type should be 'full'")
        assertEquals("172.16.254.1", rule.content, "Content should be IPv4 address")
        assertEquals(false, rule.dns, "DNS should match defaultDNSBehavior (true)")
    }

    @Test
    fun testParseRuleWithIPv6() {
        // Test parsing with IPv6 address
        val rule = RuleItem.parseRule("domain:[2001:0db8:85a3::8a2e:0370:7334]")
        assertEquals("domain", rule.type, "Type should be 'domain'")
        assertEquals(
            "[2001:0db8:85a3::8a2e:0370:7334]",
            rule.content,
            "Content should be IPv6 address"
        )
        assertEquals(false, rule.dns, "DNS should match defaultDNSBehavior (false)")
    }

    @Test
    fun testParseRuleWithMultipleColons() {
        // Test string with multiple colons
        val rule = RuleItem.parseRule("set:us-east:server1")
        assertEquals("set", rule.type, "Type should be 'set'")
        assertEquals(
            "us-east:server1",
            rule.content,
            "Content includes remaining parts after first colon"
        )
        assertEquals(false, rule.dns, "DNS should match defaultDNSBehavior (true)")
    }

    @Test
    fun testParseRuleEdgeCases() {
        // Test empty string
        val rule1 = RuleItem.parseRule("")
        assertEquals("", rule1.type)
        assertEquals("", rule1.content)
        assertEquals(false, rule1.dns)

        // Test only colon
        val rule2 = RuleItem.parseRule(":")
        assertEquals("", rule2.type)
        assertEquals("", rule2.content)
        assertEquals(false, rule2.dns)

        // Test type with empty content
        val rule3 = RuleItem.parseRule("set:")
        assertEquals("set", rule3.type)
        assertEquals("", rule3.content)
        assertEquals(false, rule3.dns)

        // Test empty type with content
        val rule4 = RuleItem.parseRule(":data")
        assertEquals("", rule4.type)
        assertEquals("data", rule4.content)
        assertEquals(false, rule4.dns)
    }

    @Test
    fun testParseRules() {
        // Test parsing a list of rules
        val list = listOf(
            "set+dns:region-asia",
            "regexp+dns:.*\\.net",
            "domain:test.org",
            "full:192.0.2.1",
            "[::1]",
        )
        // Note: parseRules calls parseRule(raw) without defaultDNSBehavior; assuming true for this test
        val rules = RuleItem.parseRules(list)
        assertEquals(5, rules.size, "Should parse all 5 rules")

        assertEquals("set", rules[0].type)
        assertEquals("region-asia", rules[0].content)
        assertEquals(true, rules[0].dns)

        assertEquals("regexp", rules[1].type)
        assertEquals(".*\\.net", rules[1].content)
        assertEquals(true, rules[1].dns)

        assertEquals("domain", rules[2].type)
        assertEquals("test.org", rules[2].content)
        assertEquals(false, rules[2].dns)

        assertEquals("full", rules[3].type)
        assertEquals("192.0.2.1", rules[3].content)
        assertEquals(false, rules[3].dns)

        assertEquals("", rules[4].type)
        assertEquals("[::1]", rules[4].content)
        assertEquals(false, rules[4].dns)
    }
}