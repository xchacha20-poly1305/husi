# Husi Contributing Guide

Welcome, and thank you for contributing! 🎉

---

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

---

### Go Guidelines

* Run `make fmt_go` and `make test_go` before committing.
* Write **unit tests** wherever possible.
* Make **documentation writing** a habit.

---

### Java / Kotlin Guidelines

#### Import usage

* **Always use imports** instead of fully qualified names in code.
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

#### `forEach` vs `for` loops

* `forEach` is fluent, especially at the end of a chain:

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

#### `also` vs `apply`

* Prefer `also` over `apply` when `this` is ambiguous.
* `apply` is great for object configuration, but nested scopes (e.g. in Activities or Fragments) may introduce confusion.
* `also` makes the receiver explicit via `it`, improving readability.

*Example of ambiguity with `apply`:*

```kotlin
private lateinit var textView: TextView
private val isVisible = true

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    textView = findViewByID(R.id.textView).apply {
        this@apply.isVisible = isVisible // `this` is ambiguous
    }
}
```

*Preferred version with `also`:*

```kotlin
private lateinit var textView: TextView
private val isVisible = true

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    textView = findViewByID(R.id.textView).also {
        it.isVisible = isVisible // `it` clearly refers to the TextView
    }
}
```
