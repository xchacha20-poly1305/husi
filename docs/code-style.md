# Husi Code Style Guide

## Overview

**Readable > Useful > High performance but poorly readable.**

Truly readable code is more than just clear—it's understandable even without context (**contextless readability**).

---

## Coding Details

### Common Guidelines

* Use **English** for all comments.

* Stay **polite** in code comments. Avoid offensive language.

  * You can be grumpy, but express it with **decent wording**.

* Avoid **confusing abbreviations**.
  A reader unfamiliar with the abbreviation has to guess or grep; the few bytes saved are not worth the cognitive cost.

  **Bad:**

  ```go
  dl := &net.Dialer{}
  ```

  **Good:**

  ```go
  dialer := &net.Dialer{}
  ```

* **Redundant comments** are as useless as this sentence.

* Strive for **readability through naming**, not excessive comments.

* Use **constants** wherever possible.
  Magic literals hide intent and scatter duplicates across the codebase; a named constant is self-documenting and has exactly one place to update.

  **Exceptions** — inline literals are fine when the value is local and self-evident:
  * Layout-only dp values in Compose UI that no other component references (e.g. `Modifier.padding(16.dp)`).
  * Arbitrary delays with no domain meaning (e.g. a short animation debounce).

  **Bad:**

  ```go
  import (
      "net"

      N "github.com/sagernet/sing/common/network"
  )

  func dnsConn() (net.Conn, error) {
      return net.Dial(N.NetworkUDP, "8.8.8.8:53") // Google DNS
  }
  ```

  **Good:**

  ```go
  import (
      "net"

      N "github.com/sagernet/sing/common/network"
  )

  func dnsConn() (net.Conn, error) {
      const googleDNS = "8.8.8.8:53"
      return net.Dial(N.NetworkUDP, googleDNS)
  }
  ```

* Our style uses **names** to communicate meaning.

### Path Handling

* Do **not** build filesystem paths by string concatenation such as `base + "/child"` or `absolutePath + "/"`.
  String concatenation breaks on Windows (`\` vs `/`), mishandles trailing separators, and bypasses `File`'s normalization.

* Prefer `File.resolve(...)`, `File(parent, child)`, or equivalent path APIs when combining local paths.

* On Windows, we should use `/` instead of `\`.
  The Windows JVM returns `\` separators, but sing-box (the Go core) always expects `/`; `invariantPathString()` ensures the forward-slash form.

  **Bad:**

  ```kotlin
  val geoDir = repository.externalAssetsDir.absolutePath + "/geo"
  ruleSet.path = "$geoDir/$name.srs"
  ```

  **Good:**

  ```kotlin
  import fr.husi.ktx.invariantPathString

  val geoDir = repository.externalAssetsDir.resolve("geo")
  ruleSet.path = geoDir.resolve("$name.srs").invariantPathString()
  ```

---

### Go Guidelines

* Run `make fmt_go` and `make test_go` before committing.
* Write **unit tests** wherever possible.
* Make **documentation writing** a habit.

---

### Java / Kotlin Guidelines

#### Import usage

* **Always use imports** instead of fully qualified names in code.
  Fully qualified names clutter the call site and make refactoring harder -- the IDE cannot collapse or auto-update them.
* The **only exception** is when referencing `R` classes from other packages (e.g., `com.google.android.material.R`).

**Bad:**

```kotlin
val density = androidx.compose.ui.platform.LocalDensity.current
androidx.compose.runtime.DisposableEffect(view) { /* ... */ }
```

**Good:**

```kotlin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.DisposableEffect

val density = LocalDensity.current
DisposableEffect(view) { /* ... */ }
```

#### Explicit backing fields for flows

* Prefer explicit backing fields for public read-only `StateFlow` or `SharedFlow` properties backed by mutable flows.
* This keeps the public type read-only while avoiding extra `_uiState` / `_uiEvent` properties.
  The `_foo` / `foo` naming dance leaks the mutable handle into the class's namespace; an explicit backing field scopes it to the property body, so nothing else in the class can accidentally mutate it.

**Good:**

```kotlin
val uiState: StateFlow<ScreenUiState>
    field = MutableStateFlow(ScreenUiState())

val uiEvent: SharedFlow<ScreenUiEvent>
    field = MutableSharedFlow<ScreenUiEvent>()

fun updateName(name: String) {
    uiState.update { it.copy(name = name) }
}
```

#### `forEach` vs `for` loops

* `forEach` is fluent, especially at the end of a chain.
  However, `forEach` cannot `break` or `return` from the enclosing function, so standalone iterations that may exit early should use `for`.

  ```kotlin
  strings.filter { it.isNotEmpty() }.forEach { println(it) }
  ```

* For standalone iterations, `for` loops are often more flexible:

  * Can use `break`
  * Can use `return` from enclosing function
  * Explicit variable names are clearer

  ```kotlin
  fun firstNonEmptyString(strings: List<String>): String? {
      for (string in strings) {
          if (string.isNotEmpty()) {
              return string
          }
      }
      return null
  }
  ```

#### Prefer `Duration` overloads

* Prefer `Duration` overloads over raw numeric time parameters (e.g., `Long` milliseconds) whenever available.
  A raw number leaves the unit implicit at the call site, whereas `Duration` makes the unit explicit and prevents unit-mismatch bugs.

  **Bad:**

  ```kotlin
  delay(1000L)
  ```

  **Good:**

  ```kotlin
  import kotlin.time.Duration.Companion.milliseconds

  delay(1000.milliseconds)
  ```
