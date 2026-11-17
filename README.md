# Husi (虎兕)

Husi is a non-professional proxy-set-based multiplatform proxy tool set.

## 📲 Install & Run

### Android

[![Get it on Obtainium](./docs/assets/badge_obtainium.png)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22fr.husi%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fxchacha20-poly1305%2Fhusi%22%2C%22author%22%3A%22xchacha20-poly1305%22%2C%22name%22%3A%22husi%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5E%28%3F%21plugin-%29%5C%22%2C%5C%22includePrereleases%5C%22%3Afalse%7D%22%7D)

Or [Pre-release](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22fr.husi%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fxchacha20-poly1305%2Fhusi%22%2C%22author%22%3A%22xchacha20-poly1305%22%2C%22name%22%3A%22husi%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5E%28%3F%21plugin-%29%5C%22%2C%5C%22includePrereleases%5C%22%3Atrue%7D%22%7D)

### Desktop

* **Java requirement:** Standard packages (`deb`, `rpm`, `pacman`, `tarball`, `.dmg`, and standard Windows `.zip`/`.msi`) have no bundled JRE for end users and require system **Java Runtime >= 21**. `AppImage`, `Flatpak`, and Windows `-jbr` packages bundle a `jlink`-trimmed [JetBrains Runtime](https://github.com/JetBrains/JetBrainsRuntime) and do not require system Java.
* **Privilege model:** The launcher and UI run unprivileged. Privileges live in the separate `husi-core` daemon (`husi-core service install`; systemd / launchd / Windows SCM service `husi-daemon`). Without the daemon, the UI falls back to an unprivileged session host; TUN requires the daemon (or NetworkManager on Linux).

#### Linux

* **Native packages (`deb`, `rpm`, `pacman`):** Install and enable the daemon automatically and ship a polkit action so privilege prompts name the daemon cleanly.
* **Installing without root:**
  * **`tarball` (`.tar.zst`):** Unpacks a relocatable app subtree with its own installer:

    ```shell
    ./install.sh                     # installs under ~/.local, no root anywhere
    ./install.sh --with-daemon       # ... and installs the daemon too, via pkexec
    ./install.sh --prefix /opt/husi  # or somewhere else entirely
    ```

    `install.sh` copies the tree to `<prefix>/lib`, symlinks `<prefix>/bin`, registers a desktop entry and icon under `$XDG_DATA_HOME` with absolute paths (so the application menu and URL schemes work whether or not `<prefix>/bin` is on `PATH`), and creates a matching `uninstall.sh`. Without `--with-daemon`, nothing requests privileges; Husi still runs as a local proxy, and TUN can use either NetworkManager or the daemon installed later from Settings.
  * **`AppImage`:** Single self-contained file with a bundled JetBrains Runtime.
  * **`Flatpak`:** Sandboxed package with the same bundled runtime.

  On tarball and AppImage installs, installing the daemon through `pkexec` uses a generic polkit prompt because `husi-core` lives under a user-writable prefix; you can also run `husi-core service install` as root.
* **Config directory:** `$XDG_CONFIG_HOME/husi/` if set, otherwise `$HOME/.config/husi/`. The launcher reads optional user config files there:
  * `<config-dir>/husi/desktop-java-opts.conf` for JVM options
  * `<config-dir>/husi/desktop-app-args.conf` for application startup arguments

#### macOS

Install `Husi.app` from the `.dmg` image (requires system Java 21+). Optional user config files live under:

* `~/Library/Application Support/husi/desktop-java-opts.conf` for JVM options
* `~/Library/Application Support/husi/desktop-app-args.conf` for application startup arguments

#### Windows

Windows releases come in two pairs:

* Standard `.zip` and `.msi` (require system Java 21+).
* `-jbr.zip` and `-jbr.msi` (bundle a JetBrains Runtime in `runtime\` next to the launcher; the launcher prefers it over system Java unless overridden by the `JAVA` environment variable).

The MSI installs into `%LOCALAPPDATA%\Programs\Husi` for the current user without elevation, creates Start Menu and desktop shortcuts, and registers configured URL schemes. To choose another directory, run `msiexec /i <package>.msi INSTALLFOLDER="C:\path\to\Husi"` (add `/qn` for a silent install).

> [!NOTE]
> **Migrating from NSIS:** Uninstall the old NSIS installation before installing the MSI. The MSI does not migrate its uninstall entry or take ownership of its files. User configuration remains in the application data directory.

The Windows launcher runs unprivileged; TUN requires the `husi-core` daemon installed as a Windows service (via Settings, or `husi-core service install` from an elevated terminal). The MSI does not install or remove the service; without it, the UI falls back to an unprivileged session host.

| What | Where | Installed by |
|---|---|---|
| UI (launcher, jar, and bundled core) | `%LOCALAPPDATA%\Programs\Husi` | the MSI, per user, no elevation |
| Daemon (core the service runs) | `%ProgramFiles%\husi` | `husi-core service install`, elevated |
| Daemon state (snapshots, ownership) | `%ProgramData%\husi` | the daemon itself |

If the service is no longer needed, run `husi-core service uninstall` from an elevated terminal before uninstalling the app (add `--purge` to remove `%ProgramData%\husi` too). After an MSI upgrade, update the existing service from Settings to deploy the new core.

Official Windows payloads are Authenticode-signed with a self-signed certificate; see [release/windows/README.md](./release/windows/README.md) for fingerprints and verification instructions.

#### CLI Usage

```shell
$ fr.husi --help
Usage: fr.husi [<options>] [<deep-link>]...

Options:
  -d, --dir=<path>       Data directory
  -l, --log-level=<int>  Log level override (0-6)
  -m, --many             Ignore exist instance
  -b, --background       Start without opening the main window
  -h, --help             Show this message and exit

Arguments:
  <deep-link>  Deep links
```

`<deep-link>` accepts URLs to import (see [Subscription Import Link](./docs/subscription-link.md)).

### 🌐 Web Dashboard

The Web Dashboard (Wasm) supports both the sing-box API and the Clash API (including [reF1nd/sing-box](https://github.com/reF1nd/sing-box) extensions).

Application diagnostics are written to the browser console: filter for `[husi` and enable **Verbose** to see request timing and subscription state changes.

Because development moves rapidly, using the online hosted version is recommended. Due to the [Firefox LAN access bug for WebSocket](https://bugzilla.mozilla.org/show_bug.cgi?id=2081728), Firefox users should use the downloaded version instead.

#### Core configuration

##### `sing-box`

[sing-box API service](https://sing-box.sagernet.org/configuration/service/api/):

**Online**

```json
{
  "services": [
    {
      "type": "api",
      "access_control_allow_origin": [
        "http://127.0.0.1",
        "https://xchacha20-poly1305.github.io/husi"
      ],
      "access_control_allow_private_network": true,
      "dashboard": {
        "enabled": false
      }
    }
  ]
}
```

**Download**

```json
{
  "services": [
    {
      "type": "api",
      "dashboard": {
        "enabled": true,
        "download_url": "https://github.com/xchacha20-poly1305/husi/archive/refs/heads/gh-pages.zip"
      }
    }
  ]
}
```

[sing-box experimental Clash API](https://sing-box.sagernet.org/configuration/experimental/clash-api/):

**Online**

```json
{
  "experimental": {
    "clash_api": {
      "access_control_allow_origin": [
        "http://127.0.0.1",
        "https://xchacha20-poly1305.github.io/husi"
      ],
      "access_control_allow_private_network": true
    }
  }
}
```

**External**

```json
{
  "experimental": {
    "clash_api": {
      "external_ui": "ui",
      "external_ui_download_url": "https://github.com/xchacha20-poly1305/husi/archive/refs/heads/gh-pages.zip"
    }
  }
}
```

##### `mihomo`

**Download**

```yaml
external-ui: ui
external-ui-url: "https://github.com/xchacha20-poly1305/husi/archive/refs/heads/gh-pages.zip"
```

## 📚 Documentation

* [End-user Wiki](https://github.com/xchacha20-poly1305/husi/wiki)
* [Daemon Host (`husi-core run` & `daemon.json`)](./docs/daemon.md)
* [Configuration Screen Hotkeys](./docs/hotkeys.md)
* [Subscription Import Link (`husi://subscription`)](./docs/subscription-link.md)

## 🛠️ Contributing & Building

* [Contributing Guide](./CONTRIBUTING.md) — ways to contribute, development setup, common commands, and architecture notes
* [Build & Packaging Guide](./docs/build.md) — reproducible compilation, cross-compilation, native packaging, and signing
* [Code Style Guide](./docs/code-style.md) — Go, Kotlin, and cross-platform path conventions

### 📚 Localization

Is Husi not in your language, or is a translation incorrect or incomplete? Help translate on [Weblate](https://hosted.weblate.org/engage/husi/).

[![Translation status](https://hosted.weblate.org/widgets/husi/-/horizontal-auto.svg)](https://hosted.weblate.org/engage/husi/)

## 📖 License

[GPL-3.0 or later](./LICENSE)

## 🌐 GeoIP data

Husi can generate and use GeoIP rule-set assets derived from MaxMind GeoLite2 data. GeoLite2 data is
created by MaxMind and is subject to the
[GeoLite2 End User License Agreement](https://www.maxmind.com/en/geolite/eula) and related attribution
and update requirements.

Third-party mirrors or re-published GeoLite2 database files do not grant additional rights beyond
MaxMind's terms. If you build, redistribute, or use Husi with generated GeoIP assets, you are
responsible for ensuring that your use and redistribution of those assets complies with the
applicable MaxMind license terms.

## 🤝 Acknowledgements

* [SagerNet/sing-box](https://github.com/SagerNet/sing-box)
* [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
* [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet)
* [XTLS/AnXray](https://github.com/XTLS/AnXray)
* [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid)
* [SagerNet/sing-box-for-android](https://github.com/SagerNet/sing-box-for-android)
* [AntiNeko/CatBoxForAndroid](https://github.com/AntiNeko/CatBoxForAndroid)
* [MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)
* [dyhkwong/Exclave](https://github.com/dyhkwong/Exclave)
* [chen08209/FlClash](https://github.com/chen08209/FlClash)
* [appshubcc/Bettbox](https://github.com/appshubcc/Bettbox)
* [Zephyruso/zashboard](https://github.com/Zephyruso/zashboard)
* [RikkaApps/RikkaX](https://github.com/RikkaApps/RikkaX)

Developing

* [![](https://resources.jetbrains.com/storage/products/company/brand/logos/jetbrains.svg)](https://www.jetbrains.com)

  JetBrains' powerful IDE.
