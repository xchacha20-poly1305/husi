# Android Native Plugin Specification

## Abstract

Android proxy clients often distribute protocol implementations as separate
Android application packages (APKs) to limit the size of the main application
and allow independent updates. A plugin package bundles one or more native
executables and exposes them to a host application through a declared
`ContentProvider` and package metadata. This document specifies how a plugin
package advertises its identity, how a host discovers installed plugins, and
how the host obtains an executable path or copies the plugin's files.

## Status of This Memo

This document is a project-level specification of an inter-application packaging
and discovery contract on Android. It is not an Internet Standards Track
specification.

## 1. Introduction

On Android, an application cannot arbitrarily execute files from another
application's private data directory unless those files are exposed through an
inter-process contract or unpacked into a readable native library directory.
To keep specialized or large protocol binaries (such as NaiveProxy, Mieru,
Hysteria, or Juicity) optional, a host application discovers standalone plugin
APKs installed on the same device and runs their native binaries as child
processes.

How the host configures, launches, or forwards traffic to the child process is
agreed out of band and depends on the protocol or custom plugin profile (see
[custom-plugin.md](./custom-plugin.md) and [protect.md](./protect.md)). This
document specifies only package discovery, metadata, executable resolution, and
file extraction.

## 2. Background

The Android plugin model originated in `shadowsocks-android` [SS-ANDROID],
which packaged SIP003 plugins as standalone APKs exposing a `ContentProvider`
matched by an intent action. `SagerNet` [SAGERNET] adapted this mechanism for
general proxy executables under the `io.nekohasekai.sagernet.plugin` namespace,
retaining the `ContentProvider` query, file-stream, and `call` interfaces.

Later, `Matsuri` / `NekoBoxForAndroid` [NEKOBOX] introduced a metadata-only
shortcut ("Fast Mode") using `io.nekohasekai.sagernet.plugin.executable_path`.
Because Android extracts native libraries (`lib*.so`) into the plugin package's
world-readable `nativeLibraryDir` at install time, a host can resolve the
executable path directly from `PackageManager` metadata without starting the
plugin process or invoking its `ContentProvider`.

## 3. Requirements Language

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD",
"SHOULD NOT", "RECOMMENDED", "NOT RECOMMENDED", "MAY", and "OPTIONAL" in this
document are to be interpreted as described in BCP 14 [RFC2119] [RFC8174] when,
and only when, they appear in all capitals, as shown here.

## 4. Terminology

- Host: the main Android application that discovers plugin packages and
  executes their native binaries.
- Plugin: a standalone Android application package (APK) that bundles one or
  more native executables for use by a host.
- Provider: the `android.content.ContentProvider` component declared by a
  plugin to advertise its metadata and serve its executable path or files.
- Plugin ID: a non-empty string identifier (for example, `hysteria2-plugin`)
  that uniquely identifies the logical plugin functionality across providers.
- Native Library Directory: the filesystem directory indicated by a plugin's
  `ApplicationInfo.nativeLibraryDir`, managed by the Android package installer.

## 5. Package Packaging and Manifest Contract

### 5.1. Installation and Native Library Extraction

A plugin package bundles its native executable(s) as shared object files named
`lib<name>.so` under `lib/<abi>/` inside the APK.

When direct execution from `nativeLibraryDir` is used (Sections 7.2 and 8), the
system installer must extract the `.so` files onto the filesystem with execute
permissions:

- The `<manifest>` element SHOULD set `android:installLocation="internalOnly"`.
- The `<application>` element MUST ensure native libraries are extracted at
  install time: either by setting `android:extractNativeLibs="true"` in the
  merged manifest or by building with legacy JNI packaging enabled
  (`packaging.jniLibs.useLegacyPackaging = true` in Android Gradle Plugin).

### 5.2. ContentProvider Declaration

A plugin MUST declare at least one `<provider>` inside `<application>` with the
following attributes:

- `android:exported="true"`, so the host application can inspect and query it.
- `android:authorities`, set to a unique authority string (typically prefixed
  by the publisher's namespace; see Section 9). If a plugin bundles a forked
  binary with incompatible features or behavior, its `android:authorities`
  MUST NOT reuse an existing authority, and SHOULD NOT start with another
  project's or publisher's package name; instead, it SHOULD use the fork
  distributor's own package name prefix.
- `android:directBootAware="true"` is RECOMMENDED so the plugin can be used
  before the user unlocks the device when the host runs in Direct Boot mode.

If the host discovers plugins by inspecting `PackageInfo.providers` directly,
the plugin provider SHOULD be the first `<provider>` declared in the plugin's
manifest.

### 5.3. Intent Filters and Package Visibility

The provider MUST declare an `<intent-filter>` with the action:

```text
io.nekohasekai.sagernet.plugin.ACTION_NATIVE_PLUGIN
```

The provider MAY additionally declare a second `<intent-filter>` with the same
action and a `<data>` element binding the `plugin` scheme, a host authority,
and a path of `/<plugin_id>`:

```xml
<intent-filter>
    <action android:name="io.nekohasekai.sagernet.plugin.ACTION_NATIVE_PLUGIN" />
</intent-filter>
<intent-filter>
    <action android:name="io.nekohasekai.sagernet.plugin.ACTION_NATIVE_PLUGIN" />
    <data
        android:scheme="plugin"
        android:host="fr.husi"
        android:path="/<plugin_id>" />
</intent-filter>
```

On Android 11 (API level 30) and later, a host application MUST declare package
visibility for plugin packages in its own `AndroidManifest.xml`, either via a
`<queries>` intent entry for `io.nekohasekai.sagernet.plugin.ACTION_NATIVE_PLUGIN`
or by holding the `QUERY_ALL_PACKAGES` permission.

### 5.4. Provider Metadata

The `<provider>` element MUST include the plugin ID metadata entry:

- `io.nekohasekai.sagernet.plugin.id` (REQUIRED): a string (or string resource
  reference) naming the plugin ID. It MUST NOT be empty or blank.

The `<provider>` element SHOULD also include the Fast Mode metadata entry
(Section 8):

- `io.nekohasekai.sagernet.plugin.executable_path` (RECOMMENDED): a string (or
  string resource reference) giving the path to the executable relative to the
  plugin's `nativeLibraryDir` (for example, `libhysteria2.so`).

A host reading provider metadata MUST accept both literal string values and
Android `@string/...` resource references resolved against the plugin package's
resources.

## 6. Discovery and Resolution

A host discovers installed plugins by querying `PackageManager` (either via
`queryIntentContentProviders` for `ACTION_NATIVE_PLUGIN` or by scanning
installed packages with `GET_PROVIDERS | GET_META_DATA`).

When multiple installed plugin packages advertise the same
`io.nekohasekai.sagernet.plugin.id`:

1. The host SHOULD filter out any package that fails publisher verification
   (Section 9).
2. The host MAY order the remaining providers by a configured publisher
   preference list and select the highest-priority match.
3. If no preference order distinguishes them, the host MAY select any matching
   provider.

If no external plugin package matches the requested plugin ID, a host that
bundles internal fallback binaries in its own `nativeLibraryDir` MAY fall back
to its bundled binary.

## 7. Standard ContentProvider Interface

The standard `SagerNet` plugin interface operates on the plugin's
`ContentProvider`. Even when a plugin supports the Fast Mode extension
(Section 8), it MUST implement this `ContentProvider` interface so that hosts
relying on the standard contract can resolve or extract the executable.

### 7.1. Constants

| Constant | Value |
|---|---|
| `ACTION_NATIVE_PLUGIN` | `io.nekohasekai.sagernet.plugin.ACTION_NATIVE_PLUGIN` |
| `METADATA_KEY_ID` | `io.nekohasekai.sagernet.plugin.id` |
| `METADATA_KEY_EXECUTABLE_PATH` | `io.nekohasekai.sagernet.plugin.executable_path` |
| `METHOD_GET_EXECUTABLE` | `sagernet:getExecutable` |
| `EXTRA_ENTRY` | `io.nekohasekai.sagernet.plugin.EXTRA_ENTRY` |
| `COLUMN_PATH` | `path` |
| `COLUMN_MODE` | `mode` |

### 7.2. Direct Executable Resolution (`call`)

To obtain the absolute filesystem path of the plugin executable via IPC, the
host invokes `ContentResolver.call` on the provider with method
`sagernet:getExecutable` (`METHOD_GET_EXECUTABLE`).

A provider that supports direct execution MUST return a non-null `Bundle`
containing key `io.nekohasekai.sagernet.plugin.EXTRA_ENTRY` (`EXTRA_ENTRY`)
mapped to a `String` holding the absolute path to the executable (typically
`<nativeLibraryDir>/lib<name>.so`).

If the provider does not support direct execution from `nativeLibraryDir`, it
MAY throw `UnsupportedOperationException`.

### 7.3. File Enumeration and Extraction (`query` and `openFile`)

When direct execution is unavailable or a plugin needs to expose multiple
auxiliary files, the host queries and copies files from the provider:

1. **`getType(uri)`**: The provider SHOULD return `"application/x-elf"`.
2. **`query(uri, projection, selection, selectionArgs, sortOrder)`**:
   - The host passes `selection == null`, `selectionArgs == null`, and
     `sortOrder == null`. The provider SHOULD reject non-null values for these
     parameters.
   - The provider returns a `Cursor` containing the columns requested in
     `projection`, selected from:
     - `path` (`String`): relative destination path for the file (with leading
       and trailing `/` stripped), normally matching `<plugin_id>` for the main
       executable.
     - `mode` (`Int`): POSIX file permission bits (for example, `0b111101101` /
       octal `0755` for executables, or `0b110100100` / octal `0644` for data
       files).
3. **`openFile(uri, mode)`**:
   - The host opens each enumerated entry by requesting
     `content://<authority>/<path>` with mode `"r"`.
   - The provider MUST verify that `mode == "r"` and return a read-only
     `ParcelFileDescriptor` (`ParcelFileDescriptor.MODE_READ_ONLY`) for the
     corresponding file, or throw `FileNotFoundException` if the URI path is
     unknown.
4. **`insert` / `update` / `delete`**:
   - The provider MUST NOT allow mutation and MUST throw
     `UnsupportedOperationException` for `insert`, `update`, and `delete`.

## 8. Fast Mode Extension (`executable_path`)

`Matsuri` / `NekoBoxForAndroid` introduced a metadata-based optimization commonly
referred to as "Fast Mode". It is an extension to the original `SagerNet`
specification. Plugins are RECOMMENDED to provide it, and hosts MAY prefer it
over starting the plugin's `ContentProvider`.

### 8.1. Plugin Requirements

To support Fast Mode, a plugin:

1. Ensures its native executable is extracted to `nativeLibraryDir` at install
   time (Section 5.1).
2. Declares `<meta-data android:name="io.nekohasekai.sagernet.plugin.executable_path" android:value="<relative_path>" />`
   inside its `<provider>` element, where `<relative_path>` is relative to
   `ApplicationInfo.nativeLibraryDir` (for example, `libhysteria2.so`).

### 8.2. Host Resolution Procedure

A host that implements Fast Mode resolves a plugin executable as follows:

1. Locate the `ProviderInfo` for the target plugin ID (Section 6).
2. Read the string value of `io.nekohasekai.sagernet.plugin.executable_path`
   from `ProviderInfo.metaData`.
3. If present, resolve that relative path against
   `ProviderInfo.applicationInfo.nativeLibraryDir`.
4. Verify that the resolved path exists and is executable (`canExecute()`).

If the metadata key is absent, standard hosts SHOULD fall back to the
`ContentProvider` methods in Section 7 (`call` with `sagernet:getExecutable`,
or `query` + `openFile`). Note that a host MAY choose to implement only Fast
Mode; therefore, omitting `executable_path` limits compatibility with
Fast-Mode-only hosts, while omitting the `ContentProvider` methods limits
compatibility with standard `SagerNet` hosts. Implementing both is
RECOMMENDED.

## 9. Security Considerations

Executing a binary supplied by another installed package grants that binary the
host's network privileges, access to any file descriptors or configuration
files passed to it, and access to the host's socket protect path if configured.

### 9.1. Publisher and Signature Verification

Because any installed application can declare a `ContentProvider` with
`ACTION_NATIVE_PLUGIN` and an arbitrary `io.nekohasekai.sagernet.plugin.id`, a
host SHOULD verify the authenticity of a plugin package before trusting or
executing it:

- The host SHOULD maintain a mapping of known provider authority prefixes to
  trusted signing certificate SHA-256 digests.
- When a package's provider authority matches a known publisher prefix, the
  host SHOULD inspect the package's signing certificates
  (`GET_SIGNING_CERTIFICATES` on API 28+, checking `apkContentsSigners` when
  multiple signers are present or `signingCertificateHistory` otherwise) and
  reject the package if none of its certificates match the trusted set for that
  publisher.
- The host MAY automatically trust plugin packages signed with the same
  certificate as the host application itself.
- For third-party developers, the host MAY provide a user setting to register
  additional custom authority prefixes.

### 9.2. Arbitrary Plugin IDs

When a host supports user-defined or custom plugin profiles where the user can
specify an arbitrary `pluginId` (see [custom-plugin.md](./custom-plugin.md)),
the host SHOULD require explicit user approval for each `pluginId` before
resolving or executing it, preventing imported profiles or subscriptions from
silently invoking unexpected installed plugin packages.

### 9.3. Path Traversal in Fast Mode

When resolving `io.nekohasekai.sagernet.plugin.executable_path`, a plugin
supplies a relative path. The resolved file resides in the plugin's
`nativeLibraryDir`, which is owned and written by the Android system installer.

## 10. References

### 10.1. Normative References

[RFC2119] Bradner, S., "Key words for use in RFCs to Indicate Requirement
Levels", BCP 14, RFC 2119, March 1997.

[RFC8174] Leiba, B., "Ambiguity of Uppercase vs Lowercase in RFC 2119 Key
Words", BCP 14, RFC 8174, May 2017.

### 10.2. Informative References

[SS-ANDROID] shadowsocks-android plugin framework,
<https://github.com/shadowsocks/shadowsocks-android>.

[SAGERNET] SagerNet plugin API,
<https://github.com/SagerNet/SagerNet>.

[NEKOBOX] Matsuri / NekoBoxForAndroid fast native plugin loader,
<https://github.com/MatsuriDayo/NekoBoxForAndroid>.

## Appendix A. Minimal Plugin Implementation

The following `AndroidManifest.xml` and Kotlin `ContentProvider` illustrate a
minimal plugin package (`example-plugin` bundling `libexample.so`) that
conforms to both the standard `SagerNet` `ContentProvider` contract (Section 7)
and the Fast Mode extension (Section 8).

### A.1. `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    android:installLocation="internalOnly">

    <application
        android:allowBackup="false"
        android:extractNativeLibs="true"
        android:label="Example Plugin">

        <provider
            android:name=".BinaryProvider"
            android:authorities="com.example.plugin.example.BinaryProvider"
            android:directBootAware="true"
            android:exported="true">
            <intent-filter>
                <action android:name="io.nekohasekai.sagernet.plugin.ACTION_NATIVE_PLUGIN" />
            </intent-filter>
            <intent-filter>
                <action android:name="io.nekohasekai.sagernet.plugin.ACTION_NATIVE_PLUGIN" />
                <data
                    android:host="fr.husi"
                    android:path="/example-plugin"
                    android:scheme="plugin" />
            </intent-filter>

            <meta-data
                android:name="io.nekohasekai.sagernet.plugin.id"
                android:value="example-plugin" />
            <meta-data
                android:name="io.nekohasekai.sagernet.plugin.executable_path"
                android:value="libexample.so" />
        </provider>
    </application>

</manifest>
```

### A.2. `BinaryProvider.kt`

```kotlin
package com.example.plugin.example

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class BinaryProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "application/x-elf"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        check(selection == null && selectionArgs == null && sortOrder == null)
        val cursor = MatrixCursor(projection ?: arrayOf(COLUMN_PATH, COLUMN_MODE))
        val basePath = uri.path?.trim('/').orEmpty()
        if (PLUGIN_ID.startsWith(basePath)) {
            cursor.newRow()
                .add(COLUMN_PATH, PLUGIN_ID)
                .add(COLUMN_MODE, MODE_EXECUTABLE)
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        check(mode == "r")
        if (uri.path != "/$PLUGIN_ID") throw FileNotFoundException(uri.path)
        return ParcelFileDescriptor.open(
            File(getExecutable()),
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? =
        when (method) {
            METHOD_GET_EXECUTABLE -> Bundle().apply {
                putString(EXTRA_ENTRY, getExecutable())
            }
            else -> super.call(method, arg, extras)
        }

    private fun getExecutable(): String =
        File(context!!.applicationInfo.nativeLibraryDir, LIB_NAME).absolutePath

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException()

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException()

    companion object {
        private const val PLUGIN_ID = "example-plugin"
        private const val LIB_NAME = "libexample.so"
        private const val MODE_EXECUTABLE = 0b111101101 // 0755

        private const val METHOD_GET_EXECUTABLE = "sagernet:getExecutable"
        private const val EXTRA_ENTRY = "io.nekohasekai.sagernet.plugin.EXTRA_ENTRY"
        private const val COLUMN_PATH = "path"
        private const val COLUMN_MODE = "mode"
    }
}
```
