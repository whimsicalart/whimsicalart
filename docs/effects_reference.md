# Editor Effects Reference — per-effect catalog & interaction rules

> Living reference for the photo **editor**. It catalogs **every effect** and the
> **business rules** that govern its preview interaction (pan/zoom, the eye /
> visibility toggle, hold-to-compare with the original image) and its place in
> the layer stack. The underlying **reversible, non-destructive, ordered stack**,
> the fold renderer and the shared geometry model (beauty + background) are
> documented in
> `docs/effect_stack.md` — this document builds on that and does not repeat it.

## How effects are organised

Effects are **layers in the ordered, user-reorderable stack** (`effectStack`),
folded left-to-right starting from the pristine original. Their order is what
the user sees in the Layers window and how the final image is produced. See
`docs/effect_stack.md` ("Core model", "Fold renderer") for how this works.

Each effect is one of four **layer kinds**. Every kind is an **independent,
user-reorderable layer** — the kinds only describe *what the effect does*, never
where it must sit or what it "applies on top of":

| Kind | Meaning | Example effects |
|------|---------|-----------------|
| **Geometric** | resamples the image pixels (rotate / flip / restrict to a region) | transform, crop |
| **Photo-look** | deterministic, parameterised colour/pixel pass | brightness, filters, skin denoise, enhance, vignette |
| **Brush / overlay** | painted diff layer or placed overlay | pen, mosaic, blur brush, removal, stickers, text, frame |
| **Face / ML** | geometry-driven beautification | beauty sub-tools |

Panel/control details for each tool are in **User Guide** (`docs/user_guide.md`);
this document focuses on **interaction rules** and **quirks**.

---

## Preview interaction model

**One shared preview viewport** (`ViewportTransform`) holds the
current `canvasZoom` (the user's magnification relative to the fitted image;
1.0 = fitted) + `pan (offsetX/Y)`; the orientation (rotation, flips) is
separate editor state applied to the **live composite only**. The viewport
drives both the live photo and the hold-to-compare overlay, so the original is
revealed at the **same scale and position** — plain and unrotated/unflipped
(see *Hold-to-compare* below) — lining up with the preview the user is looking
at.

> **The viewport is a display-only affordance — it is never the source of a
> committed change.** Pan / zoom / rotation / flips shown in the preview are just
> how the current composite is *viewed* while editing. The committed result of
> every effect is always a **bitmap** with the change drawn inside it
> (`effect_stack.md` — *The one rule that has no exception*). In particular, a
> transform's preview may spin the canvas via `graphicsLayer` for responsiveness,
> but on **commit** it must **emit a new bitmap with the rotation/flip rasterised
> into the pixels** — the committed `StackEffect` is a bitmap-producing render,
> never a "still-rotated display state".

> **The preview base image is always preview-sized.** The image shown on the
> preview canvas uses a **preview-sized base** at all times: for the first tool
> the original is shrunk to the on-screen footprint; for every later tool it is
> the previous tool's committed preview bitmap carried on the layer's parameter.
> The current tool applies its effect on top of this base — either directly on the
> canvas in a lighter manner (GPU colour-matrix, overlay composables) or by
> generating a bitmap from the official fold method (`StackEffect.render`).
> Full-size rendering happens **only when `displayScale` > 1** — i.e. when the
> on-screen pixels outgrow the preview-sized image (`displayScale` =
> `canvasFitScale` × `canvasZoom`; the colloquial "zooming past 100%", a
> debounced background fold that replaces the preview when ready) — or during a
> full-res export (save / merge / share). The preview is never upscaled from a
> full-size bitmap — it is always the small, fast fold result.

### Canvas layout and gesture scope

The editor splits its space between the **canvas** and the **tool controls**,
and the split **switches orientation**: in portrait the controls sit beneath the
canvas, in landscape **they move to the right** while the canvas keeps its
foot. The layout is chosen from the **available window size**, never from the
device's reported orientation: the area is **landscape** when
`availableWidth > availableHeight`, otherwise **portrait** — so split-screen,
multi-window, free-form and foldable windows all resolve correctly and the
layout flips live as the user resizes the window.

Because a window can be much smaller than the device (e.g. half of a
split-screen phone), **the whole app scales its UI chrome** so tool controls
and app bars never take over a cramped window. The scale is provided **once at
the app root** by a `LocalUiScale` composition local, so every screen — editor,
gallery, settings, camera, collage, onboarding — and every dialog/popup they
open inherits the same factor. It is anchored to a fixed **minimum workable
window**
rather than the physical device (so the same rule holds on any device and in
multi-window). The minimum is the smallest window where the editor fits
**at scale 1.0 with a ~320 dp square letterbox** — and because the chrome
differs per orientation, the anchor is **orientation-aware**:

- **Portrait**: chrome = 64 top bar + 56 eye row + 200 controls reserve = 320
  dp, plus a square letterbox whose side equals the width. With width 320 the
  minimum is `320 wide × (320 + 320) = 640 tall`:
  `uiScale = min(winWidth / 320.dp, winHeight / 640.dp).coerceIn(0.6f, 1.0f)`.
- **Landscape**: chrome = 600 dp controls panel + 120 dp (64 top bar + 56 eye
  row), plus a square letterbox. A 432 dp window height leaves a `432 − 120 =
  312` dp letterbox, so the minimum is `912 wide (600 + 312) × 432 tall`:
  `uiScale = min(winHeight / 432.dp, winWidth / 912.dp).coerceIn(0.6f, 1.0f)`.

where `winWidth/winHeight` are the real available window sizes in dp (the same
`min` over both axes, so a split — which halves **one** axis — always trips the
scale: on a 437×972 dp device a half-height split gives `min(437/320, 486/640)
≈ 0.76`, a half-width split `min(218.5/320, 972/640) ≈ 0.60`). Full-screen
windows meeting both anchors keep `uiScale = 1.0`; a window smaller than the
anchors in either axis ramps smoothly down to a floor of `0.6` so Material
touch targets stay usable.
The scale applies to the **UI chrome across the whole app** — every screen's
top bar, the editor's bottom tool bar, tool controls panel and eye row, and all
dialogs/pickers and their icons, paddings and fonts — **and to the two control
reserves**; the **canvas itself is not scaled down** (see the letterbox rules
below), and the preview-resolution ceiling stays on device pixels. The scale is
realized by multiplying each chrome surface's dp values by
`LocalUiScale.current` — never by rewriting the density, which would also
rescales the gesture/zoom/overlay pixel math and desync the preview↔canvas
mapping.

**Portrait (the visible area is taller than wide).** The canvas sits on top,
with the **eye row**, the **bottom tool bar** and the **tool controls** beneath
it:

- **The canvas is a letterboxed area.** It spans the full available width and its
  height is `min(available width, available chrome height − controls height)`, where
  `available chrome height = available height − eye row` (the space below the app top bar minus
  the fixed-height eye row under the canvas, `56 dp × uiScale`). Neither the eye row nor the
  controls ever come out of the canvas' budget. The canvas is a perfect square **only in the
  exact case where the width is the binding constraint**
  (`availableWidth ≤ availableHeight − 56 dp × uiScale − controlsHeight`, typical portrait); when
  the reduced remaining height binds instead the canvas is a **wide letterbox**. The image
  is letterboxed (fit) inside that area, whatever its shape.
- **The bottom tool bar + tool controls keep a `200 dp × uiScale` minimum height and never
  exceed 30% of the available chrome height.** The controls' height is their natural content height
  clamped into `[200 dp × uiScale, 30% × (available height − eye row)]`. The canvas takes the
  remaining height up to its **square cap** (the available width); when the space left over after
  the tools' minimum exceeds that cap, the canvas saturates at the cap and the **surplus is
  handed to the controls**, which grow to absorb it — **but never past their 30% cap**: beyond
  that the extra space stays with the canvas letterbox (its height is never shrunk below the
  square cap by the controls).
- **The eye row stays between the canvas and the tool bar.** The inspect label
  and its **eye button** sit directly under the canvas, above the bottom tool
  bar and the tool controls.
- **The controls take the remaining space, and they scroll.** The control area
  is never resized to fit its content: when the active tool's controls need more
  vertical space than the remaining height, the controls become **vertically
  scrollable** (dragging them up/down scrolls — the drag is *not* forwarded to
  the canvas). Text and sticker panels are the tallest and are the ones that
  regularly scroll.
- **Every tool's controls sit below the bottom tool bar, in the same scrollable
  panel.** The active tool's controls — including **Crop's aspect-ratio
  selector** and **Transform's rotate/flip row** — appear in the same controls
  panel beneath the bottom tool bar as every other tool, not on separate rows
  above it. The only part of a tool that stays overlaid on the canvas is its
  interactive selection geometry (e.g. the crop selection grid).

**Landscape (the visible area is wider than tall).** The **bottom tool bar and
the tool controls move to a right-side panel**; the **eye row stays below the
canvas**:

- **The canvas is a letterboxed area on the left.** It fills the height from the
  app top bar down to the eye row, and its width is
  `min(available height − 56 dp × uiScale, visible app width − panel width)` — the mirror of
  the portrait rule (the `56 dp × uiScale` term is the eye row, so the square cap equals the
  height the canvas already occupies — the space between the top bar and the eye row). The
  **right controls panel** therefore always keeps at least **`600 dp × uiScale`** of width and
  never exceeds **70% of the visible window width** (the width is shared only between the panel
  and the canvas letterbox, so the panel's available width is the whole window width): the
  panel's width is its natural content width clamped into `[600 dp × uiScale, 70% × visible
  app width]`. The canvas takes the remaining width up to that square cap, and when the
  remaining width exceeds the cap the canvas stays at the cap and the panel **grows to absorb
  the surplus** — **but never past its 70% cap**: beyond that the extra space stays with the
  canvas letterbox (its width is never shrunk below the square cap by the panel). The image is
  letterboxed (fit) inside that area, whatever its shape.
- **The eye row sits below the canvas, exactly as in portrait** — the inspect
  label and its **eye button** hang under the left canvas area, never under the
  controls panel.
- **The right panel holds the bottom tool bar (at its top) and the scrollable
  tool controls beneath it** — the same order, order and stacking as the
  portrait column. The controls scroll when they exceed the panel's height.

In both orientations, **every gesture is scoped to the canvas only — controls
never leak touch.** Pan/zoom (Category A), hold-to-compare, and every Category B
tool gesture (brushing, crop frame drag, sticker/text move/pinch) work **only
while the finger is over the canvas**. Touching the controls area scrolls the
controls; it never pans, zooms, compares, or paints.

### Two preview/compare categories

Every tool is one of exactly **two** categories. Every tool has an **eye
button**. A tool's pan/zoom, hold-to-compare and tail-folding behaviour derive
from its category:

**Category A — pan/zoom-capable (default):** every effect *except* the tools
listed in Category B — adjustments, filters, enhance, style, and all beauty
sub-tools except the beauty pen (crop, frames, stickers, text, transform and
the brush tools are Category B — see below). **Background is a hybrid**: it
behaves as Category A in **Blur** mode (and in **Replace** mode while no
placement background image is selected) and switches to **Category B placement
behaviour** the moment a replacement background image is selected — see
*Background* below.
- **Pan / zoom**: always available by default — the shared
  `detectTransformGestures` on the photo viewport is always active (two-finger
  pinch to zoom, clamped `0.5×–5×`; one-finger drag to pan).
- **Hold-to-compare**: always — holding shows the **plain original** (no effect
  applied, not transformed, not cropped) at the current viewport position/scale.
- **Eye button**: always present. **On by default** when the tool is selected.
  The eye controls **only whether the tail effects are folded into the
  preview**:
  - **Eye ON (default):** the canvas shows the **full composite** — prefix +
    current effect + all tail effects. This is the standard editing view.
  - **Eye OFF:** the canvas shows only the **prefix + current effect** (no tail
    folded). This lets the user see the isolated contribution of the current
    effect and everything before it, without the later effects.
  In both states **all controls remain fully enabled** — the user can change
  parameters, reorder, etc. regardless of the eye state. Pan/zoom and
  hold-to-compare are always available regardless of eye state.

**Category B — non-pan/zoom-capable:** the tools that draw, drag or place with a
single-finger gesture, so in their **normal editing state** the single-finger
gesture is owned by the tool (brush / drag the crop frame / place the overlay)
rather than panning. This set is exactly the non-pan/zoom-capable tools:
**crop, frames, stickers, text, transform**, and **all brush tools** (pen,
mosaic, blur brush, object removal). **Transform belongs here**: its two-finger
twist gesture rotates the photo, and the free-rotate slider mirrors it live with
feedback (wrapped at 360°), so its canvas gesture is tool-owned like the others
— it is **not** pan/zoom-capable (see *Transform* below).
- **Normal editing state**: the canvas **holds still** (the preview is shown at
  the default position and scale; stale pan/zoom is **not** carried over — the
  viewport is reset so the image fits).
- **Eye button**: always present. **Off by default** when the tool is selected.
  The eye controls **whether the tail effects are folded into the preview** AND
  (unlike Category A) also controls **pan/zoom and hold-to-compare**:
  - **Eye OFF (default):** the canvas shows only the **current effect based on
    the previous image** (no tail folded). The user can see the isolated
    contribution of just the current effect. Pan/zoom and hold-to-compare are
    **not available** (single-finger gesture is owned by the tool's own action).
  - **Eye ON:** the canvas shows the **full composite** — prefix + current
    effect + all tail effects. The user can pan/zoom and use hold-to-compare.
    While the eye is enabled the tool's own controls/gestures are **suspended**
    so the user can pan/zoom and compare unobstructed.
  Disabling the eye returns to the tool's normal editing state.
- **Hold-to-compare**: **not available** in the normal editing state (a
  press-and-hold there reads as the tool's own gesture — brushing, dragging the
  crop frame, moving a sticker — and would hide the in-progress work). It is
  available **only while the eye button is enabled**; holding then reveals the
  plain `originalImage` at the current viewport position/scale.
- `originalImage` is **never discarded** (including after a merge), so
  hold-to-compare always works once the eye is on.

> **The one quirk (crop only).** Disabling the crop **eye** button **resets**
> the viewport to the original position and scale. Every other Category B tool
> **keeps** the changed pan/scale when its eye is disabled. Leaving a tool that
> has an eye button resets the eye itself to its default state (on for Category
> A, off for Category B).

---

## Effect catalog

> Layer labels are what appear in the Layers window; these may differ from the
> toolbar chip label (noted per effect).

### Geometric — transform and crop

#### Crop
- **Layer key** `layer:CROP` · **Label** "Crop".
- **Controls**: aspect-ratio selector (Free, 1:1, 4:3, 3:2, 16:9, 9:16) + drag the
  crop rectangle (edges / corners), plus **pinch to scale** the selection
  rectangle. **A selected ratio constrains the crop frame exactly**: choosing a
  ratio reshapes the frame so its width/height match that ratio precisely
  (1:1 → an exact square, 4:3 → exactly 4:3, 3:2 → exactly 3:2, 16:9 / 9:16
  likewise, oriented to the frame) and **keeps** the frame locked to that ratio
  for the whole time it stays selected — dragging a corner, edge, or pinching
  rescales the frame but **always preserves the locked ratio** (never distorts
  it). Reshaping **keeps the frame's scale**: switching ratios re-proportions the
  current frame to the new ratio at the **same area** — shrinking/growing only
  when the new ratio cannot fit the image bounds — so alternating between two
  fixed ratios shows the **same apparent size each time** instead of shrinking
  progressively (the first selection from Free fills the image).
- **Resize grab area**: a touch grabs a frame edge/corner to resize it when it is
  within the frame's edge band — **20% of the frame's own dimension inside** the
  edge (a corner region, inside on both axes, resizes on both) plus a small fixed
  tolerance outside the frame. Crop resizing is **free aspect ratio**: dragging a
  corner stretches both dimensions independently (unless an aspect ratio is
  selected).
- **Eye button**: standard Category B behaviour (see *Two preview/compare
  categories*); the crop-only quirk applies: disabling the crop eye **resets**
  the viewport to the original position and scale (see the *one quirk* note
  above).
- **Hold-to-compare**: the standard Category B behaviour — not available in the
  normal editing state (a press-and-hold there reads as a frame drag/resize; the
  drag is owned by the crop frame), available only while the eye is enabled,
  revealing the plain (uncropped, unoriented) original.
- **Quirks**: an ordinary layer, not a "base step" — like every effect it
  commits by resampling the running composite to the selected region, and
  whatever sits after it in the user's stack order consumes that result.
  A re-entered crop is always normalised against the **current base image** —
  the fold base at the crop's own position. **After Merge Layers the base is the
  merged bitmap** (`firstFull`/`firstPreview` are repointed to the merged
  result), so a subsequent crop is sized to the merged dimensions — never the
  pre-merge original. Cropping is a one-shot commit (records undo history) and
  marks the shared
  geometry (beauty + background) **stale**.

#### Transform (rotate / flip / free rotate)
- **Layer key** `layer:TRANSFORM` · **Label** "Transform".
- **Controls**: Rotate CCW / Rotate CW (90° shortcuts), Flip H / Flip V, plus a
  **free rotate** slider (`0°–360°`) with CW/CCW buttons that bump the slider by
  90° (wrapping: 360→90→0→270→…). The H/V flip + CW/CCW buttons sit on a control
  line slightly **below** the main toolbar.
- **Gesture**: a **two-finger twist** on the canvas **rotates the photo** — the
  free-rotate slider **mirrors the live angle with feedback** and **wraps at
  360°** (twisting past 360° wraps back to 0 and continues), exactly as *Shared
  placement-overlay gestures* and the crop/sticker/text rot handle do. The twist
  is the tool's own canvas gesture, so it is active in the tool's normal
  editing state (eye OFF).
- **Eye button**: **Category B behaviour** (see *Two preview/compare categories*)
  — **off by default** when Transform is selected, the canvas **holds still** at
  the default fit on entry (stale pan/zoom is not carried over), and disabling
  the eye after use **resets the viewport**. While the eye is ON the canvas
  restores pan/zoom and the twist is suspended (the user inspects/compares);
  switching it back OFF returns to the tool's own twist editing state.
- **Hold-to-compare**: available **only while the eye button is enabled** —
  holding then reveals the plain `originalImage` at the current viewport
  position/scale (the in-progress transform is what is shown while not held).
- **Order of operations**: the **flip is applied before the rotation**. The
  combined transform maps each pixel as **flip first, then rotate** — mirror
  the image on the current axes, then turn the mirrored result. So a
  Flip-H + 90° rotate produces a mirror-then-rotate output (not the same image
  as rotate-then-flip). The live preview applies the same order
  (`graphicsLayer` scales the canvas before rotating), and commit rasterises
  the flip-then-rotate result into the bitmap.
- **Quirks**: any **non-multiple-of-90°** free-rotate angle makes the canvas
  **grow to fit** the rotated image. The background is **transparent by
  default**; when saved to **JPEG** the background becomes **white**. On commit
  the transform emits a **new rotated canvas** — the rotated/flipped pixels
  become part of the running composite. It is an independent layer like any
  other: whether a **Frame** placed later draws square around the rotated
  canvas, or one placed earlier rotates together with the image, is decided
  purely by stack order. Rotate/flip mark the shared geometry (beauty +
  background) **stale**.

---

### Photo-look — adjustments

All are deterministic parameterised passes; each is its own layer the user can
reorder and re-edit. All are **pan/zoom-capable (Category A)**, have an **eye
button (on by default)** that controls only tail folding, and support
**hold-to-compare with the plain original**.

| Tool | Layer key | Range | Applies as |
|------|-----------|-------|------------|
| Skin Denoise | `layer:SKIN_DENOISE` | `0..100%` | wavelet denoise (softness = 1 − intensity) |
| Brightness | `layer:BRIGHTNESS` | `-100..100` | colour matrix |
| Contrast | `layer:CONTRAST` | `-100..100` | colour matrix |
| Saturation | `layer:SATURATION` | `-100..100` | colour matrix |
| Sharpness | `layer:SHARPEN` | `0..100` | pixel sharpen pass |
| Exposure | `layer:EXPOSURE` | `-100..100` | colour matrix |
| Shadows | `layer:SHADOWS` | `-100..100` | colour matrix |
| Highlights | `layer:HIGHLIGHTS` | `-100..100` | colour matrix |
| Temperature | `layer:TEMPERATURE` | `-100..100` | colour matrix |
| Tint | `layer:TINT` | `-100..100` | colour matrix |
| Vignette | `layer:VIGNETTE` | `0..100` | radial darkening overlay |

**Layer labels** (Layers window): Brightness, Contrast, Saturation, **Sharpness**
(≠ toolbar "Sharpen"), Exposure, Shadows, Highlights, **Warmth** (≠ toolbar
"Temperature"), Tint, Vignette, **Skin Denoise** (≠ toolbar "Denoise").

**Quirks:**
- **Skin Denoise** is a first-class toolbar tool, *not* a beauty sub-tool; its
  slider shows **0–100%** (the underlying value stays `0..1`; a low value keeps
  detail, high value denoises more).
- **Vignette is a photo-look layer** (parameterised radial overlay), not a fixed
  border — it folds in stack order like every other adjustment.

---

### Photo-look — filters, enhance, style

All **pan/zoom-capable (Category A)**, **eye button (on by default)** that
controls only tail folding, **hold-to-compare** supported. **Exception:
Background** is a hybrid (see its section below).

#### Filters
- **Layer key** `layer:FILTER` (color/style/lens).
- **Filters is a single effect** that carries the color, style, and lens options
  as controls of that one layer.
- **Controls**: Color filters (mutually exclusive), Style filters (mutually
  exclusive with color), Lens filters (multi-select).
- **Lens filters split into two kinds**:
  - *Matrix lens filters* (Clear, UV, polarizers, ND, infrared, soft, bokeh):
    their color matrices combine into the same filter layer's matrix pass.
  - *Real-pass lens filters* (**red-eye**, **star**, **anamorphic**, **astro**):
    rendered as per-pixel CPU passes inside the renderer (see
    `LensFilter`/`LensFilterProcessor`). They are **excluded from the combined
    color matrix** (no double application) and, because they cannot be expressed
    as a draw-time `ColorFilter`, they surface through the composite preview
    exactly like style filters — toggling one re-fires the composite fold.
- **Red-eye** removes flash-lit red pupils (heuristic eye-mask desaturation).

#### Auto-Enhance
- **Layer key** `layer:ENHANCE` · **Label** "Auto-Enhance" (toolbar: "Enhance");
  on/off toggle; deterministic enhance pass.

#### Background
- **Layer key** `layer:BACKGROUND` · **Label** "Background".
- **Controls**: auto-detect subject (runs on open), **Blur / Replace** modes,
  blur intensity + bokeh shape (Blur mode), or a preset/gallery **placement
  background** image (Replace mode).
- **The subject mask is geometry** and follows the same geometry-track process as
  the beauty tools (`docs/effect_stack.md`, *Shared geometry context (beauty +
  background)* and *Lazy beauty geometry*): a **provisional preview-resolution
  mask is resolved first and used immediately** — Blur / Replace reacts instantly
  on entry — the **real mask is resolved in the background at the geometry-track
  resolution (the adaptive 1–4 MP budget, never the full-size photo)** and
  swapped in, and **commit blocks until the real mask is ready** — the mask
  evaluation has the **same two initiations and the same merge** as the beauty
  geometry (`docs/effect_stack.md`, *Lazy beauty geometry* step 4): the
  `displayScale` > 1 background full-size fold (colloquially zooming past
  100%), or the commit itself, whichever
  starts first; the shared context serves one result per segment × resolution,
  so the two never run the same evaluation twice.
- **Blur intensity is a `0..100` percentage slider** where **`0` applies no
  blur** — the photo stays sharp, nothing is defocused — and the defocus **grows
  smoothly with the slide** up to the maximum of **`100` ≈ `0.20 × canvas
  width`** (a full-strength but never extreme look). The blur is applied only
  outside the subject mask.
- **Soft subject edge (Blur and Replace).** The subject boundary is never a
  hard cut: for a **few pixels inward** from the mask path the background — the
  blurred copy in Blur mode or the replacement backdrop in Replace mode —
  **fades softly into the subject edge**, so the composite looks integrated.
  The feathered band is intentionally thin (a few pixels), extending the
  auto-detect mask's soft edge rather than re-segmenting it.
- **Placement parameters** (Replace mode): the placement background's `scale`
  (default `1.0` = its fit size), `pan` (`offsetX/offsetY`),
  `rotationDegrees`, and the `freeScaleUp` switch.
- **Re-entering Replace restores the last placement.** The first time the user
  selects **Replace** with no placement background chosen, the **first preset**
  is selected automatically, so the tool enters the placement state immediately.
  Once a background has been chosen (preset or gallery), selecting **Replace**
  always restores **that same background** and its placement values — the tool
  never swaps it silently.
- **Switching the background image keeps the placement.** Picking a **different**
  preset or gallery image while a replacement background is already placed does
  **not** reset the grid: the current `scale`, `pan` and `rotationDegrees` (and
  the `freeScaleUp` switch) carry over unchanged to the new image, so the user's
  composition stays exactly where they put it. Only **Clear** resets the
  placement.
- **A hybrid Category A / Category B tool** — the interaction category depends on
  the state, not on the layer kind:
  - **Blur mode — Category A.** Eye button **on by default**; it controls only
    tail folding; pan/zoom and hold-to-compare are always available. All blur
    controls stay enabled regardless of eye state.
  - **Replace mode, no placement image selected — Category A.** The eye stays
    **on** (the user can still preview the composite or fall back to Blur); the
    placement controls are disabled and no grid is shown.
  - **Replace mode, a placement image selected — Category B (placement state).**
    Selecting a placement background **switches the eye OFF**, **resets** the
    preview viewport (the canvas holds still at the default fit), and shows a
    **proportional placement grid** that frames the **actual placement image**:
    the replacement background is drawn **behind the photo**, and the subject
    (the photo with its removed background) stays on top **inside the grid** —
    the grid bounds always equal the image's true scaled/panned/rotated bounds,
    so the composite follows the grid exactly and never drifts from it:
    - **The grid uses the same selection grab area as Crop's selection grid (and
      stickers/text):** each corner and edge carries a resize grab band running
      from the **extended selection edge** inward to a line **20% of the image's
      own dimension inside its real bounds** (the same inner fraction —
      `OVERLAY_RESIZE_INSIDE_FRACTION = 0.2f` — as Crop's frame). Dragging inside
      a band **resizes the placement proportionally** (uniform scale from the
      opposite corner — never free-form distortion), and the **Size slider
      mirrors the live scale** (feedback to the slider, shown as `%` of fit
      size). Touches deeper inside the grid (past the 20% line) **pan** it
      instead.
    - **The gestures belong to the grid**: a single-finger drag **pans** it
      (inside the grid), a corner/edge band drag **resizes** it (as above), a
      two-finger pinch **scales** it **proportionally** too, and the pinch's
      twist **rotates** it — stabilised and wrapped at 360° exactly as *Shared
      placement-overlay gestures* describe. The **rotate slider** mirrors the
      wrapped twist, and the **Size slider** mirrors the grid scale.
    - **Scale is locked to 100% and below by default**: the placement can only be
      reduced below its fit size, down to `0.2×`, unless the **"Free scale up"
      switch** is turned on. The switch **starts off**; turning it **on unlocks
      grid sizes above 100%**, up to **`2×` / 200%** (a faithful replacement
      hides under the subject-sized gap, but for subjects whose cut-out is
      smaller than the frame the user may want the backdrop enlarged — guarded by
      that switch). With the switch off, a drag / pinch / slider trying to exceed
      100% is clamped to 100%.
    - **Scaling never crops.** Enlarging the placement beyond the current canvas
      never crops the overflow: the committed bitmap **grows to fit** the scaled
      placement (the same canvas-growth rule as Transform's free rotate), so the
      full scaled-up placement is emitted with the — now relatively smaller —
      canvas region. The user trims the final framing with
      **Crop** afterwards. Scale-up resampling uses **lohalo interpolation**
      (high-order, ringing-free Lanczos-class resampling).
    - **Eye button (Category B behaviour)**: Eye OFF (the default placement
      state) → the canvas shows only the current effect based on the previous
      image (no tail folded); the placement grid and controls are visible and
      active; pan/zoom and hold-to-compare are **not** available. Eye ON → the
      canvas shows the **full composite** (prefix + background + all tail
      effects); the placement **grid is hidden** and the placement
      controls/gestures are **suspended**; pan/zoom and hold-to-compare are
      enabled. Disabling the eye returns to the placement state (grid and
      controls back; the canvas holds still at the default fit).
  - **Leaving the tool** resets the eye to its per-state default (on in Blur /
    no-image states, off in the placement state).
  - **Switching Replace → Blur is non-destructive on the canvas.** Selecting
    **Blur** removes the replacement backdrop (the canvas returns to the
    subject image's **pan / zoom / rotation as if the replacement had never been
    applied**), but the placement **control values are preserved** — the chosen
    background image, `scale`, `pan`, `rotationDegrees` and `freeScaleUp` all
    stay stored. Selecting **Replace** again therefore shows the placement in
    exactly the state it was in.
- **Quirks**: the placement grid is a **control affordance, never the source of
  a committed crop** — on commit the effect **removes the photo's background
  completely along the subject path** (an inverted mask over the subject
  silhouette) and emits a bitmap of the subject (from the auto-detect mask,
  feathered) composited **over the placement background** transformed by the
  recorded `scale` / `pan` / `rotationDegrees`, so the backdrop is drawn
  **behind** the subject with a soft few-pixel fade at the boundary. A placement
  scaled **down** below its fit size intentionally leaves part of the canvas
  uncovered (transparent, white on JPEG); the grid makes the boundary visible
  and the user crops that region away afterwards with Crop.
  Because the placement can **move, scale and rotate the cut-out subject**, a
  REPLACE layer with a placement image selected is a **deforming task**
  (`docs/effect_stack.md`, *Which effects change geometry*) — re-entering
  Replace and changing the placement marks the shared geometry context stale,
  exactly like re-entering Transform.

---

### Beauty — face / ML sub-tools

Beauty is **individual, reorderable layers** (one per sub-tool), folded with the
shared geometry context. See `docs/effect_stack.md`
("Beauty — per-sub-tool layers + geometry"). Except for the **beauty pen**, all
sub-tools are **pan/zoom-capable (Category A)**, with an **eye button (on by
default)** that controls only tail folding and **hold-to-compare** supported.
The **beauty pen** is a **brush/stroke tool (Category B)**, exactly like the
standalone Pen: one-finger drags **paint** a stroke (never pan); the eye (off by
default) suspends painting and enables pan/zoom and hold-to-compare;
hold-to-compare is **not** available while actually painting.

Each sub-tool's **layer key**, **Layers-window label**, and **controls**:

| Sub-tool | Layer key | Label | Controls |
|----------|-----------|-------|----------|
| Auto | `beauty:auto` | Beauty · Auto Beautify | slider 0..1 |
| Smoothing | `beauty:smoothing` | Beauty · Smoothing | slider 0..1 |
| Teeth | `beauty:teeth` | Beauty · Teeth | slider 0..1 |
| Eye brighten | `beauty:eye_brighten` | Beauty · Eye Brightening | slider 0..1 |
| Dark circles | `beauty:dark_circles` | Beauty · Dark Circles | slider 0..1 |
| Spots | `beauty:spots` | Beauty · Spots | slider 0..1 |
| Wrinkles | `beauty:wrinkles` | Beauty · Wrinkles | slider 0..1 |
| Skin tone | `beauty:skin_tone` | Beauty · Skin Tone | slider −1..1 |
| Face slim | `beauty:slim` | Beauty · Slim | slider −1..1 |
| Eye enlarge | `beauty:eye_enlarge` | Beauty · Eye Enlarge | slider −1..1 |
| Nose | `beauty:nose` | Beauty · Nose | slider −1..1 |
| Jaw | `beauty:jaw` | Beauty · Jaw | slider −1..1 |
| Lipstick | `beauty:lipstick` | Beauty · Lipstick | color picker + slider |
| Blush | `beauty:blush` | Beauty · Blush | color picker + slider |
| Eye shadow | `beauty:eye_shadow` | Beauty · Eye Shadow | color picker + slider |
| Eyeliner | `beauty:eyeliner` | Beauty · Eyeliner | color picker + slider |
| Foundation | `beauty:foundation` | Beauty · Foundation | color picker + slider |
| Hair color | `beauty:hair` | Beauty · Hair Color | color picker + slider |
| Brightness pen | `beauty:pen` | Beauty · Pen | colour picker + brush size + opacity + undo/clear |

**Quirks:**
- **Makeup and pen colours come from the shared colour picker but are carried per
  sub-tool.** The six makeup sub-tools (Lips, Blush, Eye Sh., Liner, Base, Hair)
  and the **beauty pen** all present the **same compact colour row** (see *Shared
  business rules* → *Colour picker*), offering the **curated beauty preset set**
  (the makeup shades plus black, with the rainbow circle opening the full
  picker). Sharing the *row* only means the **same set of offered colours** is
  reachable for every makeup tool and the pen — it does **not** mean the tools
  share one current selection. **Each colour-bearing sub-tool keeps its own
  dedicated colour parameter** (a distinct `color` on its own layer / a distinct
  field in its own UI state): picking a colour in one beauty tool must **never**
  change the selected colour of any other beauty tool (or of a previously
  committed layer of a different sub-tool). Each sub-tool's last-picked colour is
  remembered for that sub-tool alone and restored when it is re-entered.
- Face reshape is a **geometry-changing** effect — it marks the shared geometry
  context stale so a later beauty layer regenerates against the reshaped image
  (see `docs/effect_stack.md`, *Which effects change geometry*).
- A beauty layer's **ML geometry** (face landmarks / hair mask) is always
  derived from an image grounded in the **pristine original** (so face
  landmarks stay stable regardless of colour edits), via the geometry model in
  `docs/effect_stack.md` (*Original geometry image and original geometry*); the
  layer's **pixels** apply onto the running composite at the layer's stack
  position exactly like any other effect. Fields are resolved **per-field on
  demand** — only the ML stages a tool consumes run for a segment
  (`docs/effect_stack.md`, *Per-field resolution*).
- The **ML input is device-adaptive** (`docs/effect_stack.md`, *Adaptive ML
  input resolution*): the geometry resolver never feeds the raw photo to the
  pipeline. It budgets a bounded fraction of the **device's heap** for the
  pipeline's transient working set (the segmenters emit confidence masks **at
  input size**) and picks the largest input whose **pixel count** (`width ×
  height` — the honest unit of compute/memory, which scale with area) fits,
  clamped between a **minimum** (≈ 1 MP — the quality floor, so images at or
  below it are never downscaled) and a **maximum** (≈ 4 MP — the CPU ceiling,
  so huge photos cannot blow the pipeline's time). The cap is re-derived on
  every resolve, and the reference footprint is the geometry track's
  **prefix-fold pixel count** — computed from the effects' pure dimension
  estimators, the same approach the crop tool uses to know the final dimensions —
  so the desired size is recomputed whenever a size-changing effect (crop,
  transform with rotation, background replace) changes the base's final
  footprint, and again whenever a merge re-grounds the original geometry image.
  The geometry itself **resolves preview-size first — the provisional result is
  used immediately — and then at the geometry-track 1–4 MP cap in the
  background**; it is never resolved against the full-size photo
  (`docs/effect_stack.md`, *Lazy beauty geometry*).
  All returned masks/landmark coordinates are scaled back to the base's pixel
  space, so a resolution-capped resolve is pixel-identical to a run at the full
  size.

---

### Brush / removal tools

These are **normalized-stroke layers** (see `docs/effect_stack.md`, "Brush /
pixel tools — normalized stroke layers"). Like every other **Category B**
(non-pan/zoom-capable) tool, each has an **eye toggle (off by default)**: in the
normal state the canvas holds still and a one-finger drag paints; with the **eye
enabled** the canvas gains **both** pan/zoom **and** hold-to-compare; brushing
is **suspended** while the eye is on.

| Tool | Layer key | Label | Brush/controls |
|------|-----------|-------|----------------|
| Pen | `layer:PEN` | Pen | pen type, colour picker + opacity, size + hardness, multi-layer + undo/redo |
| Mosaic | `layer:MOSAIC` | Mosaic | size, opacity, "suggest faces" (one privacy effect: pixelation) |
| Blur Brush | `layer:BLUR_BRUSH` | Blur Brush | size, strength, eraser |
| Object Removal | `layer:OBJECT_REMOVAL` | Object Removal (toolbar: "Remove Object") | size, eraser |

**Live-applied brush effects (Object Removal, Blur Brush, Mosaic):** these three
brush tools rasterise a **real** effect into the running composite, and that real
effect is expensive enough that it is never recomputed on every stroke overlap.
While stroking, the brush shows a **simple stroke/selection overlay** as the preview
(removal shows the marked area; blur and mosaic show the painted region as their
committed effect will cover it); the **actual effect** is applied to the canvas
preview only after the user **stops stroking** — debounced **half a second** after
either the finger is released **or** the stylus/finger stays still for half a second.
As soon as that quiet interval passes, the live preview updates to show the **real**
effect for the strokes drawn so far — for object removal the content-aware fill, for
the blur brush the actual gaussian-style blur of the painted patches, for mosaic the
pixelated/privacy pattern itself — so the user never has to wait for commit to see
what the tool actually produces. This keeps the preview responsive during painting
while the true, heavier effect appears a moment after each stroke settles.

**The debounce cancels on any resumed activity.** The half-second countdown is not a
one-shot deadline that fires no matter what — any resumption of stroking cancels the
pending countdown and restarts it from zero:
- staying still for `0.4 s` and then continuing to stroke cancels the countdown and a
  fresh full half-second of stillness is required again;
- releasing the finger and re-starting to stroke within half a second of the release
  also cancels the pending countdown, so the real effect is **never** applied while
  the user is mid-stroke. Only a full, uninterrupted half-second of stillness (or a
  release that is not followed by a new stroke within half a second) triggers the
  actual effect.
Once the debounce fires, the committed strokes are folded into the composite as the
live preview, the selection overlay hides, and the next stroke cancels the live
result and returns to the overlay until it settles again. Regenerating the real
effect is asynchronous and can take a few hundred milliseconds on a large photo, so
the overlay is **never dropped into a dead gap**: from the moment the debounce fires
until the composite *that includes the just-settled strokes* is actually published,
the canvas keeps showing the selection drawing on top of the previous composite —
the drawing is removed only once that new preview is displayed. The canvas never
shows unbrushed content with no drawing at all.

**Pen controls:** pen strokes keep every existing control (pen type — solid, glow,
neon, rainbow; multi-layer + undo/redo) and add **colour opacity** and **hardness**.
- **Colour opacity** (0–100%) applies to **every** brush and makes the drawn art
  progressively translucent, applied on top of (never replacing) the chosen colour.
- **Hardness** (0–100%) shapes the stroke's edge intensity relative to its size and
  applies only to the **solid** and **rainbow** brushes: at **100%** the stroke has
  a fully solid border across its whole size (hard edge); at **0%** only a single
  centre pixel is fully solid and the stroke fades from there out to the size (soft
  edge). Intermediate values blend between the two. A stroke is "solid" at the edge
  only when no opacity is applied — with an opacity set, the whole stroke (including
  its solid border) is additionally scaled by that opacity. **Glow** and **neon**
  never use hardness, so the hardness control is **hidden** while either of them is
  selected (opacity stays available).
- **Overlaps never over-darken (or over-brighten) a stroke.** The live **pen
  stand-in for every brush (solid, glow, neon, rainbow)** is drawn from the
  *same* per-pixel raster the committed fold uses, which accumulates the
  **maximum** stamp alpha (with the winning band's own colour) at each pixel
  instead of source-over compounding. Where a soft stroke crosses itself the
  overlap keeps a single arm's coverage, and where two GLOW/NEON halos overlap at
  a sharp bend or a slow drag they never pile up past one stamp's value — the
  preview is pixel-identical to the committed result at every hardness and
  opacity, and the corner stays closed (no stamp gaps) because the raster stamps
  its full band profile along the whole stroke.
- **Colour selection respects the brush type**: the colour picker is shown for
  **solid**, **glow**, and **neon** and is **hidden** while the **rainbow** brush is
  selected — a rainbow stroke derives its hue from the stroke's position (the hue
  shifts along the rainbow as the stroke is drawn), never from the chosen colour;
  colour opacity still applies to a rainbow stroke.
- **Colour choices come from the shared picker** (see *Shared business rules* →
  *Colour picker*); any colour reachable there is reachable for the pen.
- **Strokes connect point to point for a fluid line.** A fast drag still produces a
  continuous line: consecutive captured points are joined (interpolated) by drawing
  the stroke along the segment between each pair, so fast, sparse samples never
  leave gaps or dotted breaks. This holds in both the live brush overlay and the
  committed render, so a quick flick paints the same solid ribbon it commits.

**Business rule (all brush tools):** one-finger drags paint a stroke; the
**eye** button suspends brushing and enables pan/zoom **and** hold-to-compare;
on disabling the eye, brushing resumes from that point (the changed pan/scale is
**kept** — the crop-only-quirk does not apply here). **Brush size adapts to the
image scale**, and **already-brushed strokes follow pan/scale** as the preview
moves (strokes stay glued to the image, not the screen). **Hold-to-compare** is
**not available** while actually painting (a press there would hide the drawn
strokes); it works in the
eye-enabled state like any other Category B tool.

---

### Decorative / placement overlays

Placed overlays that draw into the running composite at their stack position.

### Shared placement-overlay gestures

The placement tools (stickers, text, and Background Replace's placement state)
apply in any state where the tool's gestures are active. Their shared
**selected-item interaction** is (identical across the placement tools):

- **Actionable area**: the touch targets for **selection** (tap), **grab / move**
  (one-finger pan), **resize** (corner/edge drag) and **pinch** (zoom + rotate)
  are **larger than the item**: the item's current size inflated outward by
  **50% of its size + 48 dp** on every side — small items still get a
  fat-finger-friendly target, and the target grows together with the item. The
  selection grid, however, keeps outlining exactly the item's own bounds.
- **Resize grab band**: the corner/edge resize is grabbed from the **extended
  selection edge** inward, down to a line **20% of the item's own dimension
  inside its real bounds** (the same inner grab fraction as Crop's frame).
  Touches within that band resize; touches deeper inside the item (past the 20%
  line) **move** it instead.
- **Drag-target resolution**: a single-finger drag resolves its target with the
  exact rules below (an **already-selected** item wins whenever the position is
  inside its expanded area; otherwise the **nearest-centre** item at that
  position is chosen) and the **resize band of that resolved item** decides
  resize vs. move. The selected item's band is therefore NOT privileged — so a
  corner drag on an **unselected** item, when no selected item covers that
  position, **resizes it immediately** instead of first moving it, and a drag
  inside an already-selected item's area always stays on it (never leaks to an
  overlapped neighbour).
- **Overlap resolution**: when a touch falls inside the expanded areas of
  several items, **grabbing and pinching** apply to the **already-selected**
  item whenever it is one of the candidates (a selected item is never lost under
  another one). In every other case — **tapping** to change the selection, or
  grabbing/pinching when none of the candidates is selected — the touch targets
  the item whose **centre is closest** to the touch point, regardless of list or
  z order. Only when two or more centres are **exactly equal** (the same point,
  and it is the closest) does **list insertion order** decide — the earliest
  added wins.
- **Rotation wraps at 360°.** The rotation value is kept **wrapped (mod 360)** in
  `[0°, 360°)` — a full turn just places the value back at 0° and keeps going. The
  **rotation slider** mirrors this: it always reflects the wrapped value, never
  climbs past 360°, and returns to 0° exactly when the item completes a turn.
  This applies whether the rotation came from the pinch-twist gesture or from
  driving the slider itself.
- **Pinch-twist is stabilised.** The combined pinch (zoom) + twist (rotate)
  gesture separates the two reliably:
  - **Rotation only after it is deliberate.** Small angular jitter (fingers
    shifting while zooming) is **ignored** until the cumulative twist exceeds a
    **dead zone** (~10° — deliberately generous, so a pure pinch that the user
    intends as *zoom only* virtually never rotates the item even as the fingers
    drift while scaling).
  - **Rotation is smoothed.** Once past the dead zone, the rotation applied
    tracks the *average* of recent angular deltas rather than each raw sample, so
    a trembling two-finger turn produces a **steady** rotation instead of
    shaking, and letting go mid-turn never triggers a snap.
  - **Rotation is measured as the true finger twist.** The angle fed to the
    stabiliser is the **change in the angle between the two fingers** (the
    inter-finger vector), so the item turns **1:1 with the fingers** — a 10°
    deliberate two-finger twist rotates the item exactly 10°, never several
    times more, and a pinch-zoom where the fingers merely drift along curved
    paths reads as ~0° rotation.
  - **Slider feedback.** The rotation slider is updated from the same stabilised
    value, so the preview and the slider are always in sync (see *Rotation wraps
    at 360°*).

#### Stickers
- **Layer key** `layer:STICKERS` · **Label** "Stickers".
  - **Controls**: the picker opens with an **Emoji** entry first, which launches
  the **Android emoji popup** (`EmojiPickerView`) so any current emoji can be
  picked directly as a sticker. The **Shapes** section follows, holding the
  **tintable geometry** stickers — **heart, star, circle, square, triangle,
  flower, drop** — each with a **default fill colour** (heart → red, star → yellow, circle
  → blue, square → indigo, triangle → green, flower → pink, drop → cyan), and each taking any
  other fill colour from the shared **fill colour** picker. The remaining categories (Nature, Animals,
  Food, Objects, Decoration) show the bundled vector stickers. As the **last**
  entry the picker offers a **Gallery** option: it launches the **phone's photo
  picker** (`PickVisualMedia`) so **any image from the device gallery** is picked
  and becomes a sticker, so a user's own photo/art can be placed just like a
  bundled sticker. Stickers
  come in two kinds:
  - **Colour-enabled stickers** — tintable geometry (the Shapes section, and any
    tintable preset): the shared **fill colour** picker (see *Shared business
    rules* → *Colour picker*) recolours the sticker's **fill** in place; the
    shape's **default fill colour** is what appears when no fill is picked. These
    are exactly the stickers that also expose the **line** controls (below).
  - **Non-colour-enabled stickers** — fixed-colour art (emojis, photos, and
    everything else): **no fill colour control and no line controls** (a **gallery
    photo** is not colour-enabled —
    its own pixels are the sticker).
  - **Line** (colour-enabled stickers only): three controls accompany the fill —
    a **line style** selector (**None** (default) / **Solid** / **Dashed** /
    **Dotted**), a **line thickness** slider (0–20% of the sticker's shorter
    side, default 5%), and a **line colour** picker (a second instance of the
    shared colour picker; defaults to the sticker's default fill colour). The
    line is drawn around the sticker's art, outside its fill; a **None** style
    draws no line (the thickness/colour values are then inert but retained). The
    three line controls are shown only while a colour-enabled sticker is
    selected.
  - **Opacity** is available for **every** sticker, and a **rotation** control
    mirrors the pinch-twist angle (for devices whose input cannot twist). The
    rotation slider **wraps at 360°**: a full turn past 360° flips the value back
    to 0° and continues (see *Shared placement-overlay gestures* below), so the
    slider always mirrors the item's current rotation.
- **Emoji rendering**: an emoji sticker renders **at its full sticker size** — the
  glyph fills the sticker's bounding square without being cropped on any side, in
  both the live preview and the committed result.
- **Gallery stickers keep the original image's aspect ratio**: a gallery photo
  sticker is **never squeezed into a square**. Its on-canvas box
  (**width × height fraction**) reflects the **decoded image's own aspect ratio**
  — a portrait photo gets a taller-than-wide sticker box, a landscape photo a
  wider-than-tall one — and the renderer resamples the source **to exactly that
  box** (proportional fit, no centring crop and no distortion). The aspect
  ratio is carried with the placement (derived once from the picked image's
  dimensions) so the live preview, the committed fold, and a re-open of the
  sticker all agree on the same box.
- **Sticker items are parameterised.** Every placed sticker is retained as a
  `StickerPlacement` parameter — sticker id (preset or emoji), position
  `(x, y)`, uniform scale, rotation, opacity, its optional **fill colour**, and
  — for colour-enabled stickers — the optional **line style** / **line
  thickness** / **line colour** —
  held on the `StickerEffect` stack entry as the source of truth, so returning
  to the tool restores every sticker for re-editing; only **Merge Layers**
  bakes them into pixels. A **gallery sticker** is parameterised by the picked
  image's **content URI** (carried on the placement in place of a preset id),
  which the preview and the committed fold decode at render time — the pixel
  data itself is never baked until merge, exactly like every other sticker.
  Multiple stickers can be placed and coexist; each is independently editable.
- **Insertion**: the first sticker is placed **at the canvas centre**; each
  subsequent sticker is placed **slightly below the previous** placed one, so a
  rapid sequence of additions walks down the canvas.
- **Selection**: the selected sticker is framed by a **selection grid** —
  bounding outline with corner handles drawn exactly on the **sticker's own
  bounds** (the grid is visual only — it never grows). **Tap-drag** moves it,
  **pinch** scales it uniformly, **pinch + twist** rotates it (stabilised per
  *Shared placement-overlay gestures*), **tap** selects it, and a **Remove**
  button deletes the currently selected sticker.
  - **Corner / edge resize**: dragging a selection **corner handle** (or inside
    an **edge's** resize band) **resizes** the sticker. Unlike Crop — whose
    corner drag is **free aspect ratio** — a sticker **always scales
    proportionally**: any corner or edge drag applies a **uniform scale** from
    the opposite corner (edges scale along their axis and the orthogonal axis
    follows), so the sticker never distorts.
  - **Interaction rules** (actionable area, resize grab band, drag-target
    resolution, overlap resolution): the shared placement-overlay rules — see
    *Shared placement-overlay gestures*.
- **Rendering**: stickers are **vector** where possible so scaling stays smooth
  and resolution-independent. Raster stickers (a **gallery photo** picked via
  the Gallery entry) scale with high-quality **filtered (bilinear) resampling**
  whenever rendered at anything other than 100% — the picked image is decoded
  from its **content URI** (through the same efficient decode used by the
  gallery: downsampled to the sticker's sticker space, then resampled to the
  on-canvas size), so a large photo never loads at full pixel size just to show
  as a small sticker.
  The **fill** is drawn first and the **line** next, stroked along the sticker's
  **silhouette** (the alpha contour of its art) at the chosen thickness and
  colour, honouring the sticker's opacity; **Dashed** / **Dotted** styles apply a
  dash path effect along that contour, and the line follows the sticker's
  scale/rotation exactly (the preview and the committed fold draw it
  identically).
  **Web GIF search is not offered**: no open-source, on-device mechanism exists
  (GIPHY/Tenor/Klipy are proprietary, keyed hosted services — Tenor ends
  2026-06-30, GIPHY is no longer free).
- **Eye button**: standard Category B behaviour (see *Two preview/compare
  categories*); the sticker gestures / selection grid are **suspended** while the
  eye is on.

#### Text
- **Layer key** `layer:TEXT` · **Label** "Text".
- **Controls**: a **multi-line text field**, **standard text-editor icon buttons**
  for alignment (**left / centre / right / justified** — the usual left-align,
  centre-align, right-align, justify icons) and for style (**bold / italic /
  underline / strike-through** — the common **B / *I* / U / S** icons, combinable
  in any combination), plus text colour with **opacity** (0–100%) chosen from the
  shared **colour picker** (see *Shared business rules* → *Colour picker*), font
  family, font size, the shadow effect (**angle, distance, colour** — colour from
  the same colour picker), the outline effect
  (**size, colour, blur intensity** — colour from the picker; **blur** shapes the
  softness from a solid edge at `0%` out to a fully feathered edge at `100%`,
  rendered live on the preview text exactly as it commits, so the toggle and
  sliders take effect immediately),
  a **background shape**
  selector (**none / rect / rounded / oval / pill / blur**) — the four filled
  shapes carry their own **colour** and **opacity**, while **blur** (the last
  entry) shows no colour picker and no opacity slider and instead offers a single
  **intensity** slider (see *Background blur semantics* below) — and a
  **rotation** control mirroring the pinch-twist angle (it
  wraps at 360° and stays in sync with the gesture exactly like the sticker
  slider — the slider reflects the live pinch-twist value, wrapped, and driving
  the slider pokes the same wrapped value back into the item; see *Shared
  placement-overlay gestures*); or OCR **"Recognize Text"**
  which turns recognised text into the same editable text items.
- **Wrapping matches the live layout.** Long text wraps to fit the block the
  same way in preview and committed result: words break at spaces, **consecutive
  spaces are preserved**, a no-space **CJK / ideographic run breaks between
  characters**, and a single word too wide for the block is **soft-wrapped by
  character** instead of being clipped. Explicit newlines (and blank lines) are
  always honoured. The committed wrap stays pure `Paint.measureText` math
  (deterministic on every backend), mirroring the live Compose layout on these
  line-breaking rules; only the wrap width differs slightly (the live block is
  the canvas minus padding, the committed block caps at 95% of the image width),
  which can flip a borderline-length word by one line.
- **Background-shape geometry is size-relative** — every filled shape derives its
  geometry from the text's own size, never a fixed dp: **rect** is a
  sharp-cornered rectangle, **rounded** clips its corners at a radius of **25% of
  the font size**, **oval** inscribes an ellipse in the text block, and **pill**
  rounds at half the short side. The **live preview and the committed rasteriser
  share the same corner rule**, so a ROUNDED background shows identical corner
  curvature at every font size and pinch scale (the corner scales with the block
  in both).
- **Outline blur semantics.** The outline effect's **blur intensity** is a
  feathered edge: at **0%** the outline is a single hard stroke hugging the glyph
  edge at its chosen **size**; as blur rises to **100%** the outline keeps its
  full thickness at the glyph (it starts at the outline's own edge, next to the
  fill) but **fades to transparent outward** — the farther from the glyph the
  more see-through it becomes, so a 100%-blurred outline is a soft translucent
  halo that starts opaque at the glyph and dissolves outward. Blur never shrinks
  the visible thickness inward toward the font; it only softens the outer edge.
- **Background blur semantics.** The **blur** entry of the background-shape
  selector (the **last** position in the list) is a **soft-edged oval of blurred
  photo content behind the text** — not a solid colour. It has **no colour
  picker** and **no opacity slider**; instead the background controls show a
  single **intensity** slider, **0–100%** — **0%** applies no blur at all (the
  background is indistinguishable from none), and **100%** applies the strongest
  blur. The **shape is an oval** (like the *oval* entry, sized to the text
  block), and the blur is a **real image blur**: the photo pixels **behind the
  text** are diffused rather than covered, chosen so that underlying detail
  (e.g. the very text this background is used to cover after OCR) becomes
  legibly unreadable at moderate intensities. The oval is **soft at the borders
  — the blur fades outward**, mixing gradually into the surrounding image rather
  than ending in a hard edge, so the background never looks like a pasted disc.
- **No colour/opacity controls when Blur is selected.** Because the blur
  background carries no colour, the shared *background colour* picker and the
  *background opacity* slider are **hidden** whenever the blur shape is active;
  the intensity slider replaces them in the controls panel.
- **OCR import enables the blur background by default.** Text imported via
  **Recognize Text** arrives with the background shape set to **blur** and the
  intensity at a sensible preset (in the upper range), so recognized text
  typically lands on a legibility-clearing blurred patch behind it. The user
  can still change the shape, intensity, or remove the background entirely —
  the imported item is a fully normal `TextItem`.
- **Transparency is carried by the fill, not blended with the outline.**
  When a font colour whose **opacity is below 100%** (translucent fill) is
  combined with an outline, the text's own glyph **fades toward transparency**
  on its own — it never blends with / fades toward the outline colour behind it.
  (The outline is drawn beneath the fill, so a translucent fill must still show
  as translucent, revealing the image through the glyph.) If the fill is opaque
  the glyph shows the outline only around its edge, as usual.
- **The outline hugs the glyph edge outward only.** The outline band is rendered
  **strictly outside** the glyph — it never grows inward into the glyph's shape
  (there is no inward half). Concretely, the committed rasteriser
  (`BitmapRenderer.drawTexts`) and the live preview (`TextOverlayLayer`) draw
  each feathered pass at **double width** into an isolated layer and then clip /
  clear the **glyph fill shape** out of that layer, so the surviving band is the
  full chosen **size** but only on the non-fill (outer) side of the edge. Because
  nothing of the outline overlaps the glyph region, a **translucent or 0% fill**
  reveals the image through the glyph cleanly with no outline showing inside the
  shape, for any blur intensity, matching the negative-mask result pixel-for-
  pixel at every opacity with no extra pass.
- **Text items are parameterised.** Every text — typed or recognized — is a
  `TextItem` parameter holding its full definition: content, colour (with
  opacity), font family, font size, alignment, position `(x, y)`, scale,
  rotation, the bold/italic/underline/strikethrough style set, and its shadow
  (angle / distance / colour) and outline (size / colour / blur) effects, and
  a background shape (none / rect / rounded / oval / pill / **blur** — the filled
  shapes carry colour and opacity; **blur** carries a blur intensity instead).
  The `TextItem`
  list **is the source of truth on the `TextEffect` stack entry** — the render
  simply rasterises that list into the running composite for the next fold.
  Returning to the tool therefore **restores every item** (content and all
  parameters) for re-editing; only **Merge Layers** bakes text into pixels and
  loses the parameters — exactly as for every other layer.
- **Insertion**: text is added automatically as it is typed — the **first**
  text is placed **at the canvas centre**, and each **subsequent** text is
  placed **slightly below the previous added one**, so a rapid sequence of
  additions walks down the canvas. OCR text is placed at its detected regions
  (see *OCR* below) — matching their position, scale, rotation and colour as
  closely as the engine can determine. Every item is immediately selectable and
  draggable. The
  text currently being typed is **immediately the selected item**: as soon as
  the field holds any non-empty text the draft is framed by its selection grid
  and every control edit targets it in place — the grid appears with the first
  typed character.
- **Selection**: the selected text item is framed by a **selection grid** — a
  bounding outline with corner handles drawn on the canvas — so it is always
  clear which text is being edited. The grid always outlines the item's full
  outer box, **regardless of the background shape selected** (a rounded/oval/
  pill background never clips the frame).
  - **Hold / drag** the body of the selected text **moves** it (pan).
  - **Corner / edge resize** the selected text by dragging a **corner handle**
    (or an **edge's** resize band): it **always scales proportionally** (uniform
    scale from the opposite corner), never free aspect ratio — a text item never
    distorts.
  - **Pinch** **scales** it (uniform proportion).
  - **Pinch + twist** **rotates** it — stabilised exactly as *Shared
    placement-overlay gestures* describe (a zoom-only pinch never rotates, and
    an intended twist is smoothed and mirrored by the rotation slider).
  - **Tapping** an unselected text selects it **and loads its content and
    parameters into the text field and controls** for immediate in-place
    editing — editing the field updates that item in place. The **Add** button
    commits the field as a **new** item (placed below the previous one).
  - **Remove resets the panel; there is no Cancel link.** The **Remove** button
    deletes the currently selected item **and resets every control in the panel
    to its default** (font size, colour, opacity, font family, style,
    alignment, rotation, shadow, outline, and background shape/colour/
    opacity/blur), **clears the input text**, and leaves the tool in its fresh
    first-run state — exactly as if the tool had just been opened. There is no
    separate **Cancel** button: a committed item is only removed via **Remove**,
    and a discarded draft simply leaves no new item.
  - **Interaction rules** (actionable area, resize grab band, drag-target
    resolution, overlap resolution): the shared placement-overlay rules — see
    *Shared placement-overlay gestures*.
- **Eye button**: standard Category B behaviour (see *Two preview/compare
  categories*); the text gestures / selection grid are **suspended** while the
  eye is on.

#### OCR (Recognize Text)
- **Engine**: on-device **PP-OCR** (PaddleOCR) models run through the open-source
  **MNN** runtime — the same OCR stack as the F-Droid-listed `offline-translator`
  project — replacing closed-source ML Kit text recognition. No network call is
  made; the detector + recognizer models ship with the app.
- **Availability-gated**: the **Recognize Text** action appears only when the
  on-device OCR runtime can initialize on the current device/emulator (the
  model's native libraries must match the device ABI, e.g. an ARM build cannot
  run on x86 hosts). Where the runtime can't load, the action is hidden and the
  rest of the text tool stays fully usable.
- **Imported items match the detected text as closely as possible.** Every
  recognized line becomes the same editable `TextItem` as typed text, placed so
  it reconstructs the source region as faithfully as the engine's output allows:
  - **Position** — the item's anchor is the detected line's **bounding-box
    centre** (normalized to the source image), so it lands on the original text.
  - **Size** — the item's font size reproduces the detected line's **height**
    proportion of the image (not merely its width), so the glyphs match the
    source scale.
  - **Rotation** — the item's rotation is set to the detected line's **skew
    angle**, so tilted text comes in tilted (the rotation control then mirrors
    it, and the user can still adjust it freely).
  - **Colour** — the item's colour is sampled from the **detected region's
    dominant foreground pixels** wherever that is reliable (e.g. a crisp
    dark-on-light line), so the imported text visually matches the source;
    when sampling is inconclusive (complex/colourful background), it falls back
    to the text tool's default colour.
  - **Background** — the item's background shape is set to **blur**, with the
    intensity at a **sensible preset** (see *Text* → *Background blur
    semantics*), so each imported line arrives on a blurred patch that hides the
    source glyphs beneath it while the editable overlaid text carries the same
    visual information. This is the OCR default; the user can change the shape
    or intensity (or remove the background) freely like any typed text.
- The recognized items stay fully editable: select them to load content + params
  back into the field (see *Text* above), re-colour, re-scale and re-rotate them
  like any typed text.

#### Frames
- **Layer key** `layer:FRAMES` · **Label** "Frames".
- **Two tiers**: the Frames tool has two **tiers** — the **inner frame** and the
  **outer frame** — each presented as its own **labelled section** (**"Inner
  frame"** / **"Outer frame"**) with its own style row and controls. Both tiers
  default to **"None"** (no frame): a tier at None contributes nothing and shows
  only its style row. The tiers share the same **style** set — except that the
  outer tier offers no **Heart** style. The **inner** tier draws **inward**
  onto the photo (the current behaviour); the **outer** tier draws **outward**
  from the photo edge and, when enabled, **enlarges the output image** (see
  *Outer-frame dimensions* below).
- **Controls**: each tier's section has a **style** selector, a **thickness**
  slider, an **edge size** slider (shown for every ⁎ style below), a **colour**
  picker, a live preview of the frame around the image, and — for the special
  **Custom (Curved)** style — four dedicated sliders (**Angle**, **Curve
  distance**, **Strength**, **Count**). Selecting **"None"** for a tier hides
  **every other controller of that tier** — the edge size, thickness, colour,
  line and Custom sliders are not shown while no style is picked, leaving only
  that tier's label and style row.
  - **Inner vs outer drawing**: the inner tier's band, edge treatment and
    decorative lines all extend **inward** from the picture edge toward the
    photo; the outer tier's extend **outward** from the picture edge away from
    the photo (the photo's own pixels are left untouched).
  - **Style**: the frame's outer shape and edge treatment, shown as chips in this
    order (the **outer** tier shows the same list **minus Heart**):
    - **Shapes**: **solid** (a straight-edged rectangular band), **rounded** ⁎ (a
      rectangle with rounded corners), **beveled** ⁎ (a corner-chamfered / mitred
      band), **oval** (an elliptical/oval band clamped to the largest ellipse that
      fits), and **heart** (a heart-shaped band clamped to the largest heart that
      fits, with the heart **centred** on the canvas — its bounding box is
      centred both horizontally and vertically in the interior). **Heart is an
      inner-tier-only style**: the outer tier offers no Heart chip.
    - **Edge & bevel profiles** (the real picture-frame / furniture edge
      treatments — every profile is ⁎ and takes the **edge size** slider):
      **inward curve edge** ⁎ (the sides bow inward — concave — toward the
      corners), **corner eased edge** ⁎ (softly rounded corners), **gear edge** ⁎
      (a rim of rounded gear-teeth bumps), **ogee edge** ⁎ (an S-shaped double
      curve along the edge), **double ogee edge** ⁎ (two S-curves stacked),
      **dupont edge** ⁎ (a stepped flat with an inner chamfer), **cove edge** ⁎ (a
      concave inward-quarter hollow), **french cove edge** ⁎ (a deep concave cove
      with a flat step), **dupont + ½ bullnose edge** ⁎ (the dupont step capped by
      a half-round convex bead), and **ogee cove edge** ⁎ (an S-curve that
      recedes into a hollow).
    - **Ornamental / moulding styles**: **wavy** (relief waves repeated along the
      edge), **sawtooth** (a zig-zag edge), **cogged** (alternating square teeth —
      distinct from the **gear edge** profile above), **floral** (a flourish of
      flowers / leaves repeated around the border), **waterfall** (a broad flat
      step that drops off a vertical tier), **single wave** (one wide S-wave per
      side), **twin wave** (two S-waves per side), **recessed** (a flat band with
      a recessed mid-step), **offset wave** (an S-wave shifted along the side),
      **water waves inward** (a row of inward half-curves set one beside the other
      along the edge) and **water waves outward** (the same half-curves bulging
      outward into the photo).
    - **Custom (Curved)**: a free-form member with its own four sliders; it
      combines the code's inward-curve / cove / french-cove / dupont+½-bullnose
      behaviours into one parametrised edge.
    Choosing a style shows a short **example** of that shape around the preview.
  - **Thickness** (0–100% slider): the band's width relative to the short image
    dimension — **inward** from the picture edge for the **inner** tier,
    **outward** from it for the **outer** tier.
    The band is **half** the slider value of the short edge: `0%` = no visible
    band, `50%` = a quarter of the short edge, `100%` = half the short edge (a
    thick border — the frame never swallows the image below 100%). (Oval/
    heart-shaped styles snap to the largest band that fits the aspect ratio; the
    outer tier's band is capped the same way.)
  - **Edge size** (0–100% slider, shown for every ⁎ style — **rounded**,
    **beveled** and the whole **edge & bevel profile** family): how far the
    corner / edge treatment extends along each edge, relative to half the
    **short** image dimension. `0%` = a **squared** corner (the edge meets the
    frame band at a right angle, like **solid**). `100%` = the treatment takes
    **50% of the short edge**: it runs all the way to the middle of the short
    edge, so the **short edge touches both corners** — but because the edge is
    squared straight between them, on the **long** edge the two corner
    treatments only meet if the image is itself square (otherwise a straight run
    remains between them). For **rounded** it is the corner arc's radius read as
    a fraction of the short edge; for **beveled** it is the chamfer's depth along
    the edge; for each edge profile it is how far the profile's curve / step
    reaches along the edge. A new frame layer starts at **20%**. **Edge size is
    independent of thickness**: the treatment keeps its footprint when the
    thickness slider moves — the edge shape is **preserved** — except when the
    band is so thick that the treatments from opposite edges would meet/touch
    across the short dimension, where the interior clamp (`min(width, height)/2`)
    may compress them (see the *Edge-size bug fix* below). For the **outer**
    tier the same reach is measured **outward** from the picture edge (the
    profile then runs along the ring's outer border).
  - **Frame lines** (both tiers, any style except None): concentric decorative
    lines in the tier's **colour** inside the band. A **Lines on/off** toggle
    controls them: with the toggle **off**, `0` lines are drawn (just the plain
    band) and **no line controls are shown**; with it **on**, the three line
    controls appear and the lines draw **inward** from the inner tier's band
    toward the photo, **outward** from the outer tier's band away from the
    photo:
    - **Lines** (1–10 slider, default 1): how many decorative lines to draw
      (the toggle-off state is the only "0 lines" state).
    - **Distance** (0–20% of the short image edge, default **equal to the
      default line thickness**): the spacing between consecutive lines (and
      between the band and the first line).
    - **Line thickness** (0–10% of the short edge, default 2%): each line's
      width.
    Each line strip is a **concentric offset copy** of the boundary it follows —
    the interior window boundary for the **inner** tier, the band's **outer
    border** for the **outer** tier — spaced at a constant perpendicular
    distance, so the strips carry both the boundary's shape and its corners.
    Whenever the followed boundary forms a **vertex whose interior angle lies
    more than 60° away from 180°** (an angle **below 120° or above 240°** — the
    four right-angle frame corners, sharp mid-edge ornaments such as **sawtooth**,
    **cogged** and a full-strength **Custom (Curved)** triangle, on both the
    convex peaks and the concave notches), the strip reproduces that corner as a
    **hard (mitered) angle** in the line, mirroring the frame's own outline /
    inline; at vertices within 60° of a straight path (a smooth 120°–240° angle)
    or on smoothly curved boundaries the strip shares the boundary's curve, as on
    **oval**, **heart** and the rounded corner styles.
    The lines are drawn in the tier's frame colour with the same opacity
    handling as the band. For the **outer** tier, the lines' total outward
    extent (`lines × (distance + line thickness)`, when the toggle is on)
    contributes to the enlarged output size (see *Outer-frame dimensions*
    below).
  - **Custom (Curved) sliders** (only when the **Custom (Curved)** style is
    selected):
    - **Angle** (−180°…+180°): chooses **where along each edge the curve
      begins** — a phase offset that shifts the pattern along the edge (`0`
      starts at the corner; advancing it rotates where the curve peaks).
    - **Curve distance** (−100%…+100%): how far the curve sits from the frame
      band (the band itself comes from **thickness** / **edge size**).
      **Negative curves inward (a scoop — concave)**, **positive curves outward
      (a bulge — convex)**, `0%` is a straight edge; a new frame layer starts at
      **−50%**.
    - **Strength** (negative…+100%): shapes the curve — `0%` is a **perfect
      arc**; negative overshoots into a **drop-like / teardrop** curve; as it
      approaches `+100%` the arc straightens into **straight lines** that meet at
      an inward/outward point (a triangle).
    - **Count** (1–10, default 2): the number of **half-curves on each side** —
      `1` is a single half-curve, `2` is one complete wave per side (the
      default), `4` is two waves per side, and so on.
  - **Colour**: from the shared **colour picker** (see *Shared business rules* →
    *Colour picker*). When a tier's style is selected (anything but **None**) the
    tier's frame and line colour defaults to **black**; the colour stays a user
    pick until the style is cleared back to **None** (selecting styles again
    restores the last picked colour for that tier).
- **Outer-frame dimensions**: when the **outer** tier is enabled (style ≠ None)
  the rendered output is **larger than the underlying image**. The fold's canvas
  width/height grow by **twice the outer tier's total outward extent**, where
  the extent = the outer band (thickness) **plus** the outer tier's line extent
  (`lines × (distance + line thickness)`) **plus** any outward-carrying edge
  treatment; the image is inset by that extent and the outer band fills the ring
  beyond the picture. **The method that computes the output dimensions must take
  the outer extent into account** — the fold canvas creation in
  `BitmapRenderer.render` (and per-effect `FrameEffect.render`) allocates
  `width + 2 × extent` × `height + 2 × extent` and draws the photo at
  `(extent, extent)` so every later effect and the save/share export the larger
  canvas. The interactive preview shows the outer band around the *fitted* photo
  and clips it to the viewport (the extra extent needs no extra resolution — it
  is drawn into the same fitted rect).
- **Edge-size bug fix (preserve the edge shape)**: the edge & bevel treatments
  (**beveled**, **rounded**, **corner eased edge**, **gear edge**, **ogee edge**,
  **double ogee**, **dupont**, **cove**, **french cove**, **dupont + ½ bullnose**,
  **ogee cove**, and any other ⁎ style) are sized by **the edge-size slider
  alone** — `FrameGeometry.fillZone` computes the treatment from the
  edge-size-derived reach (`swing = amplitude × reach`), never from the band
  thickness, so dragging the **thickness** slider **preserves the edge shape**
  (the straight band between corners may grow/shrink, but the corner treatment
  keeps its footprint). The **only** exception: when the band is so thick that
  the treatments of opposite edges would meet/touch across the short dimension,
  the interior clamp (`min(width, height) / 2`) may compress them. Applies to
  **both tiers** (the outer tier mirrors the same rule).
- **Eye button**: standard Category B behaviour (see *Two preview/compare
  categories*).
- **Quirks**: **Frame is an ordered layer** — it folds in the same add-order as
  every other effect (not a fixed always-on-top border). The **inner** and
  **outer** tiers are **one frame layer**: a single `FrameEffect` stack entry
  carries both, so both tiers commit, reorder, merge and revert together.

---

## Shared business rules

- **Colour picker (custom colour anywhere — compact row + popup)**. Every place
  that asks for a colour — the **text** tool (font colour, filled
  background-shape colour, shadow colour, outline colour; the text tool's **blur**
  background shows no colour — see *Text* → *Background blur semantics*), the **sticker** tool (fill colour and
  line colour of colour-enabled / geometry stickers), the **frame** tool (frame colour), the
  **pen** tool (solid / glow / neon brush colour), and the **beauty** makeup
  tools and beauty pen (Lips, Blush, Eye Sh., Liner, Base, Hair, Pen) — uses the
  **same colour picker**. The control is deliberately **compact so it fits inside
  a controls panel** and does not crowd out the tool's other controls: one row of
  the **most common colours** (a short curated preset — black, white, and a
  handful of saturated basics, or for beauty the makeup shade set plus black)
  plus, as the row's **last cell**, a **rainbow-coloured circle** that **launches
  the colour-pick popup/dialog**.
  - The **popup** holds the **full picker**: the **wide preset swatch row** (for
    text and frame: the full rainbow plus black/white/greys and the existing
    colour sets for the shadow and outline — not just a handful of swatches),
    a **hue–saturation box**, and a **brightness / alpha slider** (the alpha
    appears only where the tool carries colour opacity — text and frame).
  - Choosing a preset or dragging in the box updates the picked colour **live**;
    the popup's choice applies on **confirm** (preset picks apply immediately),
    so any colour can be chosen — not just the compact presets.
  - The currently picked colour is shown live in the preview (glyphs /
    background / shadow / outline / frame band / sticker tint / pen stroke) as
    soon as it is chosen.
- **Photo loading bakes EXIF orientation into the original**: opening a photo into
  the editor decodes its pixels together with the **EXIF orientation metadata**
  (every rotation and mirror tag a JPEG can carry) and bakes that orientation
  into the resident `originalBitmap` **before any editing state is derived**.
  A photo whose EXIF says it is rotated or flipped therefore enters the editor
  already right-side up: the preview, the hold-to-compare plain original, the
  fold's base input and every committed result all use the correctly-oriented
  pixels — the raw, un-oriented decode is never shown or edited.
- **Reorderable, ordered stack**: every layer can be dragged to any position in
  the Layers window; reordering is undoable and changes the final result because
  the fold runs left-to-right. While dragging, the list shows a **drop slot**
  feedback — the other rows shift aside to leave a visible blank band at the
  exact position the dragged layer would land on release (see `effect_stack.md` →
  *Layers window* → *Reorder drag feedback*), and the dragged row rides on top
  as a translucent ghost.
- **Commit on lost focus (all tools)**: a tool's parameters are committed to its
  stack entry when it **loses focus** — selecting a different tool, opening the
  Layers window, or tapping **Save** — but **only if** the parameters **changed
  and are not the default** values. The committed result is baked into the input
  for the **next** effect.
- **Bake to input (all tools)**: when committed, every effect's result becomes
  the **committed input bitmap** for the next effect. Each later effect consumes
  the previous effect's **baked bitmap** — it does **not** re-apply a raw
  parameter or geometric override from an earlier tool. This is the *order
  matters* rule — it never implies a fixed ordering or grouping of effects; the
  user's stack position decides what each effect consumes:
  - **A Transform** commits by **emitting a new bitmap with the rotation/flip
    drawn inside the pixels** — a new oriented canvas (transparent bg, white on
    JPEG). A **Frame** placed *after* it draws **square** around that rotated
    canvas; a **Frame** placed *before* it rotates together with the image.
    Neither placement is privileged — both are plain stack order.
  - **Frames** consume the incoming committed bitmap and add their **border
    drawn into** the bitmap on top of it.
- **Commit = produce a bitmap. No exception — no display-only permanence.** Every
  effect's committed result is rasterised into the working bitmap (see
  `effect_stack.md` — *The one rule that has no exception*). A **Transform** is
  **not** "keep the canvas rotated and just remember the rotation as viewport
  state"; on commit it **generates the rotated/flipped bitmap** and that bitmap is
  what later effects consume. The preview `graphicsLayer`/viewport transform is a
  **transient editing affordance only**. Any effect whose committed state is
  purely a viewport transform — i.e. an effect that does **not** produce a bitmap
  on commit (e.g. a transform whose frame still follows the rotation, or a
  rotation that is applied only in the preview) — is a **violation** of this rule
  and must be reworked so the commit emits the rasterised bitmap and later
  effects consume the committed input.
- **Recovery / reversibility**: re-entering a committed tool restores its saved
  parameters into the controls, recomputes the composite before it, and on
  re-commit re-applies **at the same stack position** with a cascade fold of the
  tail. See `docs/effect_stack.md`, *Commit on lost focus*.
- **Merge / Apply Layers** flattens everything into one **Merged** layer, keeps
  the original for compare, cleans the stack, resets controls, clears beauty
  state, and calls `flatten()` on the shared geometry context.
- **Lens filters stack** independently of (and orthogonally to) the mutually
  exclusive colour/style filter.
- **Geometry-changing effects** (crop, rotate, flip, face reshape, a background
  Replace placement — Replace mode with a placement image selected — occluding
  overlays, and the hard-stroke brushes **pen / mosaic / object removal**) mark
  the shared geometry context **stale**, so a beauty layer or the background
  tool's subject mask resolved after them regenerates geometry against the
  changed image
  (`docs/effect_stack.md`, *Which effects change geometry*). **Face-makeup** and
  **blur-brush** do **not** hard-cover and do **not** mark it stale.
- **Live preview is debounced** while dragging sliders; the reversible stack is
  rebuilt and **committed on lost focus**.
- **Hold-to-compare** is governed by the two categories (see *Two
  preview/compare categories*): pan/zoom-capable (Category A) tools always keep
  the plain original available regardless of eye state; non-pan/zoom-capable
  (Category B) tools provide it **only while their eye is enabled**.
- **Non pan/zoom-capable (Category B) tools** keep the preview at the default
  position/scale in their normal editing state rather than carrying stale
  pan/zoom; their eye enables pan/zoom. **Only crop** resets the viewport when
  its eye is disabled; the other eye tools keep the changed pan/scale.
  **Background's placement state** is a Category B state: selecting a placement
  image resets the viewport to the default fit, and disabling its eye returns to
  that same held-still placement view (see *Background*).
- **Eye button default state**: Category A tools are **on** by default (full
  composite shown); Category B tools are **off** by default (isolated current
  effect shown). Leaving a tool resets the eye to its default state.
  **Background is state-dependent**: on in Blur mode and in Replace mode without
  a placement image, off once a placement image is selected.
