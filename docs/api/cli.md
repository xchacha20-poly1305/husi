# Desktop CLI API

The Husi desktop binary is a small command-line interface (built with
[Clikt](https://ajalt.github.io/clikt/)). Running it with no subcommand launches the GUI; the
subcommands documented here let you **drive an already-running instance** from a terminal or a
script — query status, switch the clash mode, inspect connections, stream logs, and so on.

## Synopsis

```
fr.husi [GLOBAL OPTIONS] [COMMAND] [ARGS]
```

- On Linux the package installs the launcher on `PATH` as `fr.husi` (the package name). The examples
  below use `fr.husi`; substitute the platform executable on macOS/Windows.
- `fr.husi --help` lists commands; `fr.husi <command> --help` shows a command's options.

### How it works

Each subcommand below (except [`open`](#open)) connects to the running instance over a Unix domain
socket and issues a one-shot request:

```
<data-dir>/files/api.sock
```

Default data directory:

| Platform | Default                                                                 |
|----------|-------------------------------------------------------------------------|
| Linux    | `$XDG_CONFIG_HOME/husi` if set, otherwise `$HOME/.config/husi`          |
| macOS    | `$HOME/Library/Application Support/husi`                                |
| Windows  | `%APPDATA%\husi` if set, otherwise `%USERPROFILE%\AppData\Roaming\husi` |

If no instance is reachable on that socket, the command prints an error to **stderr** and exits with
status `1`. The data directory is resolved from the global [`--dir`](#global-options) option, so to
target a non-default instance you must pass `-d` **before** the subcommand:

```
fr.husi -d /custom/data/dir status
```

## Global options

These belong to the root command and must appear **before** the subcommand.

| Option                  | Description                                                                                                                                                       |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `-d, --dir <dir>`       | Data directory. Defaults to the platform config directory above. Must exist and be readable/writable. Determines which instance's socket the subcommands talk to. |
| `-l, --log-level <0-6>` | Log level override for a freshly launched instance (see [log levels](#log-levels)).                                                                               |
| `-m, --many`            | Allow multiple instances (skip the single-instance check). Only affects launching.                                                                                |
| `-b, --background`      | Launch without opening the main window (requires a working tray).                                                                                                 |

> Internal options `--autostart` and `--task` exist but are added by the program itself, not by
> users.

## JSON output

Every subcommand that talks to the running instance accepts `--json`. The contract is:

- **stdout** carries the result as JSON; **stderr** carries human-readable diagnostics.
- On success, stdout is valid JSON. On error, stdout is empty, the error goes to stderr, and the
  exit status is non-zero — so a caller can rely on "stdout parses as JSON ⇒ success".
- One-shot commands print a single pretty-printed JSON object. The streaming [`log`](#log) command
  prints [JSON Lines](https://jsonlines.org/): one compact JSON object per line.

Side-effecting commands return `{ "ok": true, ... }`; query commands return their data object.

### Exit codes

| Code | Meaning                                                                                           |
|------|---------------------------------------------------------------------------------------------------|
| `0`  | Success.                                                                                          |
| `1`  | No running instance, libcore failed to load, or an invalid argument (e.g. an unknown clash mode). |

## Commands

### `status`

Print an overview of the running instance.

```
fr.husi status [--json]
```

Text:

```
running:     yes
memory:      33554432 (32 MB)
goroutines:  42
connections: 3 active, 10 closed
clash mode:  rule (available: rule, global, direct)
```

JSON:

```json
{
  "running": true,
  "memory": 33554432,
  "memoryReadable": "32 MB",
  "goroutines": 42,
  "connections": {
    "active": 3,
    "closed": 10,
    "total": 13
  },
  "clashMode": {
    "current": "rule",
    "available": [
      "rule",
      "global",
      "direct"
    ]
  }
}
```

| Field                 | Type            | Notes                                                              |
|-----------------------|-----------------|--------------------------------------------------------------------|
| `running`             | boolean         | Always `true` (the command only succeeds against a live instance). |
| `memory`              | integer (bytes) | Go heap in use.                                                    |
| `memoryReadable`      | string          | Human-readable form of `memory`.                                   |
| `goroutines`          | integer         | Live goroutine count.                                              |
| `connections.active`  | integer         | Open connections.                                                  |
| `connections.closed`  | integer         | Closed-but-retained connections.                                   |
| `connections.total`   | integer         | `active + closed`.                                                 |
| `clashMode.current`   | string \| null  | `null` if the instance did not report a mode within ~2s.           |
| `clashMode.available` | string[]        | Modes exposed by the running configuration.                        |

### `mode`

Query or switch the clash mode.

```
fr.husi mode [--json]            # query current + available modes
fr.husi mode <mode> [--json]     # switch to <mode>
```

Setting an unknown mode (when the instance reports a non-empty mode list) prints an error to stderr
and exits `1`.

Query JSON:

```json
{
  "current": "rule",
  "available": [
    "rule",
    "global",
    "direct"
  ]
}
```

Set JSON:

```json
{
  "ok": true,
  "mode": "global"
}
```

| Field       | Type           | Notes                                          |
|-------------|----------------|------------------------------------------------|
| `current`   | string \| null | Query only; `null` if not reported within ~2s. |
| `available` | string[]       | Query only.                                    |
| `ok`        | boolean        | Set only.                                      |
| `mode`      | string         | Set only; the mode that was applied.           |

### `conn`

List connections, or close one with the [`close`](#conn-close) subcommand.

```
fr.husi conn [--active] [--closed] [--json]
fr.husi conn close <uuid> [--json]
```

| Option     | Description                   |
|------------|-------------------------------|
| `--active` | Show only active connections. |
| `--closed` | Show only closed connections. |

Passing both or neither of `--active`/`--closed` lists all connections.

List JSON (empty result yields `{ "connections": [], "total": 0 }`):

```json
{
  "connections": [
    {
      "uuid": "0b7f...e1",
      "state": "active",
      "network": "tcp",
      "src": "127.0.0.1:50080",
      "dst": "example.com:443",
      "host": "example.com",
      "outbound": "proxy/selector",
      "rule": "domain_suffix=example.com => route",
      "protocol": "tls",
      "chain": "proxy => direct",
      "uploadTotal": 1234,
      "downloadTotal": 56789,
      "startedAt": "2026-06-20 10:11:12",
      "closedAt": ""
    }
  ],
  "total": 1
}
```

Connection object fields:

| Field           | Type            | Notes                                               |
|-----------------|-----------------|-----------------------------------------------------|
| `uuid`          | string          | Connection identifier (use with `conn close`).      |
| `state`         | string          | `active` or `closed`.                               |
| `network`       | string          | e.g. `tcp`, `udp`.                                  |
| `src`           | string          | Source `address:port`.                              |
| `dst`           | string          | Destination `address:port`.                         |
| `host`          | string          | Sniffed/destination host; may be empty.             |
| `outbound`      | string          | Matched outbound (`tag/type`).                      |
| `rule`          | string          | Matched routing rule; `final` when no rule matched. |
| `protocol`      | string          | Sniffed protocol; may be empty.                     |
| `chain`         | string          | Outbound chain, joined by ` => `; may be empty.     |
| `uploadTotal`   | integer (bytes) | Bytes sent.                                         |
| `downloadTotal` | integer (bytes) | Bytes received.                                     |
| `startedAt`     | string          | Local time `yyyy-MM-dd HH:mm:ss`; empty if unknown. |
| `closedAt`      | string          | Same format; empty while the connection is active.  |

#### `conn close`

Close the connection with the given UUID, then exit.

```
fr.husi conn close <uuid> [--json]
```

JSON:

```json
{
  "ok": true,
  "uuid": "0b7f...e1"
}
```

| Field  | Type    | Notes                     |
|--------|---------|---------------------------|
| `ok`   | boolean | Always `true` on success. |
| `uuid` | string  | The UUID that was closed. |

### `log`

Stream logs, or clear the buffer.

```
fr.husi log [--json]          # replay the buffer, then stream live entries (Ctrl-C to stop)
fr.husi log --clear [--json]  # clear the log buffer, then exit
```

Text stream:

```
[INFO] inbound/mixed[mixed-in]: tcp connection from 127.0.0.1:50080
```

JSON stream — one compact object per line (JSON Lines):

```json
{
  "level": "INFO",
  "message": "inbound/mixed[mixed-in]: tcp connection from 127.0.0.1:50080"
}
```

Clear JSON:

```json
{
  "ok": true
}
```

| Field     | Type   | Notes                                                                                  |
|-----------|--------|----------------------------------------------------------------------------------------|
| `level`   | string | One of the [log levels](#log-levels); falls back to the numeric level if out of range. |
| `message` | string | Log line.                                                                              |

### `reset_network`

Reset the network (re-establish the default network / underlying connections).

```
fr.husi reset_network [--json]
```

JSON:

```json
{
  "ok": true
}
```

### `memory`

Print Go heap in use.

```
fr.husi memory [--json]
```

JSON:

```json
{
  "memory": 33554432,
  "memoryReadable": "32 MB"
}
```

| Field            | Type            | Notes                |
|------------------|-----------------|----------------------|
| `memory`         | integer (bytes) | Go heap in use.      |
| `memoryReadable` | string          | Human-readable form. |

### `goroutines`

Print the live goroutine count.

```
fr.husi goroutines [--json]
```

JSON:

```json
{
  "goroutines": 42
}
```

### `open`

Import deep links into a running instance, or launch the GUI when none are given. This is the entry
the desktop file / URL-scheme handler invokes (`fr.husi open %u`); an empty invocation behaves
exactly like a bare launch. This command does **not** support `--json`.

```
fr.husi open [deep-link...]
```

## Reference

### Log levels

| Value | Name    |
|-------|---------|
| `0`   | `PANIC` |
| `1`   | `FATAL` |
| `2`   | `ERROR` |
| `3`   | `WARN`  |
| `4`   | `INFO`  |
| `5`   | `DEBUG` |
| `6`   | `TRACE` |

### Examples

```sh
# Quick health check as JSON
fr.husi status --json | jq '.connections'

# Switch to global mode
fr.husi mode global

# List active connections and close the busiest one
uuid=$(fr.husi conn --active --json | jq -r '.connections | max_by(.downloadTotal) | .uuid')
fr.husi conn close "$uuid"

# Follow logs and pull out warnings/errors (JSON Lines)
fr.husi log --json | jq -c 'select(.level == "WARN" or .level == "ERROR")'

# Target a non-default data directory (note: -d comes before the subcommand)
fr.husi -d /tmp/husi-test status
```
