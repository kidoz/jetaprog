#!/usr/bin/env python3
"""Export desktop, Linux package, and GitHub branding from the vector master.

Requires Python 3, librsvg's rsvg-convert, and ImageMagick's magick.
Run from any directory: python3 tools/export_brand_assets.py
"""
from __future__ import annotations

import shutil
import struct
import subprocess
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
BRANDING = REPO / "packaging/branding"
RESOURCES = REPO / "app/desktop/src/jvmMain/resources"
ARCHLINUX = REPO / "packaging/archlinux"
SOURCE = BRANDING / "logo.svg"
BACKGROUND = "#1A1B1E"  # IntelliJColors.background
TEXT = "#D7DBE0"  # IntelliJColors.editorIdentifier
SECONDARY = "#9D9D9D"  # IntelliJColors.textSecondary
ACCENT = "#5B9BD5"  # IntelliJColors.accent
LIGHT_ACCENT = "#2D7DD2"  # IntelliJLightColors.accent, >= 3.9:1 on light and dark pages


def run(*args: str | Path) -> None:
    """Run an exporter without shell interpolation."""
    subprocess.run([str(arg) for arg in args], check=True)


def main() -> None:
    for command in ("rsvg-convert", "magick"):
        if not shutil.which(command):
            raise SystemExit(f"Missing required tool: {command}")

    with tempfile.TemporaryDirectory(prefix="jetaprog-brand-") as directory:
        temporary = Path(directory)
        images = {}
        for size in (16, 32, 64, 128, 160, 256, 512, 1024):
            output = temporary / f"icon-{size}.png"
            run("rsvg-convert", "-w", str(size), "-h", str(size), "-o", output, SOURCE)
            images[size] = output
        shutil.copyfile(images[1024], RESOURCES / "icon.png")
        # The PKGBUILD installs this into the hicolor theme's 512x512 directory.
        shutil.copyfile(images[512], ARCHLINUX / "jetaprog.png")

        # Modern ICNS PNG representations, including Retina variants.
        entries = (
            (b"icp4", 16), (b"icp5", 32), (b"icp6", 64),
            (b"ic07", 128), (b"ic08", 256), (b"ic09", 512), (b"ic10", 1024),
            (b"ic11", 32), (b"ic12", 64), (b"ic13", 256), (b"ic14", 512),
        )
        chunks = []
        for tag, size in entries:
            data = images[size].read_bytes()
            chunks.append(tag + struct.pack(">I", len(data) + 8) + data)
        payload = b"".join(chunks)
        (RESOURCES / "icon.icns").write_bytes(b"icns" + struct.pack(">I", len(payload) + 8) + payload)
        run("magick", images[1024], "-define", "icon:auto-resize=256,128,64,48,32,16", RESOURCES / "icon.ico")

        # Opaque canvas also works when GitHub crops the avatar to a circle.
        run("magick", images[512], "-background", BACKGROUND, "-alpha", "remove",
            "-alpha", "off", "-strip", BRANDING / "github-avatar.png")
        run("magick", "-size", "1280x640", f"xc:{BACKGROUND}",
            "(", images[512], "-resize", "320x320", ")",
            "-geometry", "+64+160", "-composite",
            "-font", RESOURCES / "fonts/JetBrainsMono-Bold.ttf", "-fill", TEXT,
            "-pointsize", "84", "-annotate", "+424+292", "JetaProg",
            "-font", RESOURCES / "fonts/JetBrainsMono-Regular.ttf", "-fill", SECONDARY,
            "-pointsize", "26", "-annotate", "+428+350", "A workspace for your code.",
            "-fill", ACCENT, "-pointsize", "19",
            "-annotate", "+428+420", "Cross-platform IDE  /  Built-in MCP",
            "-strip", BRANDING / "github-social-preview.png")

        # Compact README wordmark on a transparent canvas. One name color that reads on
        # any page background, since not every Markdown viewer follows the page theme.
        # +size keeps the spacer's -size off the label.
        run("magick", images[160], "(", "-size", "36x1", "xc:none", ")",
            "(", "+size", "-background", "none", "-fill", LIGHT_ACCENT,
            "-font", RESOURCES / "fonts/JetBrainsMono-Bold.ttf", "-pointsize", "112",
            "label:JetaProg", ")",
            "-background", "none", "-gravity", "center", "+append",
            "-strip", BRANDING / "readme-banner.png")

    print("Exported PNG, ICO, ICNS, Arch Linux icon, GitHub avatar, social preview, "
          "and README banner from logo.svg.")


if __name__ == "__main__":
    main()
