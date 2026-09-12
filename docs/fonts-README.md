# Bundled text fonts

The Text tool ships these fonts **inside the APK** instead of resolving the
device's generic `serif` / `monospace` / `cursive` families. That is intentional:
with an OEM custom font pack active (for example the Motorola Edge 40 Pro's),
every generic family aliases onto the single installed font, so `Serif`,
`Monospace` and `Handwriting` would all rasterise into the same shape. Bundling
the fonts makes the rendered glyphs deterministic across every device.

`fontFamily` wiring mirrors the exported renderer 1:1:

- `BitmapRenderer.typefaceFor` loads `@font/serif`, `@font/monospace`,
  `@font/handwriting` via `ResourcesCompat.getFont` (feature/editor domain).
- The live canvas overlay maps the same three to `FontFamily(...)` resource
  fonts (feature/editor ui `TextOverlayLayer.kt`).
- `DEFAULT` and `SANS_SERIF` intentionally stay on the platform fallback
  (`Typeface.DEFAULT` / `SANS_SERIF`) — Sans Serif IS the default.

## Licences

| Font               | Family/File         | Licence    |
|--------------------|---------------------|------------|
| Noto Serif         | `serif_*`           | SIL OFL 1.1 |
| Droid Sans Mono    | `monospace_regular` | Apache-2.0 |
| Dancing Script     | `handwriting_*`     | SIL OFL 1.1 |

See the OFL/Apache licence texts in-repo or at https://openfontlicense.org and
https://www.apache.org/licenses/LICENSE-2.0.