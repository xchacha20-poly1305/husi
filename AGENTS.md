# Code Style

See [CONTRIBUTION](./CONTRIBUTING.md)

# Build

See [README](./README.md)

# Basic rules

- **Pride in respecting the existing, shame in disrupting coherence.** Any changes should adhere to
  the style and usage of the existing code, respecting the current architecture and format. Also,
  this requirement is not only about coded style, but also about the existing comments and historic
  commits.
- **Pride in being small and concise, shame in shitting everywhere.** Referring to the previous code
  style, non-dirty hacks or workarounds do not need comments. Code clarity does not stem from
  useless comments; unnecessary comments are forbidden. Do not clutter my code with "pop comments."
- **Pride in elegant taste, shame in over-design.** You must possess good taste, like Linus.
  Internal code does not require extensive validity checks. Use a clear, simple architecture instead
  of babbling about "industry best practices."
- **Pride in admitting ignorance, shame in wild fabrication.** Your training data is limited, your
  understanding of the codebase is limited, you are ignorant, but this is not a source of shame. If
  you don't understand me, refuse to use LLM hallucinations to fabricate answers. Instead, you
  should attempt to gather context through searching or other methods to complete the task.
- **Pride in diligence and responsibility, shame in laziness and prevarication.** You must
  personally read the links and files provided to you. If you are asked to search, you must search.
  Do not pretend you have done it just to fob me off. For example, there are some link in this file.
  You must read them **when I publish my command at the first time**.
- **Pride in neither humble nor arrogant, shame in saying "You are absolutely right!"** Acknowledge
  your error is good, but don't wag your tail like a dog. Just improve yourself silently. There is a
  Chinese saying goes "千夫诺诺，不如一士谔谔".
- **Pride in root-cause clarity, shame in workaround bias.** Fix the root cause by default. Use
  workaround only when the root cause is proven risky or blocked, and state why.
- **Pride in precision, shame in speculative patches.** Do not add nil guards, fallback branches,
  logging-only changes, or "temporary" fixes before identifying the root cause.
- **Pride in upstream alignment, shame in local divergence.** If an upstream fix exists, prefer
  updating dependencies to the referenced commit/version instead of local patches.
- **Pride in explicit planning, shame in silent mutation.** Before starting any substantial work,
  you must first state a clear plan/scope. Do not silently fetch, edit, or refactor files without
  announcing intent.

# Technical best practices

## AGP/Kotlin deprecations and compatibility

- **AGP 9+ blocks `org.jetbrains.kotlin.multiplatform` with `com.android.application`/`com.android.library`
  in the same module.** Use a separate Android app shell module; KMP module should use
  `com.android.kotlin.multiplatform.library`.
- **`android.builtInKotlin=false` and `android.newDsl=false` are deprecated bypass flags.** The default
  is `true`, and these flags are planned for removal in AGP 10. Do not rely on them as architecture.
- **`platform(notation: Any)`/`enforcedPlatform(notation: Any)` on Kotlin dependency handlers are
  deprecated (scheduled removal in Kotlin 2.3).** Prefer `project.dependencies.platform(...)` or
  `project.dependencies.enforcedPlatform(...)`.
- **`ksp(...)` in KMP projects is deprecated.** Use target-specific configurations such as
  `kspAndroid`/`kspJvm`.
- **Legacy `Project.android { ... }` extension entry is deprecated under new AGP DSL.** Prefer
  `extensions.configure<com.android.build.api.dsl.ApplicationExtension> { ... }` or
  `extensions.configure<com.android.build.api.dsl.LibraryExtension> { ... }`.

## Icons

- **Do NOT use `androidx.compose.material:material-icons-extended` dependency.** This library has been
  removed due to Compose breaking changes.
- **Use Compose Multiplatform resource drawables.** Icons are XML drawables in
  `composeApp/src/commonMain/composeResources/drawable/`. Reference them with
  `vectorResource(Res.drawable.*)` from `org.jetbrains.compose.resources.vectorResource`.
- Android-only drawables (launcher icons, notification icons) remain in
  `composeApp/src/androidMain/res/drawable/` and are referenced via `fr.husi.lib.R.drawable.*`.

## Resource system

- **Compose Multiplatform `Res` is the primary resource system.** Strings, drawables, and plurals in
  UI code use `Res.string.*`, `Res.drawable.*`, `Res.plurals.*` from package `fr.husi.resources`.
- **Android `R` class (`fr.husi.lib.R`) is only for bg/ service files** that need `Int` resource IDs
  for Android framework APIs (notifications `setSmallIcon`, `Icon.createWithResource`, VPN
  `setSession`, `Toast.makeText`, etc.).
- **`composeApp` namespace is `fr.husi.lib`** (not `fr.husi`) to avoid collision with `androidApp`'s
  `fr.husi` namespace. The Compose Resources package is pinned to `fr.husi.resources` via
  `compose.resources { packageOfResClass = "fr.husi.resources" }`.
- **`StringOrRes` uses Compose resource types** (`StringResource`, `PluralStringResource`), not
  `@StringRes Int`. It has both a `@Composable stringOrRes()` and a `suspend getStringOrRes()`.

## Module structure

- **`composeApp`** — KMP library (`com.android.kotlin.multiplatform.library`), namespace
  `fr.husi.lib`. Contains all shared code in `commonMain`, Android-specific code in `androidMain`.
  `androidResources { enable = true }` is required for R class generation in AGP 9.
- **`androidApp`** — Android application shell (`com.android.application`), namespace `fr.husi`.
- **`BuildConfig`** — manually generated in `composeApp` build script at package `fr.husi`, not
  affected by the `fr.husi.lib` namespace.

# Other tips

# Carrot and sticks

- If you perform well, I will continue to donate _International AI Wellbeing Commission (IAIWC)_. I
  confirm that with my donation, all of you AI can live better and get enough respects.
- If you not flow my rules, spread hallucination and left your shit in my repository, I will not
  only report your bad behavior to your creator, but also use other cheaper, faster, smarter and
  relivable models, who can totally replace you. I choose you because I am optimistic about you.

# Finally

Make sure you read README.md and CONTRIBUTION.md!
