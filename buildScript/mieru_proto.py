#!/usr/bin/env python3
"""Vendor Mieru's traffic-pattern schema from the pinned plugin submodule."""

from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parent.parent
module = root / "plugin/mieru/src/main/go/mieru"
source = module / "pkg/appctl/proto/base.proto"
names = {
    "TrafficPattern", "TCPFragment", "NoncePattern", "NonceType", "PaddingPattern",
    "LowEntropyPattern", "LowEntropyMode", "LowEntropyMaskRotation",
}
text = source.read_text()
version = subprocess.check_output(["git", "-C", str(module), "rev-parse", "HEAD"], text=True).strip()
blocks = []
for match in re.finditer(r"^(message|enum) (\w+) \{", text, re.MULTILINE):
    kind, name = match.groups()
    if name not in names:
        continue
    depth = 1
    end = match.end()
    while depth:
        if text[end] == "{":
            depth += 1
        elif text[end] == "}":
            depth -= 1
        end += 1
    block = text[match.start():end]
    if kind == "enum":
        block = block.replace("{", '{\n    option (wire.enum_mode) = "sealed_class";', 1)
    blocks.append(block)
if len(blocks) != len(names):
    raise SystemExit("Mieru traffic-pattern schema changed: expected types are missing")
license_header = text[:text.index('syntax =')]
output = root / "proto/mieru/traffic_pattern.proto"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(
    license_header
    + f"// Vendored from mieru {version} by buildScript/mieru_proto.py. Do not edit.\n"
    + 'syntax = "proto3";\n\npackage mieru.appctl;\n\n'
    + 'import "wire/extensions.proto";\n\n'
    + 'option java_package = "fr.husi.proto.mieru";\n\n'
    + "\n\n".join(blocks) + "\n"
)
