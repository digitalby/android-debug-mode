#!/usr/bin/env python3
"""
Generate all raster icon and marketing assets for android-debug-mode from the
adaptive icon vector drawables.

Requirements:
    pip install -r scripts/requirements.txt

macOS prerequisite (if cairosvg reports a missing library):
    brew install cairo
"""

from __future__ import annotations

import io
import xml.etree.ElementTree as ET
from pathlib import Path

import cairosvg
from PIL import Image, ImageDraw, ImageFont

REPO_ROOT = Path(__file__).resolve().parent.parent

DENSITIES: dict[str, int] = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

FOREGROUND_AVD = REPO_ROOT / "app/src/main/res/drawable-v24/ic_launcher_foreground.xml"
BACKGROUND_AVD = REPO_ROOT / "app/src/main/res/drawable/ic_launcher_background.xml"
MIPMAP_BASE = REPO_ROOT / "app/src/main/res"
FASTLANE_IMAGES = REPO_ROOT / "fastlane/metadata/android/en-US/images"

APP_NAME = "Debug Mode Widget"
AVD_NS = "http://schemas.android.com/apk/res/android"


# ---------------------------------------------------------------------------
# AVD → SVG conversion
# ---------------------------------------------------------------------------

def _avd_attr(el: ET.Element, name: str) -> str | None:
    return el.get(f"{{{AVD_NS}}}{name}")


def _group_transform(el: ET.Element) -> str:
    def f(attr: str, default: float = 0.0) -> float:
        v = _avd_attr(el, attr)
        return float(v) if v is not None else default

    tx, ty = f("translateX"), f("translateY")
    sx, sy = f("scaleX", 1.0), f("scaleY", 1.0)
    rot = f("rotation")
    px, py = f("pivotX"), f("pivotY")

    parts: list[str] = []
    if tx or ty:
        parts.append(f"translate({tx},{ty})")
    if rot:
        parts.append(f"rotate({rot},{px},{py})")
    if sx != 1.0 or sy != 1.0:
        parts.append(f"scale({sx},{sy})")
    return " ".join(parts)


def _element_to_svg(el: ET.Element) -> str:
    tag = el.tag.split("}")[-1]
    if tag == "path":
        mapping = [
            ("pathData", "d"),
            ("fillColor", "fill"),
            ("strokeColor", "stroke"),
            ("strokeWidth", "stroke-width"),
            ("fillAlpha", "fill-opacity"),
            ("strokeAlpha", "stroke-opacity"),
        ]
        attrs = {svg: v for avd, svg in mapping if (v := _avd_attr(el, avd)) is not None}
        attr_str = " ".join(f'{k}="{v}"' for k, v in attrs.items())
        return f"<path {attr_str}/>"
    if tag == "group":
        transform = _group_transform(el)
        t_attr = f' transform="{transform}"' if transform else ""
        children = "\n  ".join(_element_to_svg(c) for c in el)
        return f"<g{t_attr}>\n  {children}\n</g>"
    return ""


def _avd_inner_svg(avd_path: Path) -> str:
    """Return SVG fragment (no root element) for the content of an AVD file."""
    root = ET.parse(avd_path).getroot()
    return "\n".join(_element_to_svg(c) for c in root)


def build_composite_svg(size: int) -> bytes:
    """Composite background + foreground into a single SVG at the given pixel size."""
    bg = _avd_inner_svg(BACKGROUND_AVD)
    fg = _avd_inner_svg(FOREGROUND_AVD)
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" '
        f'viewBox="0 0 108 108" width="{size}" height="{size}">\n'
        f"{bg}\n{fg}\n"
        f"</svg>"
    )
    return svg.encode("utf-8")


# ---------------------------------------------------------------------------
# Rendering helpers
# ---------------------------------------------------------------------------

def render_png(svg_bytes: bytes, size: int) -> Image.Image:
    png = cairosvg.svg2png(bytestring=svg_bytes, output_width=size, output_height=size)
    return Image.open(io.BytesIO(png)).convert("RGBA")


def apply_round_mask(img: Image.Image) -> Image.Image:
    s = img.size[0]
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, s - 1, s - 1), fill=255)
    out = img.copy()
    out.putalpha(mask)
    return out


# ---------------------------------------------------------------------------
# Asset generators
# ---------------------------------------------------------------------------

def generate_mipmap_icons() -> None:
    print("Mipmap icons:")
    for density, px in DENSITIES.items():
        out_dir = MIPMAP_BASE / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)

        for webp in out_dir.glob("*.webp"):
            webp.unlink()
            print(f"  deleted {webp.relative_to(REPO_ROOT)}")

        svg_bytes = build_composite_svg(px)
        img = render_png(svg_bytes, px)

        square = out_dir / "ic_launcher.png"
        img.save(square, "PNG")
        print(f"  wrote   {square.relative_to(REPO_ROOT)}")

        round_img = apply_round_mask(img)
        rounded = out_dir / "ic_launcher_round.png"
        round_img.save(rounded, "PNG")
        print(f"  wrote   {rounded.relative_to(REPO_ROOT)}")


def generate_fastlane_icon() -> None:
    print("Fastlane icon:")
    out = FASTLANE_IMAGES / "icon.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img = render_png(build_composite_svg(512), 512)
    img.save(out, "PNG")
    print(f"  wrote   {out.relative_to(REPO_ROOT)}")


def generate_feature_graphic() -> None:
    print("Feature graphic:")
    W, H = 1024, 500
    canvas = Image.new("RGBA", (W, H), "#1565C0")

    icon_size = 220
    icon = render_png(build_composite_svg(icon_size), icon_size)
    icon_x, icon_y = 100, (H - icon_size) // 2
    canvas.alpha_composite(icon, (icon_x, icon_y))

    draw = ImageDraw.Draw(canvas)
    try:
        font_large = ImageFont.load_default(size=56)
    except TypeError:
        font_large = ImageFont.load_default()

    text_x = icon_x + icon_size + 60
    bbox = draw.textbbox((0, 0), APP_NAME, font=font_large)
    text_h = bbox[3] - bbox[1]
    text_y = (H - text_h) // 2
    draw.text((text_x, text_y), APP_NAME, fill="#FFFFFF", font=font_large)

    out = FASTLANE_IMAGES / "featureGraphic.png"
    canvas.convert("RGB").save(out, "PNG")
    print(f"  wrote   {out.relative_to(REPO_ROOT)}")


def generate_screenshot_placeholder() -> None:
    print("Screenshot placeholder:")
    W, H = 1080, 1920
    canvas = Image.new("RGB", (W, H), "#1565C0")
    draw = ImageDraw.Draw(canvas)

    try:
        font = ImageFont.load_default(size=48)
    except TypeError:
        font = ImageFont.load_default()

    text = "Screenshot placeholder"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text(((W - tw) // 2, (H - th) // 2), text, fill="#FFFFFF", font=font)

    out = FASTLANE_IMAGES / "phoneScreenshots" / "01_widget.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out, "PNG")
    print(f"  wrote   {out.relative_to(REPO_ROOT)}")


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    generate_mipmap_icons()
    generate_fastlane_icon()
    generate_feature_graphic()
    generate_screenshot_placeholder()
    print("\nAll assets generated.")
