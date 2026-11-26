# Custom Plugin

The **Custom Plugin** profile lets husi launch an arbitrary external proxy binary or Android plugin APK, inject runtime parameters via placeholders, and forward traffic to it over a local **SOCKS5** inbound (`127.0.0.1:<port>`).

Your plugin must listen for SOCKS5 connections (without authentication) on the allocated `{{listen_host}}:{{listen_port}}` (`{{listen_addr}}`).

## Platform setup

### Android

Android launches plugins from installed plugin APKs via their `pluginId`:

1. Install the plugin APK on the device.
2. Open **Settings → Plugin → Approved Custom Plugins** and approve the plugin ID (either check it under **Installed Plugins** or add it manually).
3. Create a **Custom Plugin** profile and select the approved ID in **Plugin ID**.

Unapproved plugin IDs are rejected when starting the profile.

### Desktop (Linux, macOS, Windows)

Desktop launches a local executable directly:

1. Create a **Custom Plugin** profile.
2. In **Executable Path**, pick the binary via the file chooser, drag and drop it onto the row/dialog, or enter its path manually.

The path must point to an existing executable file.

## Profile fields

| Field | Description |
|---|---|
| **Plugin ID** / **Executable Path** | Target plugin to run (Android plugin ID or Desktop executable path). |
| **Supports Protect Path** | Enable if the plugin can protect its own outbound sockets using husi's protect mechanism (see [protect.md](./protect.md) and [protect_windows.md](./protect_windows.md)). |
| **Launch Arguments** | Command-line arguments passed to the plugin. Parsed with shell-style quoting (`'...'`, `"..."`, and `\` escapes). Splitting happens **before** placeholder substitution, so expanded values containing spaces or newlines stay in a single argument. |
| **Config File Name** | File name (including extension) used when writing the temporary config file. Defaults to `config.json`. Path separators are stripped. |
| **Config Content** | Optional configuration file content (JSON, YAML, TOML, INI, etc.). Saved verbatim without sing-box schema validation. If left empty, no config file is written. |
| **Environment Variables** | Optional `KEY=VALUE` entries, one per line. Empty lines and lines starting with `#` are ignored. Values support placeholder substitution. |
| **Server Address** / **Server Port** | Optional. When both are set, husi enables server mapping, proxy chains, and latency tests, and populates `{{server_*}}` and `{{final_*}}`. |

In the **Launch Arguments** and **Environment Variables** dialogs, click the **?** button in the title bar to view and copy placeholders. In the **Config Content** editor, click the **info** button in the top bar to view and insert placeholders at the cursor.

## Placeholders

Placeholders use `{{name}}` syntax. Unknown `{{...}}` tokens are left untouched.

| Placeholder | Scope | Value |
|---|---|---|
| `{{listen_host}}` | Args, Env, Config | Local SOCKS5 bind host (`127.0.0.1`). |
| `{{listen_port}}` | Args, Env, Config | Allocated local SOCKS5 port. |
| `{{listen_addr}}` | Args, Env, Config | Local SOCKS5 address (`127.0.0.1:<port>`). |
| `{{final_host}}` | Args, Env, Config | Target host to dial: `127.0.0.1` when mapped (e.g., proxy chain / local mapping), otherwise `serverAddress`. |
| `{{final_port}}` | Args, Env, Config | Mapped port or `serverPort`; empty if unset. |
| `{{final_addr}}` | Args, Env, Config | Ready-to-use `host:port` (IPv6 bracketed); empty if host or port is unset. |
| `{{server_host}}` | Args, Env, Config | Original `serverAddress` (useful for TLS SNI / `Host` header even when mapped). |
| `{{server_port}}` | Args, Env, Config | Original `serverPort`; empty if unset. |
| `{{config}}` | Args, Env | Path to the generated config file; empty if **Config Content** is empty. |
| `{{protect_path}}` | Args, Env, Config | Protect socket/path when **Supports Protect Path** is enabled and protection is active; otherwise empty. |
| `{{ca_path}}` | Args, Env, Config | Path to husi's custom plugin CA file (`plugin-ca.pem`) if present; otherwise empty. |
| `{{ca_content}}` | Args, Env, Config | Raw UTF-8 PEM text of `plugin-ca.pem` if present; otherwise empty. |
| `{{ca_content_json}}` | Args, Env, Config | JSON-escaped content of `plugin-ca.pem` (without surrounding quotes) for embedding inside a JSON string. |
| `{{log_level}}` | Args, Env, Config | Current log level (`trace`, `debug`, `info`, `warn`, `error`, `fatal`). |
| `{{cache_dir}}` | Args, Env, Config | Application cache directory path. |

### Custom CA

`{{ca_path}}`, `{{ca_content}}`, and `{{ca_content_json}}` read husi's global custom plugin CA file (`plugin-ca.pem` in the app's external assets directory). If no custom CA file is configured, all three expand to an empty string.

## Examples

### Config-file plugin (JSON)

- **Launch Arguments**:

  ```text
  client -c {{config}}
  ```

- **Config File Name**: `config.json`
- **Config Content**:

  ```json
  {
    "listen": "{{listen_addr}}",
    "server": "{{final_addr}}",
    "sni": "{{server_host}}",
    "ca_pem": "{{ca_content_json}}",
    "protect_path": "{{protect_path}}",
    "log_level": "{{log_level}}"
  }
  ```

- **Server Address**: `example.com`
- **Server Port**: `443`

### CLI-only plugin (no config file)

- **Launch Arguments**:

  ```text
  --socks5 {{listen_addr}} --remote {{final_addr}} --sni {{server_host}}
  ```

- **Environment Variables**:

  ```text
  LOG_LEVEL={{log_level}}
  SSL_CERT_FILE={{ca_path}}
  ```

- **Config Content**: *(leave empty)*
