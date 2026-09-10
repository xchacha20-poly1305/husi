# Husi Project skills

Skills name starts with `husi-` are the project develop skills. And the local skills will be ignored.

# Install for Claude Code

This directory is on `.agents/skills`. If you want to use them in Claude, you should link / copy this directory to `.claude/skills`.

Run on project root:

```shell
mkdir -p .claude/
ln -s ../.agents/skills .claude/skills
```

# Suggested extra skills

## Android official

| name             | reason                                          |
|------------------|-------------------------------------------------|
| android-cli      | Powerful Android developing cli manual          |
| navigation-3     | The guide of a fasion new navigation fragmework |
| navigation-event | Correct navigation usage                        |

They are on [android/skills](https://github.com/android/skills). Install
with [Android CLI](https://developer.android.com/tools/agents/android-cli).

```shell
# --agent=codex install to .agents/skills
android skills add android-cli navigation-3 navigation-event --agent=codex --project=.
```

## Material Icon download

Agents should not draw the Material icon manually.

<https://github.com/xchacha20-poly1305/material-icon-download>

