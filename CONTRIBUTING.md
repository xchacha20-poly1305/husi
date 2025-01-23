# Welcome to husi contributing guide

Welcome and thank you for contributing! 🎉

# Overview

Readable > Useful > High performance but poorly readable.

Truly readable code is more than just clear; it's understandable even without context. (Contextless readable)

# Coding detail

## Common

* All comments use English.

* Reduce Confusing abbreviations.

Bad

```go
dl := &net.Dialer{}
```

Good

```go
dialer := &net.Dialer{}
```

* If a comment is meaningless， it is meaningless like this sentence, which is redundant.

* Readable code from naming, not comments.

* Use constant as much as possible.

Bad

```go
import (
    "net"

    N "github.com/sagernet/sing/common/network"
)

func defaultDNS() (net.Conn, error) {
    return net.Dial(N.NetworkUDP, "8.8.8.8:53") // Google DNS
}
```

Good

```go
import (
    "net"

    N "github.com/sagernet/sing/common/network"
)


func defaultDNS() (net.Conn, error) {
    const googleDNS = "8.8.8.8:53"
    return net.Dial(N.NetworkUDP, googleDNS)
}
```

Our style use **name** to tell reader what's this mean.

## Go

* Use `make fmt_go` and `make test_go` before committing.

* Create unit test as much as possible.

* Make writing documentions a habbit.

## Java / Kotlin

* Reduce `forEach`.

Bad

```kotlin
val numberList = listOf(1, 2, 3)
numberList.forEach(::println)
```

Good

```kotlin
val numberList = listOf(1, 2, 3)
for (number in numberList) {
    println(number)
}
```

* `also` sometimes better than `apply`.

Bad

```kotlin
lateinit var textView: TextView
val isVisible = true

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    textView = findViewByID(R.id.textView).apply {
        this@apply.isVisible = isVisible
    }
}
```

Good

```kotlin
lateinit var textView: TextView
val isVisible = true

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    textView = findViewByID(R.id.textView).also {
        it.isVisible = isVisible
    }
}
```
