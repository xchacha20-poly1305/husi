#!/usr/bin/env python3

import re
import shutil
import struct
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE_LARGE = ROOT / "art" / "icon.svg"
SOURCE_SMALL = ROOT / "art" / "icon-small.svg"

CANVAS = 135.47
ANDROID_LAYER_DP = 108

BACKGROUND = "#191C15"
MONOCHROME_COLOR = "#000000"
MONOCHROME_STROKE_SCALE = 1.6

ANDROID_MARK_FRACTION = 0.53
PLATED_MARK_FRACTION = 0.58
SMALL_MARK_FRACTION = 0.72
SMALL_ARTWORK_MAX_SIZE = 96

MIN_STROKE_PIXELS = 1.25
STRUCTURAL_STROKE_WIDTH = 1.0

MACOS_PLATE_INSET = 0.0977
MACOS_PLATE_CORNER = 0.225
DESKTOP_PLATE_INSET = 0.04
DESKTOP_PLATE_CORNER = 0.22
LEGACY_PLATE_CORNER = 0.22

MEASURE_RESOLUTION = 2048

ANDROID_MIPMAP_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
STORE_SIZE = 512
LINUX_SIZE = 512
WINDOWS_SIZES = [16, 24, 32, 48, 64, 128, 256]
MACOS_ELEMENTS = {
    "icp4": 16,
    "icp5": 32,
    "ic11": 32,
    "ic12": 64,
    "ic07": 128,
    "ic08": 256,
    "ic13": 256,
    "ic09": 512,
    "ic14": 512,
    "ic10": 1024,
}
ICNS_MAGIC = b"icns"
ICNS_HEADER_SIZE = 8

PATH_PATTERN = re.compile(r"<path\b(.*?)/>", re.DOTALL)
ATTRIBUTE_PATTERN = re.compile(r'([a-zA-Z-]+)\s*=\s*"([^"]*)"')

SVG_TO_ANDROID = {
    "d": "pathData",
    "fill": "fillColor",
    "stroke": "strokeColor",
    "stroke-width": "strokeWidth",
    "stroke-linecap": "strokeLineCap",
    "stroke-linejoin": "strokeLineJoin",
}
TRANSPARENT = "#00000000"


@dataclass(frozen=True)
class Plate:
    fill: str | None = None
    inset: float = 0.0
    corner: float = 0.0


DESKTOP_PLATE = Plate(BACKGROUND, DESKTOP_PLATE_INSET, DESKTOP_PLATE_CORNER)
LEGACY_PLATE = Plate(BACKGROUND, 0.0, LEGACY_PLATE_CORNER)
MACOS_PLATE = Plate(BACKGROUND, MACOS_PLATE_INSET, MACOS_PLATE_CORNER)
STORE_PLATE = Plate(BACKGROUND)


@dataclass(frozen=True)
class Bounds:
    x: float
    y: float
    width: float
    height: float


@dataclass
class Artwork:
    paths: list[dict[str, str]]
    bounds: Bounds


@dataclass
class ArtworkSet:
    large: Artwork
    small: Artwork

    def select(self, size: int) -> tuple[Artwork, float]:
        if size <= SMALL_ARTWORK_MAX_SIZE:
            return self.small, SMALL_MARK_FRACTION
        return self.large, PLATED_MARK_FRACTION


def fail(message: str) -> None:
    print(f"icon: {message}", file=sys.stderr)
    raise SystemExit(1)


def require_tool(name: str) -> str:
    found = shutil.which(name)
    if found is None:
        fail(f"missing required tool: {name}")
    return found


def run(*command: str) -> str:
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        fail(f"{command[0]} failed: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout


def read_paths(source: Path) -> list[dict[str, str]]:
    if not source.is_file():
        fail(f"missing source artwork: {source}")
    document = source.read_text()
    stripped = PATH_PATTERN.sub("", document)
    if "<" in stripped.split(">", 1)[1].rsplit("</svg>", 1)[0]:
        fail(f"{source.name} may only contain <path> elements")
    paths = [dict(ATTRIBUTE_PATTERN.findall(match)) for match in PATH_PATTERN.findall(document)]
    if not paths:
        fail(f"{source.name} has no paths")
    for path in paths:
        unknown = set(path) - set(SVG_TO_ANDROID)
        if unknown:
            fail(f"{source.name} has unsupported path attributes: {', '.join(sorted(unknown))}")
    return paths


def render_paths(paths: list[dict[str, str]]) -> str:
    rendered = []
    for path in paths:
        attributes = " ".join(f'{name}="{value}"' for name, value in path.items())
        rendered.append(f"  <path {attributes}/>")
    return "\n".join(rendered)


def document_for(paths: list[dict[str, str]], body: str = "") -> str:
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {CANVAS} {CANVAS}">\n'
        f"{body}{render_paths(paths)}\n</svg>\n"
    )


def measure(paths: list[dict[str, str]]) -> Bounds:
    magick = require_tool("magick")
    rsvg = require_tool("rsvg-convert")
    with tempfile.TemporaryDirectory() as workspace:
        svg = Path(workspace) / "measure.svg"
        png = Path(workspace) / "measure.png"
        svg.write_text(document_for(paths))
        run(rsvg, "-w", str(MEASURE_RESOLUTION), "-h", str(MEASURE_RESOLUTION), str(svg), "-o", str(png))
        geometry = run(magick, str(png), "-format", "%@", "info:").strip()
    match = re.fullmatch(r"(\d+)x(\d+)\+(\d+)\+(\d+)", geometry)
    if match is None:
        fail(f"could not measure artwork bounds: {geometry}")
    width, height, x, y = (int(value) for value in match.groups())
    unit = CANVAS / MEASURE_RESOLUTION
    return Bounds(x * unit, y * unit, width * unit, height * unit)


def load_artwork(source: Path) -> Artwork:
    paths = read_paths(source)
    return Artwork(paths, measure(paths))


def mark_transform(bounds: Bounds, plate: Plate, fraction: float) -> tuple[float, float, float]:
    region = CANVAS * (1 - 2 * plate.inset)
    scale = region * fraction / max(bounds.width, bounds.height)
    center = CANVAS / 2
    x = center - (bounds.x + bounds.width / 2) * scale
    y = center - (bounds.y + bounds.height / 2) * scale
    return scale, x, y


def compose(artwork: Artwork, plate: Plate, fraction: float) -> str:
    scale, x, y = mark_transform(artwork.bounds, plate, fraction)
    background = ""
    if plate.fill is not None:
        origin = CANVAS * plate.inset
        size = CANVAS * (1 - 2 * plate.inset)
        radius = size * plate.corner
        background = (
            f'  <rect x="{origin}" y="{origin}" width="{size}" height="{size}" '
            f'rx="{radius}" ry="{radius}" fill="{plate.fill}"/>\n'
        )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {CANVAS} {CANVAS}">\n'
        f"{background}"
        f'  <g transform="translate({x},{y}) scale({scale})">\n'
        f"{render_paths(artwork.paths)}\n"
        f"  </g>\n</svg>\n"
    )


def rasterize(document: str, size: int, destination: Path) -> None:
    rsvg = require_tool("rsvg-convert")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as workspace:
        svg = Path(workspace) / "render.svg"
        svg.write_text(document)
        run(rsvg, "-w", str(size), "-h", str(size), str(svg), "-o", str(destination))


def hinted(artwork: Artwork, plate: Plate, fraction: float, size: int) -> Artwork:
    scale, _, _ = mark_transform(artwork.bounds, plate, fraction)
    floor = MIN_STROKE_PIXELS * CANVAS / (size * scale)
    paths = []
    for path in artwork.paths:
        width = path.get("stroke-width")
        if width is not None and float(width) >= STRUCTURAL_STROKE_WIDTH:
            path = dict(path)
            path["stroke-width"] = str(max(float(width), floor))
        paths.append(path)
    return Artwork(paths, artwork.bounds)


def rasterize_plated(artworks: ArtworkSet, plate: Plate, size: int, destination: Path) -> None:
    artwork, fraction = artworks.select(size)
    rasterize(compose(hinted(artwork, plate, fraction, size), plate, fraction), size, destination)


def flatten(source: Path, destination: Path) -> None:
    magick = require_tool("magick")
    run(magick, str(source), "-background", BACKGROUND, "-alpha", "remove", "-alpha", "off", str(destination))


def android_vector(artwork: Artwork, monochrome: bool) -> str:
    scale, x, y = mark_transform(artwork.bounds, Plate(), ANDROID_MARK_FRACTION)
    lines = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{ANDROID_LAYER_DP}dp"',
        f'    android:height="{ANDROID_LAYER_DP}dp"',
        f'    android:viewportWidth="{CANVAS}"',
        f'    android:viewportHeight="{CANVAS}">',
        "  <group",
        f'      android:scaleX="{scale}"',
        f'      android:scaleY="{scale}"',
        f'      android:translateX="{x}"',
        f'      android:translateY="{y}">',
    ]
    for path in artwork.paths:
        lines.append("    <path")
        attributes = []
        for name, value in path.items():
            android_name = SVG_TO_ANDROID[name]
            if name == "stroke-width" and monochrome:
                value = str(float(value) * MONOCHROME_STROKE_SCALE)
            elif name in ("fill", "stroke"):
                if value == "none":
                    value = TRANSPARENT
                elif monochrome:
                    value = MONOCHROME_COLOR
            attributes.append(f'        android:{android_name}="{value}"')
        lines.append("\n".join(attributes) + "/>")
    lines.append("  </group>")
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def write(destination: Path, content: str) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(content)
    print(f"icon: wrote {destination.relative_to(ROOT)}")


def emit_android(artworks: ArtworkSet, workspace: Path) -> None:
    magick = require_tool("magick")
    resources = ROOT / "composeApp" / "src" / "androidMain" / "res"
    write(resources / "drawable" / "ic_launcher_foreground.xml", android_vector(artworks.large, monochrome=False))
    write(resources / "drawable" / "ic_launcher_monochrome.xml", android_vector(artworks.large, monochrome=True))
    write(
        resources / "values" / "ic_launcher_background.xml",
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<resources>\n"
        f'    <color name="ic_launcher_background">{BACKGROUND}</color>\n'
        "</resources>\n",
    )

    for density, size in ANDROID_MIPMAP_SIZES.items():
        png = workspace / f"legacy-{density}.png"
        rasterize_plated(artworks, LEGACY_PLATE, size, png)
        webp = resources / f"mipmap-{density}" / "ic_launcher.webp"
        webp.parent.mkdir(parents=True, exist_ok=True)
        run(magick, str(png), "-define", "webp:lossless=true", str(webp))
        print(f"icon: wrote {webp.relative_to(ROOT)}")

    store_png = workspace / "store.png"
    rasterize_plated(artworks, STORE_PLATE, STORE_SIZE, store_png)
    for destination in (
        ROOT / "composeApp" / "src" / "androidMain" / "ic_launcher-playstore.png",
        ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "icon.png",
    ):
        flatten(store_png, destination)
        print(f"icon: wrote {destination.relative_to(ROOT)}")


def emit_windows(artworks: ArtworkSet, workspace: Path) -> None:
    magick = require_tool("magick")
    rendered = []
    for size in WINDOWS_SIZES:
        target = workspace / f"windows-{size}.png"
        rasterize_plated(artworks, DESKTOP_PLATE, size, target)
        rendered.append(target)
    destination = ROOT / "launcher" / "icon.ico"
    run(magick, *(str(png) for png in rendered), str(destination))
    print(f"icon: wrote {destination.relative_to(ROOT)}")


def emit_macos(artworks: ArtworkSet, workspace: Path) -> None:
    payloads = {}
    for size in sorted(set(MACOS_ELEMENTS.values())):
        target = workspace / f"macos-{size}.png"
        rasterize_plated(artworks, MACOS_PLATE, size, target)
        payloads[size] = target.read_bytes()

    elements = b""
    for element, size in MACOS_ELEMENTS.items():
        payload = payloads[size]
        elements += struct.pack(">4sI", element.encode(), ICNS_HEADER_SIZE + len(payload)) + payload
    archive = struct.pack(">4sI", ICNS_MAGIC, ICNS_HEADER_SIZE + len(elements)) + elements

    destination = ROOT / "release" / "macos" / "desktop" / "icon.icns"
    destination.write_bytes(archive)
    print(f"icon: wrote {destination.relative_to(ROOT)}")


def emit_linux(artworks: ArtworkSet) -> None:
    destination = ROOT / "release" / "linux" / "desktop" / "icon.png"
    rasterize_plated(artworks, DESKTOP_PLATE, LINUX_SIZE, destination)
    print(f"icon: wrote {destination.relative_to(ROOT)}")


def main() -> None:
    artworks = ArtworkSet(load_artwork(SOURCE_LARGE), load_artwork(SOURCE_SMALL))
    with tempfile.TemporaryDirectory() as directory:
        workspace = Path(directory)
        emit_android(artworks, workspace)
        emit_windows(artworks, workspace)
        emit_macos(artworks, workspace)
        emit_linux(artworks)


if __name__ == "__main__":
    main()
