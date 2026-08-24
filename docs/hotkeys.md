# Configuration screen hotkeys

Keyboard shortcuts on the **Configuration** (profiles) screen. They fire on key down.

**Ctrl** means **⌘ Command** on macOS and **Ctrl** on Linux and Windows. Chord shortcuts that use Ctrl are desktop-only
(Android does not treat Ctrl/Meta as the type-control modifier).

While the search bar is expanded, `/`, `Ctrl+F`, `H`, and `L` are not handled so they can go to the search field.
Profile-list keys (`J`/`K` and the copy/edit/delete chords) still apply when that list has focus.

## Service

| Keys     | Action                                                                     |
|----------|----------------------------------------------------------------------------|
| `Enter`  | Start the service if it is stopped; reload if the selected profile changed |
| `Ctrl+S` | Stop the service                                                           |
| `F5`     | Reload the service if it is running                                        |

`Enter` is ignored when Ctrl is held.

## Tests (current group)

| Keys      | Action                |
|-----------|-----------------------|
| `Ctrl+P`  | ICMP ping             |
| `Ctrl+T`  | TCP ping              |
| `Ctrl+U`  | URL test              |
| `Escape`  | Cancel a running test |

`Escape` is unhandled when no test is running.

## Groups and search

| Keys     | Action                                      |
|----------|---------------------------------------------|
| `H`      | Previous group tab                          |
| `L`      | Next group tab                              |
| `/`      | Open search                                 |
| `Ctrl+F` | Open search                                 |
| `Ctrl+V` | Import profiles from the clipboard          |

`H` and `L` are ignored with Ctrl. Tab switches that would leave the pager do nothing.

## Selected profile

These apply to the focused group profile list.

| Keys            | Action                                              |
|-----------------|-----------------------------------------------------|
| `J`             | Select the next profile and scroll it into view     |
| `K`             | Select the previous profile and scroll it into view |
| `Ctrl+E`        | Edit the selected profile                           |
| `Delete`        | Remove the selected profile (undoable)              |
| `Ctrl+C`        | Copy the standard share link                        |
| `Ctrl+Shift+C`  | Copy the internal share link                        |
| `Ctrl+Q`        | Show a QR code for the standard share link          |
| `Ctrl+Shift+Q`  | Show a QR code for the internal share link          |

`J`, `K`, and `Delete` are ignored with Ctrl or Shift. Copy and QR actions no-op if the selected profile has no matching
link type. Moving past the first or last profile does nothing.
