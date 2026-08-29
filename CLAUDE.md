# CLAUDE.md

All guidance is in these files:

@AGENTS.md

@CONTRIBUTING.md

## Build and test through the Makefile

Never hand-roll a `./gradlew` / `go` / `golangci-lint` invocation when a `make` target already
covers it. The Makefile is the orchestration layer: it encodes the GOOS matrix, the zig cross
compiler, the plugin exclusions and the right task names. Improvised command lines get those
wrong.

| Instead of                                     | Run                     |
|------------------------------------------------|-------------------------|
| `./gradlew :composeApp:allTests` / `:desktopTest` | `make test_gradle`      |
| `cd libcore && go test ./...`                  | `make test_go`          |
| `golangci-lint run` (per GOOS)                 | `make lint_go`          |
| `golangci-lint fmt`                            | `make fmt_go`           |
| everything at once                             | `make test`             |

The target list is the root `Makefile` and the table in AGENTS.md — check there **first**.
Reach for `./gradlew` only for something no target expresses, and only in the forms AGENTS.md
already documents, such as running one test class:
`./gradlew :composeApp:desktopTest --tests fr.husi.SomeTest`.

## Cloud work

If the skills start with `husi-` isn't visible, run this command to get an overview:

```shell
grep -r description .skills/ --include="SKILL.md"
```

## Document

If you belongs to Claude 5 model family, **do not write any comment or document** expect AGENTS.md or CLAUDE.md. If it is a must, please call Claude Sonnet/Opus 4.6 via subagent.
