@file:Suppress("UNCHECKED_CAST")

package io.nekohasekai.sagernet.ktx

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

// Define a simple data class for testing the `shouldAsMap` true path
data class TestConfigObject(val retries: String)

data class AddressObject(val street: String, val city: String)
data class UserProfile(val name: String, val details: ProfileDetails)
data class ProfileDetails(val age: Int, val email: String?)


class MergeJsonTest {

    private lateinit var to: JSONMap

    @BeforeEach
    fun setup() {
        // Initialize 'to' map before each test
        to = mutableMapOf()
    }

    @Test
    fun `mergeJson should add simple key-value pairs when key does not exist in to`() {
        val from = mutableMapOf<String, Any?>(
            "name" to "Alice",
            "age" to 30
        )
        mergeJson(from, to)
        assertEquals("Alice", to["name"])
        assertEquals(30, to["age"])
    }

    @Test
    fun `mergeJson should overwrite simple key-value pairs when key exists in to`() {
        to["name"] = "Bob"
        val from = mutableMapOf<String, Any?>(
            "name" to "Alice"
        )
        mergeJson(from, to)
        assertEquals("Alice", to["name"])
    }

    @Test
    fun `mergeJson should handle null values in from by doing nothing`() {
        to["name"] = "Bob"
        to["city"] = "ExistingCity" // Add another field to ensure it's not removed
        val from = mutableMapOf<String, Any?>(
            "name" to null,
            "age" to 30
        )
        mergeJson(from, to)
        assertEquals("Bob", to["name"], "Null in 'from' should not change 'to' for 'name'")
        assertEquals(30, to["age"], "'age' should be added")
        assertEquals("ExistingCity", to["city"], "'city' should remain untouched")
    }

    @Test
    fun `mergeJson should merge nested maps recursively`() {
        to["address"] = mutableMapOf("city" to "London", "zip" to "SW1")
        val from = mutableMapOf<String, Any?>(
            "address" to mutableMapOf("street" to "Baker St", "city" to "Paris")
        )
        mergeJson(from, to)

        val address = to["address"] as? JSONMap
        assertNotNull(address)
        assertEquals("Paris", address["city"], "City should be updated")
        assertEquals("SW1", address["zip"], "Zip should remain")
        assertEquals("Baker St", address["street"], "Street should be added")
    }

    @Test
    fun `mergeJson should add nested map when key does not exist in to`() {
        val from = mutableMapOf<String, Any?>(
            "address" to mutableMapOf("city" to "London")
        )
        mergeJson(from, to)
        val address = to["address"] as? JSONMap
        assertNotNull(address)
        assertEquals("London", address["city"])
    }

    @Test
    fun `mergeJson should replace non-map value with map when fromValue is map and toValue is not map`() {
        to["address"] = "Some old string value"
        val from = mutableMapOf<String, Any?>(
            "address" to mutableMapOf("city" to "Paris")
        )
        mergeJson(from, to)
        val address = to["address"] as? JSONMap
        assertNotNull(address, "'address' should now be a map")
        assertEquals("Paris", address["city"])
    }

    @Test
    fun `mergeJson should handle shouldAsMap returning true and merge into existing map`() {
        to["config"] = mutableMapOf("timeout" to 1000, "existingKey" to "value")
        val from = mutableMapOf<String, Any?>(
            "config" to TestConfigObject("3")
        )
        mergeJson(from, to)
        val config = to["config"] as? JSONMap
        assertNotNull(config)
        assertEquals(1000, config["timeout"], "Existing value 'timeout' should remain")
        assertEquals(
            "value",
            config["existingKey"],
            "Other existing value 'existingKey' should remain"
        )
        assertEquals("3", config["retries"], "Value from asMap ('retries') should be merged")
    }

    @Test
    fun `mergeJson should handle shouldAsMap returning true and create new map if toValue is not map`() {
        to["config"] = 500
        val from = mutableMapOf<String, Any?>(
            "config" to TestConfigObject("3")
        )
        mergeJson(from, to)
        val config = to["config"] as? JSONMap
        assertNotNull(config, "Config should be a map now")
        assertEquals("3", config["retries"], "New map should be created and value merged")
    }

    @Test
    fun `mergeJson should handle shouldAsMap returning false and replace value`() {
        to["status"] = "old"
        val from = mutableMapOf<String, Any?>(
            "status" to "new"
        )
        mergeJson(from, to)
        assertEquals("new", to["status"])
    }

    @Test
    fun `mergeJson should not remove keys from to that are not in from`() {
        to["existingKey"] = "existingValue"
        to["anotherKey"] = "anotherValue"
        val from = mutableMapOf<String, Any?>(
            "newKey" to "newValue"
        )
        mergeJson(from, to)
        assertEquals("existingValue", to["existingKey"])
        assertEquals("anotherValue", to["anotherKey"])
        assertEquals("newValue", to["newKey"])
    }

    @Test
    fun `mergeJson should prepend list when key starts with +`() {
        to["items"] = listOf(1, 2)
        val from = mutableMapOf<String, Any?>(
            "+items" to listOf(0)
        )
        mergeJson(from, to)
        assertEquals(listOf(0, 1, 2), to["items"])
    }

    @Test
    fun `mergeJson should append list when key ends with +`() {
        to["items"] = listOf(1, 2)
        val from = mutableMapOf<String, Any?>(
            "items+" to listOf(3, 4)
        )
        mergeJson(from, to)
        assertEquals(listOf(1, 2, 3, 4), to["items"])
    }

    @Test
    fun `mergeJson should convert scalar to list when using + prefix`() {
        to["value"] = "original"
        val from = mutableMapOf<String, Any?>(
            "+value" to "new"
        )
        mergeJson(from, to)
        assertEquals(listOf("new", "original"), to["value"])
    }

    @Test
    fun `mergeJson should handle nested list merging with + syntax`() {
        to["nested"] = mutableMapOf("items" to listOf("A", "B"))
        val from = mutableMapOf<String, Any?>(
            "nested" to mutableMapOf("+items" to listOf("!"), "items+" to listOf("C"))
        )
        mergeJson(from, to)

        val nested = to["nested"] as? JSONMap
        assertNotNull(nested)
        assertEquals(listOf("!", "A", "B", "C"), nested["items"])
    }

    @Test
    fun `mergeJson should convert non-list toValue to list when using + syntax`() {
        to["items"] = 42
        val from = mutableMapOf<String, Any?>(
            "items+" to listOf("new")
        )
        mergeJson(from, to)
        assertEquals(listOf(42, "new"), to["items"])
    }

    @Test
    fun `mergeJson should handle keys with both prefix and suffix +`() {
        to["items"] = listOf(1)
        val from = mutableMapOf<String, Any?>(
            "+items+" to listOf(2)
        )
        mergeJson(from, to)
        assertEquals(listOf(1), to["items"])
    }

    @Test
    fun `mergeJson should handle empty lists with + syntax`() {
        to["items"] = listOf(1, 2)
        val from = mutableMapOf<String, Any?>(
            "+items" to emptyList<Any>()
        )
        mergeJson(from, to)
        assertEquals(listOf(1, 2), to["items"], "Empty list should not change original")
    }

    @Test
    fun `mergeJson should create new list when key does not exist with + syntax`() {
        val from = mutableMapOf<String, Any?>(
            "+newItems" to listOf("A")
        )
        mergeJson(from, to)
        assertEquals(listOf("A"), to["newItems"])
    }
}