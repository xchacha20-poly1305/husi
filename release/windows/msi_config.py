"""Generate nFPM's JSON-compatible YAML from the shared Windows payload."""

import argparse
import datetime
import json
import pathlib
import uuid


def msi_version(version_name, version_code):
    # Pre-release suffixes disappear in MSI. VERSION_CODE orders all releases,
    # including alpha/beta/final builds with the same public version number.
    major, minor, _ = version_name.split(".", 2)
    fields = (int(major), int(minor), int(version_code))
    if not all(0 <= value <= limit for value, limit in zip(fields, (255, 255, 65535))):
        raise ValueError("MSI major.minor.VERSION_CODE must fit 255.255.65535")
    return ".".join(map(str, fields))


def literal_strings(value):
    # nFPM expands environment variables after parsing, including in paths.
    if isinstance(value, str):
        return value.replace("$", "$$")
    if isinstance(value, list):
        return [literal_strings(item) for item in value]
    if isinstance(value, dict):
        return {key: literal_strings(item) for key, item in value.items()}
    return value


def make_config(args):
    root = args.rootfs.resolve()
    contents = []
    file_versions = {}
    for source in sorted(root.rglob("*")):
        if source.is_symlink():
            raise ValueError(f"MSI payload cannot contain symlinks: {source}")
        if not source.is_file():
            continue
        destination = "/" + source.relative_to(root).as_posix()
        entry = {"src": str(source), "dst": destination}
        if destination == "/LICENSE":
            entry["type"] = "license"
        contents.append(entry)
        if source.suffix.lower() in (".exe", ".dll"):
            # Major upgrades replace the complete payload. Do not depend on PE
            # version resources being present in the launcher, Go core or JBR.
            file_versions[destination] = ""

    launcher = f"/{args.app_name}.exe"
    registry = []
    for scheme in args.url_schemes:
        key = f"Software\\Classes\\{scheme}"
        for suffix, name, value in (
            ("", "", f"URL:{scheme} Protocol"),
            ("", "URL Protocol", ""),
            ("\\DefaultIcon", "", f"[INSTALLFOLDER]{args.app_name}.exe,0"),
            ("\\shell\\open\\command", "", f'"[INSTALLFOLDER]{args.app_name}.exe" open "%1"'),
        ):
            registry.append({"root": "HKCU", "key": key + suffix, "name": name, "value": value})

    # Thin and JBR packages replace one another; architectures remain separate.
    # This identity does not change if the display name or maintainer changes.
    upgrade_code = uuid.uuid5(uuid.NAMESPACE_DNS, f"{args.package_name}.windows.{args.arch}")
    config = {
        "name": args.package_name,
        "arch": args.arch,
        "platform": "windows",
        "version": args.version_name,
        "version_metadata": args.variant,
        "maintainer": args.maintainer,
        "description": args.description,
        "homepage": args.homepage,
        "license": "GPL-3.0-or-later",
        "mtime": datetime.datetime.fromtimestamp(args.epoch, datetime.timezone.utc).isoformat(),
        "contents": contents,
        "msi": {
            "product_name": args.app_name,
            "manufacturer": args.maintainer,
            "install_dir": args.app_name,
            "per_user": True,
            "minimal_ui": True,
            "version": msi_version(args.version_name, args.version_code),
            "upgrade_code": "{" + str(upgrade_code).upper() + "}",
            "upgrade": {"allow_same_version": True},
            "file_versions": file_versions,
            "shortcuts": [
                {"name": args.app_name, "target": launcher, "directory": directory,
                 "description": args.description, "icon": str(root / launcher.lstrip("/"))}
                for directory in ("ProgramMenuFolder", "DesktopFolder")
            ],
            "registry": registry,
        },
    }
    if args.certificate:
        config["msi"]["signature"] = {
            "pfx_file": str(args.certificate.resolve()),
            "timestamp_url": args.timestamp_url,
        }
    return literal_strings(config)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rootfs", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    for field in ("package-name", "version-name", "version-code", "app-name", "description", "homepage", "maintainer"):
        parser.add_argument(f"--{field}", required=True)
    parser.add_argument("--arch", choices=("amd64", "arm64"), required=True)
    parser.add_argument("--variant", choices=("thin", "jbr"), required=True)
    parser.add_argument("--epoch", type=int, required=True)
    parser.add_argument("--url-schemes", nargs="+", required=True)
    parser.add_argument("--certificate", type=pathlib.Path)
    parser.add_argument("--timestamp-url", default="")
    args = parser.parse_args()
    try:
        config = make_config(args)
    except ValueError as error:
        parser.error(str(error))
    args.output.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
