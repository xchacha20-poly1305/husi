# Daemon host

`husi-core run` is the privileged process host. It listens on a local endpoint
and can optionally expose the same gRPC surface on TCP for external clients
(browser dashboards, scripts). `husi-core session` is the unprivileged UI child
and never reads this config.

## Working directory

State (snapshots, owner, `core/` and `cache/`) lives in the working directory.
Override with `husi-core run --dir`. Defaults:

| Platform | Working directory |
|----------|-------------------|
| Linux | `/var/lib/husi` |
| macOS | `/Library/Application Support/husi` |
| Windows | `%ProgramData%\husi` |

The process `chdir`s into `<workingDir>/core` before it starts the box, so
relative paths in a pushed config (and in `daemon.json` TLS files) are resolved
there. Prefer absolute paths for certificates.

Installing the OS service (`husi-core service install`) creates this directory
with the right owner. The default `daemon.json` path is already inside it, so
the unit file does not need a `--config` flag.

## Local endpoint and owner model

By default the daemon listens only on a local socket:

| Platform | Endpoint |
|----------|----------|
| Linux / macOS | `/var/run/husi/api.sock` |
| Windows | `\\.\pipe\ProtectedPrefix\Administrators\husi` |

Access control is peer credentials plus an owner: the first local client to
`ClaimService` (or the first `StartService`) becomes the owner, and later
callers that are not that user are rejected. `TakeOverService` transfers
ownership. TCP `--listen` is a development bypass with **no** access control;
leave it unset on a real install.

## `daemon.json`

Read only by `husi-core run`, **before** the `chdir` into `core/` so a relative
`--config` path does not drift.

| | |
|--|--|
| Default path | `<workingDir>/daemon.json` |
| Override | `husi-core run --config <path>` |
| Missing file | The extra endpoint is not started. The daemon still runs. |
| Parse error | The process refuses to start (unknown fields included). |

Comments are allowed. Unknown JSON fields are an error, not ignored.

### Fields

The `api` object is the extra TCP endpoint. Omitting it (or the whole file)
disables the endpoint.

| Field | Meaning |
|-------|---------|
| `listen` | Bind address. Defaults to `127.0.0.1` when omitted. |
| `listen_port` | TCP port. `0` binds an ephemeral port. |
| `secret` | Shared secret. Clients send `Authorization: Bearer <secret>`. Empty allows unauthenticated access. |
| `access_control_allow_origin` | CORS origins. A string or a list. Empty means `*`. |
| `access_control_allow_private_network` | CORS `Access-Control-Allow-Private-Network`. |
| `tls` | sing-box inbound TLS object (`enabled`, `certificate_path`, `key_path`, …). |

`listen` / `listen_port` come from sing-box `ListenOptions`. Other
`ListenOptions` keys such as `detour` and `udp_*` have no effect on a
TCP-only endpoint (same as sing-box `service/api`). `bind_interface` is
rejected: the daemon has no `NetworkManager`.

### Examples

Plaintext loopback:

```json
{
  "api": {
    "listen": "127.0.0.1",
    "listen_port": 9090,
    "secret": "test"
  }
}
```

TLS on all interfaces:

```json
{
  "api": {
    "listen": "::",
    "listen_port": 9090,
    "secret": "replace-me",
    "access_control_allow_origin": [
      "https://dashboard.example.com"
    ],
    "tls": {
      "enabled": true,
      "certificate_path": "/etc/husi/api.crt",
      "key_path": "/etc/husi/api.key"
    }
  }
}
```

### Differences from sing-box `service/api`

- There is no `dashboard`. That field is unknown here and will fail the parse.
- `tls.acme` and `tls.certificate_provider` are rejected. The daemon context
  has no certificate store.
- The service set is husi's full host surface (see below), not only
  `StartedService`.

## Transport

One port accepts, at the same time:

- native gRPC (HTTP/2 prior knowledge / h2c, or TLS with ALPN `h2` and `http/1.1`)
- gRPC-Web (`application/grpc-web` and `application/grpc-web-text`)
- gRPC-Web over WebSocket (`grpc-websockets` subprotocol)

The gRPC-Web bridge is wire-compatible with the improbable-eng/grpc-web
clients. Authenticate with:

```
Authorization: Bearer <secret>
```

```
grpcurl -plaintext -H 'authorization: Bearer test' 127.0.0.1:9090 list
```

## Security

The extra endpoint exposes the **same** RPCs as the local socket, including
`husi.v1.DaemonService` (start/stop the instance, install/claim the service).
Secret authentication replaces peer credentials; owner checks that need a
local identity are skipped when the caller has none.

- Non-loopback binds must set `secret` **and** `tls`. Without a secret the
  daemon logs a warning and anyone who can reach the port has the full API.
- Do not reuse the development `--listen` flag for this. `--listen` has no
  secret and no TLS.
- Relative certificate paths are resolved after `chdir`, against
  `<workingDir>/core`. Use absolute paths.

`husi-core session` is unchanged: Unix socket only, no `daemon.json`.
