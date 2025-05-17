@file:Suppress("UNCHECKED_CAST")

package io.nekohasekai.sagernet.ktx

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull

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
  assertFalse(to.containsKey("name") && to["name"] == null, "Key 'name' should not be set to null if it already existed with a non-null value and fromValue is null")
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
  to["address"] = "Some old string value" // Existing non-map value
  val from = mutableMapOf<String, Any?>(
   "address" to mutableMapOf("city" to "Paris")
  )
  mergeJson(from, to) // to["address"] should become the map from `from`
  val address = to["address"] as? JSONMap
  assertNotNull(address, "'address' should now be a map")
  assertEquals("Paris", address["city"])
 }


 @Test
 fun `mergeJson should replace list when listAppend is false`() {
  to["items"] = listOf(1, 2)
  val from = mutableMapOf<String, Any?>(
   "items" to listOf(3, 4)
  )
  mergeJson(from, to, listAppend = false)
  assertEquals(listOf(3, 4), to["items"])
 }

 @Test
 fun `mergeJson should append lists when listAppend is true and toValue is list`() {
  to["items"] = listOf(1, 2)
  val from = mutableMapOf<String, Any?>(
   "items" to listOf(3, 4)
  )
  mergeJson(from, to, listAppend = true)
  assertEquals(listOf(1, 2, 3, 4), to["items"])
 }

 @Test
 fun `mergeJson should wrap non-list toValue in list and append list when listAppend is true`() {
  to["items"] = 1 // Existing non-list value
  val from = mutableMapOf<String, Any?>(
   "items" to listOf(3, 4)
  )
  mergeJson(from, to, listAppend = true)
  assertEquals(listOf(1, 3, 4), to["items"])
 }

 @Test
 fun `mergeJson should create list with fromValue when listAppend is true and toValue is null`() {
  // 'to' does not initially contain "items"
  val from = mutableMapOf<String, Any?>(
   "items" to listOf(3, 4)
  )
  mergeJson(from, to, listAppend = true)
  assertEquals(listOf(3, 4), to["items"])
 }


 @Test
 fun `mergeJson should handle shouldAsMap returning true and merge into existing map`() {
  to["config"] = mutableMapOf("timeout" to 1000, "existingKey" to "value")
  val from = mutableMapOf<String, Any?>(
   "config" to TestConfigObject("3") // Using the data class
  )
  mergeJson(from, to)
  val config = to["config"] as? JSONMap
  assertNotNull(config)
  assertEquals(1000, config["timeout"], "Existing value 'timeout' should remain")
  assertEquals("value", config["existingKey"], "Other existing value 'existingKey' should remain")
  assertEquals("3", config["retries"], "Value from asMap ('retries') should be merged")
 }

 @Test
 fun `mergeJson should handle shouldAsMap returning true and create new map if toValue is not map`() {
  to["config"] = 500 // Existing non-map value
  val from = mutableMapOf<String, Any?>(
   "config" to TestConfigObject("3") // Using the data class
  )
  mergeJson(from, to)
  val config = to["config"] as? JSONMap
  assertNotNull(config, "Config should be a map now")
  assertEquals("3", config["retries"], "New map should be created and value merged")
  assertNull(config["config"], "The key 'config' should not exist within the nested map itself")
  assertFalse(config.containsKey(500.toString()), "Original non-map value should be replaced, not part of map")
 }


 @Test
 fun `mergeJson should handle shouldAsMap returning false and replace value`() {
  to["status"] = "old"
  val from = mutableMapOf<String, Any?>(
   "status" to "new" // shouldAsMap("new") is false
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

 // Test case for merging an object into a 'to' map where the key doesn't exist yet
 @Test
 fun `mergeJson should add object as map when key does not exist in to and shouldAsMap is true`() {
  val from = mutableMapOf<String, Any?>(
   "userProfile" to UserProfile("John Doe", ProfileDetails(30, "john@example.com"))
  )
  mergeJson(from, to)

  val userProfile = to["userProfile"] as? JSONMap
  assertNotNull(userProfile, "UserProfile should be added as a map")
  assertEquals("John Doe", userProfile["name"])

  val details = userProfile["details"] as? JSONMap
  assertNotNull(details, "Details should be a nested map")
  assertEquals(30, details["age"])
  assertEquals("john@example.com", details["email"])
 }

 @Test
 fun `mergeJson should correctly merge complex object into existing complex map`() {
  to = mutableMapOf(
   "settings" to mutableMapOf(
    "ui" to mutableMapOf("theme" to "dark", "fontSize" to 12),
    "notifications" to mutableMapOf("email" to true, "sms" to false)
   ),
   "user" to mutableMapOf("id" to "user123", "role" to "admin")
  )

  val from = mutableMapOf<String, Any?>(
   "settings" to mutableMapOf(
    "ui" to mutableMapOf("fontSize" to 14, "font" to "Arial"),
    "notifications" to mutableMapOf("sms" to true, "push" to true)
   ),
   "user" to TestConfigObject("viewer")
  )
  mergeJson(from, to)

  val uiSettings = (to["settings"] as JSONMap)["ui"] as JSONMap
  assertEquals("dark", uiSettings["theme"])
  assertEquals(14, uiSettings["fontSize"])
  assertEquals("Arial", uiSettings["font"])

  val notificationSettings = (to["settings"] as JSONMap)["notifications"] as JSONMap
  assertEquals(true, notificationSettings["email"])
  assertEquals(true, notificationSettings["sms"])
  assertEquals(true, notificationSettings["push"])

  val userMap = to["user"] as JSONMap
  assertEquals("viewer", userMap["retries"], "Should have merged the new retries field")
  assertEquals("user123", userMap["id"], "Original id should remain")
  assertEquals("admin", userMap["role"], "Original role should remain")
 }

}