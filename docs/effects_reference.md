# Editor Effects Reference — per-effect catalog & interaction rules

> Living reference for the photo **editor**. It catalogs **every effect** and the
> **business rules** that govern its preview interaction (pan/zoom, the eye /
> visibility toggle, hold-to-compare with the original image) and its place in
> the layer stack. The underlying **reversible, non-destructive, ordered stack**,
> the fold renderer and the shared beauty-geometry model are documented in
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

There is **one shared preview viewport** (`ViewportTransform`) that holds the
current `scale` + `pan (offsetX/Y)` and the orientation (rotation, flips). It
drives both the live photo and the hold-to-compare overlay, so the original is
revealed at the **same** scale, position, rotation and flips the user is looking
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

### Two preview/compare categories

Every tool is one of exactly **two** categories. Every tool has an **eye
button**. A tool's pan/zoom, hold-to-compare and tail-folding behaviour derive
from its category:

**Category A — pan/zoom-capable (default):** every effect *except* the tools
listed in Category B — adjustments, filters, enhance, style, background, crop,
frames, stickers, text, all brush tools, and all beauty sub-tools. Transform is
in this set (it is a pure control tool — rotates/flips come from buttons and a
slider, with no single-finger canvas gesture).
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
**crop, frames, stickers, text**, and **all brush tools** (pen,
mosaic, blur brush, object removal). **Transform is *not* in this set**: it is a
pure control tool (rotates/flips come from buttons and a slider, with no
single-finger canvas gesture), so it stays **pan/zoom-capable (Category A)**.
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
  rectangle.
- **Eye button**: yes (Category B — off by default). Eye OFF → canvas shows only
  the crop effect based on the previous image (no tail folded); pan/zoom and
  hold-to-compare are **not available**. Eye ON → canvas shows the full composite
  (prefix + crop + tail); pan/zoom and hold-to-compare are enabled; the crop
  frame drag is **suspended**. Disabling the eye **resets** the viewport to the
  original position and scale (the crop quirk).
- **Hold-to-compare**: not available in the normal editing state (a
  press-and-hold there reads as a frame drag/resize; the drag is owned by the
  crop frame). Available **only while the eye button is enabled**; holding then
  reveals the plain (uncropped, unoriented) original at the current viewport.
- **Quirks**: an ordinary layer, not a "base step" — like every effect it
  commits by resampling the running composite to the selected region, and
  whatever sits after it in the user's stack order consumes that result.
  Cropping is a one-shot commit (records undo history) and marks the shared
  beauty geometry **stale**.

#### Transform (rotate / flip / free rotate)
- **Layer key** `layer:TRANSFORM` · **Label** "Transform".
- **Controls**: Rotate CCW / Rotate CW (90° shortcuts), Flip H / Flip V, plus a
  **free rotate** slider (`0°–360°`) with CW/CCW buttons that bump the slider by
  90° (wrapping: 360→90→0→270→…). The H/V flip + CW/CCW buttons sit on a control
  line slightly **below** the main toolbar.
- **Eye button**: yes (Category A — on by default). Eye ON → canvas shows the
  full composite (prefix + transform + tail). Eye OFF → canvas shows only the
  prefix + transform (no tail folded). Pan/zoom and hold-to-compare are always
  available regardless of eye state. Controls remain fully enabled in both states.
- **Hold-to-compare**: available — holding reveals the plain original at the
  current viewport position/scale (the in-progress transform is what is shown
  while not held).
- **Quirks**: any **non-multiple-of-90°** free-rotate angle makes the canvas
  **grow to fit** the rotated image. The background is **transparent by
  default**; when saved to **JPEG** the background becomes **white**. On commit
  the transform emits a **new rotated canvas** — the rotated/flipped pixels
  become part of the running composite. It is an independent layer like any
  other: whether a **Frame** placed later draws square around the rotated
  canvas, or one placed earlier rotates together with the image, is decided
  purely by stack order. Rotate/flip mark the shared beauty geometry **stale**.

---

### Photo-look — adjustments

All are deterministic parameterised passes; each is its own layer the user can
reorder and re-edit. All are **pan/zoom-capable (Category A)**, have an **eye
button (on by default)** that controls only tail folding, and support
**hold-to-compare with the plain original**.

| Tool | Layer key | Range | Applies as |
|------|-----------|-------|------------|
| Skin Denoise | `layer:SKIN_DENOISE` | `0..1` | wavelet denoise (softness = 1 − intensity) |
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
  slider is `0..1` (a low value keeps detail, high value denoises more).
- **Vignette is a photo-look layer** (parameterised radial overlay), not a fixed
  border — it folds in stack order like every other adjustment.

---

### Photo-look — filters, enhance, style

All **pan/zoom-capable (Category A)**, **eye button (on by default)** that
controls only tail folding, **hold-to-compare** supported.

#### Filters
- **Layer key** `layer:FILTER` (color/style), lens filters stack separately.
- **Controls**: Color filters (mutually exclusive), Style filters (mutually
  exclusive with color), Lens filters (multi-select, stack independently).

#### Auto-Enhance
- **Layer key** `layer:ENHANCE` · **Label** "Auto-Enhance" (toolbar: "Enhance");
  on/off toggle; deterministic enhance pass.

#### Background
- **Layer key** `layer:BACKGROUND` · **Label** "Background".
- **Controls**: auto-detect subject (runs on open), Blur / Replace modes, blur
  intensity + bokeh shape, or preset/gallery replacement background.

---

### Beauty — face / ML sub-tools

Beauty is **individual, reorderable layers** (one per sub-tool), folded with the
shared geometry context. See `docs/effect_stack.md`
("Beauty — per-sub-tool layers + geometry"). All are **pan/zoom-capable
(Category A)**, **eye button (on by default)** that controls only tail folding,
**hold-to-compare** supported.

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
| Face slim | `beauty:slim` | Beauty · Slim | slider 0..1 |
| Eye enlarge | `beauty:eye_enlarge` | Beauty · Eye Enlarge | slider 0..1 |
| Nose | `beauty:nose` | Beauty · Nose | slider −1..1 |
| Jaw | `beauty:jaw` | Beauty · Jaw | slider −1..1 |
| Lipstick | `beauty:lipstick` | Beauty · Lipstick | color picker + slider |
| Blush | `beauty:blush` | Beauty · Blush | color picker + slider |
| Eye shadow | `beauty:eye_shadow` | Beauty · Eye Shadow | color picker + slider |
| Eyeliner | `beauty:eyeliner` | Beauty · Eyeliner | color picker + slider |
| Foundation | `beauty:foundation` | Beauty · Foundation | color picker + slider |
| Hair color | `beauty:hair` | Beauty · Hair Color | color picker + slider |
| Brightness pen | `beauty:pen` | Beauty · Pen | brush size + opacity + undo/clear |

**Quirks:**
- Face reshape is a **geometry-changing** effect — it marks the shared geometry
  context stale so a later beauty layer regenerates against the reshaped image
  (see `docs/effect_stack.md`, *Which effects change geometry*).
- A beauty layer's **ML geometry** (face landmarks / hair mask) is always
  derived from an image grounded in the **pristine original** (so face
  landmarks stay stable regardless of colour edits), via the geometry model in
  `docs/effect_stack.md` (*Original geometry image and original geometry*); the
  layer's **pixels** apply onto the running composite at the layer's stack
  position exactly like any other effect.

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
| Pen | `layer:PEN` | Pen | pen type, color palette, size, multi-layer + undo/redo |
| Mosaic | `layer:MOSAIC` | Mosaic | brush type, size, opacity, "suggest faces" |
| Blur Brush | `layer:BLUR_BRUSH` | Blur Brush | size, strength, eraser |
| Object Removal | `layer:OBJECT_REMOVAL` | Object Removal (toolbar: "Remove Object") | size, eraser |

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

#### Stickers
- **Layer key** `layer:STICKERS` · **Label** "Stickers".
- **Controls**: sticker category + grid; drag to place, pinch to scale each
  sticker (0.3×–5×).
- **Eye button**: yes (Category B — off by default). Eye OFF → canvas shows only
  the sticker effect based on the previous image (no tail folded);
  hold-to-compare is not available. Eye ON → canvas shows the full composite
  (prefix + sticker + tail); pan/zoom and hold-to-compare are enabled; the
  sticker drag/resize is **suspended**.

#### Text
- **Layer key** `layer:TEXT` · **Label** "Text".
- **Controls**: text field, font size, text colour, background shape + colour;
  or OCR "Recognize Text". Text overlays are draggable.
- **Eye button**: yes (Category B — off by default). Eye OFF → canvas shows only
  the text effect based on the previous image (no tail folded);
  hold-to-compare is not available. Eye ON → canvas shows the full composite
  (prefix + text + tail); pan/zoom and hold-to-compare are enabled; the text
  drag is **suspended**.

#### Frames
- **Layer key** `layer:FRAMES` · **Label** "Frames".
- **Controls**: horizontal frame-preset picker (with "None").
- **Eye button**: yes (Category B — off by default). Eye OFF → canvas shows only
  the frame effect based on the previous image (no tail folded);
  hold-to-compare is not available. Eye ON → canvas shows the full composite
  (prefix + frame + tail); pan/zoom and hold-to-compare are enabled.
- **Quirks**: **Frame is an ordered layer** — it folds in the same add-order as
  every other effect (not a fixed always-on-top border).

---

## Shared business rules

- **Reorderable, ordered stack**: every layer can be dragged to any position in
  the Layers window; reordering is undoable and changes the final result because
  the fold runs left-to-right.
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
- **Geometry-changing effects** (crop, rotate, flip, face reshape —, occluding
  overlays, and the hard-stroke brushes **pen / mosaic / object removal**) mark
  the shared beauty geometry **stale**, so a beauty layer folded after them
  regenerates geometry against the changed image
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
- **Eye button default state**: Category A tools are **on** by default (full
  composite shown); Category B tools are **off** by default (isolated current
  effect shown). Leaving a tool resets the eye to its default state.
