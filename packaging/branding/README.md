# JetaProg brand assets

The geometric J and code slash carry forward the original application's identity.
The flat charcoal tile and steel-blue mark use the IDE palette: `background`
(`#1A1B1E`), `accent` (`#5B9BD5`), and `editorIdentifier` (`#D7DBE0`). The mark keeps
these colors in both themes; its solid tile provides contrast on light surfaces.

`logo.svg` is the editable vector master. Keep its transparent outer margin for
desktop icons. The slash and J are paths, so the logo needs no installed fonts.

| Asset | Use |
|---|---|
| `logo.svg` | GitHub README and scalable artwork |
| `github-avatar.png` | 512 × 512 opaque avatar, safe for circular cropping |
| `github-social-preview.png` | 1280 × 640 repository social preview |
| `../../app/desktop/src/jvmMain/resources/icon.png` | 1024 × 1024 desktop window, welcome hub, and Linux icon |
| `../../app/desktop/src/jvmMain/resources/icon.ico` | Windows installer icon, 16–256 px representations |
| `../../app/desktop/src/jvmMain/resources/icon.icns` | macOS icon, standard and Retina representations through 1024 px |

Regenerate all raster assets from the repository root:

```bash
python3 tools/export_brand_assets.py
```

The exporter requires Python 3, librsvg (`rsvg-convert`), and ImageMagick (`magick`).
It uses the bundled JetBrains Mono font for the social preview. These are export
tools only; application builds use the checked-in assets.

The README displays the vector logo automatically. The avatar and social preview
are upload-ready files; updating these GitHub account/repository settings is a
separate step from changing the repository files.
