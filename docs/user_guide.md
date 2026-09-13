# WhimsicalArt User Guide

WhimsicalArt is a free, ad-free Android photo editor. This guide walks through
the main features. Everything works offline and requires no account.

## Getting started

1. Allow photo access when prompted so the gallery can list your photos.
2. Pick a photo from the gallery to open it in the editor.

> **Sharing into the app**: if another app shares an image to WhimsicalArt, it
> opens **directly in the editor** — no photo permission is needed just to view
> a shared image.

### Sorting and grouping the gallery

Use **Sort / Group** in the gallery's top bar to order the grid — by **name,
path, size, last modified or date taken**, ascending or descending — and to
group photos by **folder, file type, extension**, or by **day/month** of last
modified / date taken. The choice is remembered for the next launch. Tap a group
to see the photos inside it, and use the back arrow at the top to return.

## The editor

The editor toolbar at the bottom lists every tool. Selecting a tool opens its
controls panel. Most tools are applied to the live preview in real time.

When you open a photo, the editor honours its stored orientation: photos that
carry rotated or mirrored **EXIF metadata** are already shown right-side up, and
that orientation is baked into the photo you edit (including the plain original
shown by hold-to-compare).

### Save and share

Tap the check mark in the top bar to open the save dialog. From there you can
save the photo or share it with another app. Configure:

- **Format** — JPEG or PNG (lossless)
- **Quality** — Original, High, Medium, or Low presets (JPEG quality)
- **Resolution** — Original, High, Medium, Low, or Upscale 2×/4×
- **Album / folder** — the destination folder, typed into the bordered field or
  picked with the **Browse** button beside it (system **folder picker**; the
  chosen location is remembered). The Quality and Resolution rows scroll
  horizontally when the window is narrow, so the preset chips never wrap.

### Leaving the editor

Pressing **Back** in the editor — the back arrow in the top bar or the system
back gesture — returns to the gallery. The editor tracks whether the photo is
**dirty** (has changes since it was opened or since the last successful **save
or share**), under one exhaustive rule: **any action that would change the
undo/redo history or the stacked layer list marks the photo dirty** — a
committed, merged, moved, removed or re-ordered layer; any tool's commit once
its effect lands in the stacked layers at lost focus (each beauty sub-tool is
its own tool and commits its own layer exactly like any other — see *Beauty — face /
ML sub-tools*); a
re-edit of an existing layer; and an undo or a redo themselves (a merge counts
as a change even though it flattens the layer list). A preview-only tweak that
changes neither the history nor the stack (a slider dragged back to its stored
value before the tool loses focus, a no-op commit identical to the previous
checkpoint) leaves the photo clean.

Only two events clear the dirty flag: **loading a new photo to edit** and a
successful **save or share** (the photo then matches its on-disk / shared
result). Nothing else resets it — undo and redo stay dirty, and a failed save or
share leaves the photo dirty.

If the photo is dirty, pressing Back shows a confirmation dialog asking whether
to **discard the changes** and leave the photo unedited, or **cancel** and keep
editing. Discarding continues back to the gallery; cancelling stays in the
editor with the work intact. The dialog only appears when the photo is dirty: a
photo right after a successful save or share exits straight to the gallery, and
any edit made after that brings the confirmation back.

### Adjustments

Brightness, contrast, saturation, exposure, shadows, highlights, temperature,
tint, and vignette each have a slider. Sharpening adds detail. **Denoise**
has its own **0–100% slider** (higher denoises more; keep it low to preserve
detail), and **Enhance** is a one-tap toggle on the same toolbar. Every
adjustment is a reorderable layer; see **Layers** below.

### Cutting, rotating, flipping

- **Crop** — choose an aspect ratio (Free, 1:1, 4:3, 3:2, 16:9, 9:16), then drag
  the selection rectangle's edges/corners (or pinch to scale it).
- **Transform** — rotate left/right in 90° steps, mirror horizontally or
  vertically, and use the **free rotate** slider for an arbitrary angle. **Twist
  with two fingers** on the photo to rotate it directly — the slider moves with
  your fingers and the angle wraps at 360°.

### Filters

The **Filters** tool splits into **Color Filters** (single-select colour-matrix
looks), **Style Filters** (Filmic/Vibrant/Matte tone-map looks), and **Lens
Filters** (multi-select physical lens effects: clear, UV, polarizers, ND,
infrared, soft, star, bokeh, anamorphic, astro, and **red-eye**). Select a
filter to preview it;
lens filters are multi-select controls of the same single Filters effect.

### Text

The **Text** tool adds editable text overlays. Type in the **multi-line** text
field, set the alignment using the **standard text-editor icons** (left /
centre / right / justify), colour (with opacity), font family and size, and the
text style with the familiar **Bold / Italic / Underline / Strikethrough** icon
buttons (any combination), plus a shadow (angle, distance, colour), an outline
(size, colour, blur), a rotation control that wraps at 360°, and a background
shape (none / rect / rounded / oval / pill / blur) with its own colour and
opacity if you want one — except blur, the last entry, which replaces those
with a single intensity slider (0% = nothing, 100% = most) and lays a soft-edged
oval of blurred photo behind the text, fading into the image. Recognized text
(OCR) starts with blur background enabled at a preset intensity. Every colour —
font, background, shadow and outline — is chosen
 from a **colour picker** — a compact row of the **most common colours** plus a
 **rainbow-coloured circle** that opens the full picker (preset swatches **plus**
 a custom colour wheel), and
 the shadow and outline each offer a **wide palette** (full rainbow + greys and
 neutrals), not just a few fixed swatches. The outline **blur** runs from a hard
edge at 0% to a soft glow that fades to transparent outward at 100%; and a
**translucent font** (opacity below 100%) keeps its own transparency — it never
fades into the outline colour. Text is **added automatically as you type**: the
first text lands at the canvas centre and each
next one just below the previous, so you can lay a caption stack quickly. Text
stays **editable** — tap any text on the canvas to select it (a selection grid
appears around it) and its content and settings are loaded back into the field
for in-place editing. **Drag** the selected text to move it, **pinch** to zoom
it, and **pinch + twist** to rotate it — the pinch is stabilised so zooming
alone never rotates, and an intentional twist is smooth and mirrored by the
rotation control. Drag a selection **corner** (or edge) to **resize** the text
proportionally — unlike the crop frame, text always scales uniformly and never
distorts. The **Add** button commits the field as
the next text. **Remove** deletes the selected text and **resets every control
to its default** (font size, colour, opacity, font, style, alignment, rotation,
shadow, outline and background) and **clears the field**, leaving the tool as
it was on first open — there is no separate Cancel button. **Recognize Text**
(OCR) converts text found in the photo into
the same editable text overlays, offered wherever the on-device OCR runtime is
available. The white grid always outlines only the text
itself, but you can also grab, pinch or tap it from a generous area around it
(its size plus a fat-finger allowance) — handy for small text; when several
texts overlap, taps target the one whose centre is nearest, while drag/pinch
keep working on the text you have already selected.

### Stickers

The **Stickers** tool adds stickers as layers. It opens with an **Emoji** option
that pops up the Android emoji picker so you can use any current emoji as a
sticker (rendered **at its full sticker size**, never cropped); a **Shapes**
section follows with tintable geometry stickers — **heart, star, circle, square,
triangle, flower, drop** — each with a **default fill colour** (e.g. heart red, star
yellow, drop cyan) you can change from the shared **fill colour** picker (compact row +
 rainbow circle → full popup). The other categories (Decoration,
Nature, Food, Animals, Objects) offer
bundled vector stickers. As a **last** option, a **Gallery** entry launches the
**phone's photo picker** so you can pick any image from your device gallery and
use it as a sticker — your own photo/art is placed, moved, scaled, rotated and
removed exactly like a bundled sticker (it is fixed-colour: its own pixels are
the sticker, no tint). A gallery sticker keeps its **original aspect ratio** —
a portrait photo stays taller than wide and a landscape one wider than tall, it
is never squeezed into a square. Stickers come in two
kinds: **colour-enabled** ones (the Shapes, and any tintable preset) where you
can pick a **fill colour**, plus — for those same stickers — an optional
**line**: a line **style** (None / solid / dashed / dotted), a **line
thickness** slider and a separate **line colour**, so you can outline the heart
or star; and fixed-colour ones — but **every** sticker has an
**opacity** slider and a **rotation** control (wraps at 360°). Stickers are
rendered as vectors, so scaling stays smooth (gallery photos are decoded from
their content URI and resampled with high-quality filtering). Tap a sticker to select it (a
selection grid appears), **drag** to move it, **pinch** to resize, **pinch +
twist** to rotate (stabilised — zooming alone never rotates), drag a selection
**corner** (or edge) to **resize** it proportionally (never free aspect, unlike
the crop frame), and press **Remove** to delete the selected sticker. The first
sticker lands at the canvas
centre and each next one just below the previous, so you can lay a quick sticker
stack. Placed stickers stay editable until you merge layers. The grid always
outlines only the sticker itself, but you can also grab, pinch or tap it from a
generous area around it (its size plus a fat-finger allowance); when stickers
overlap, taps target the one whose centre is nearest, while drag/pinch keep
working on the sticker you have already selected.

### Decorative tools

- **Mosaic** — a pixelation brush (size and opacity) with an eraser, plus an
  optional **"suggest faces"** helper that pre-marks detected faces for
  privacy masking. The effect is a single pixelation; there are no separate
  mosaic brush kinds.
- **Blur brush** — blur parts of the image by brushing over them.
- **Pen** — freehand drawing with solid, glow, neon, and rainbow brushes, colour
  picked from the **shared colour picker** (a compact row of the most common
  colours plus a **rainbow-coloured circle** that opens the full picker popup), a
  separate **opacity**, and — for the solid and rainbow brushes — a **hardness**
  control
  (100% = solid edge across the full size, 0% = only the centre pixel solid,
  fading outward). The colour picker is hidden for the rainbow brush (its hues
  shift along the stroke being drawn) and the hardness control is hidden for the
  glow and neon brushes (they don't use it). Strokes connect point-to-point as you
  draw, so a fast swipe always paints one fluid, gap-free line. Plus layer
  management and undo/redo.

### Beauty tools

The **Beauty** tools (from the editor top entry point) use face detection to
support skin smoothing, blemish and wrinkle removal, teeth whitening, face
reshaping, and makeup (lipstick, blush, eye shadow, eyeliner, foundation,
hair color). The makeup sub-tools and the **beauty pen** pick any colour from the
shared colour picker — the makeup presets plus black, and a rainbow circle that
opens the full custom picker. The **beauty pen** is a brush: drag on the photo to
paint a stroke (the eye button suspends painting and lets you pan/zoom instead).

### Collage

The **Collage** maker combines 2–9 photos into grid layouts (each photo can be
adjusted within its slot) with controls for borders, background, and templates.

### Object removal, background blur and background replace

Object removal provides a brush you paint over the object to remove with
content-aware fill. **Blur brush** and **Mosaic** work the same way, showing their
real effect (blurred patches / pixelated pattern) instead of a placeholder while
you paint. While you brush, the tool shows a simple selection overlay over the
marked area; the **actual effect** appears in the preview about **half a second
after you stop stroking** (either you lift your finger or hold still). If you start
stroking again before that half second is up (resuming after a brief pause or
lifting and re-touching quickly), the wait restarts — the real effect is only ever
applied after a full half second of uninterrupted stillness. Background blur
(bokeh) simulates portrait depth with edge-aware blur and manual brushing. Its
**intensity slider runs from 0 — where the photo stays sharp, nothing
blurred — up to 100% for a full-strength defocus, growing smoothly as you slide
it**. Blur (and Replace, below) finish at the subject's outline with a **soft fade of
just a few pixels** that blends the blurred background into the subject's edge,
so the result looks integrated instead of a hard cut.

**Background Replace** removes the photo's background completely along the
detected subject path — an inverted (negative) mask over the subject silhouette
— and draws the chosen background **behind** the subject instead. The boundary
is feathered the same way: the replacement fades into the subject edge over a
few pixels for a natural, integrated look.
Once an image is chosen the preview enters **placement mode**: a selection grid
frames the **actual placement image** — the replacement backdrop is drawn behind
the photo and your subject stays on top inside the grid. The grid works like the
Crop grid: drag a **corner or edge** (the 20% inner grab band, as with stickers
and text) to **resize** the backdrop proportionally and the **Size** slider moves
with your finger; drag inside the grid to **pan** it, **pinch** to scale,
**twist** or use the **Rotate** slider to turn it. **Free scale up** starts off,
so the backdrop can only shrink below 100% at first; switching it on **unlocks
sizes above 100%** (up to 2× / 200%) when a smaller subject cut-out needs a
bigger backdrop. **Switching to another background image (preset or gallery)
keeps your placement** — the grid's position, size and rotation carry over to
the new image untouched; only **Clear** resets them. The **eye** is off by
default while placing and the gestures
belong to the grid; turn it on to view the composite and return to ordinary
pan/zoom. Placement never crops — scaling past the canvas grows the saved frame,
so the final framing is cropped afterwards. With no image selected (or in blur
mode) the tool behaves like any normal layer (eye on, pan/zoom always
available).

### Frames

Add a decorative border around your photo with the **Frames** tool. There are
two **tiers**, each in its own labelled section: an **inner frame** (drawn
inward onto the photo) and an **outer frame** (drawn outward from the photo's
edge — when enabled the outer frame also **makes the saved image larger**). Both
default to **"None"** (no frame). Pick a **style** per tier — a straight
**solid** band, **rounded**, **beveled**, **oval**, and for the inner tier
**heart**, or one of the **edge & bevel profiles** used on real picture frames
and furniture (inward curve edge, corner eased edge, gear edge, ogee edge,
double ogee edge, dupont edge, cove edge, french cove edge, dupont + ½ bullnose
edge, ogee cove edge). There are also ornamental and moulding edges to browse
(wavy, sawtooth, cogged, floral, waterfall, single wave, twin wave, recessed,
offset wave, water waves inward, water waves outward), and a special **Custom
(Curved)** style with its own **angle**, **curve distance**, **strength** and
**count** sliders for shaping the edge curve by hand. Most shapes and every edge
profile also give you an **edge size** slider (how the corner or edge treatment
runs along the edge — from a squared corner all the way to a treatment that
takes half the short edge). Set a **thickness** with a
slider (how far the band reaches in from the edge — out from the photo's edge
for the outer tier), and set its **colour** from
the shared colour picker (a compact row of the most common colours plus a
**rainbow-coloured circle** that opens the full picker — swatches + custom
palette). Every style also offers **frame lines** — switch **lines** on to show a
**lines** count (1–10), a **distance** between them and a **line thickness** (off
draws none), drawing concentric decorative lines inward (inner tier) or outward
(outer tier) from the band.
Each style shows a small example
around the live preview, so the result is visible before you commit. Pick
"None" for no border. A frame defaults to **black** when you pick a style; a
**translucent** frame colour blends with whatever is behind it, as you'd expect.

### Layers & preview

Every applied edit is a **layer** in an ordered, reversible stack. Open the
**Layers** window to see them, drag rows to reorder, tap a row to re-edit that
tool, and use **Merge Layers** to flatten (or the ✕ to delete a layer). While
you drag a layer by its handle, the row **follows your finger as a ghost** and
the other rows **shift aside** to show a blank **drop slot** marking where the
layer will land when you release — drag to the slot you want and let go.

The editor shows the photo on a **letterboxed canvas** whose layout follows the
**available window area** (not the device's reported orientation — split-screen
and multi-window sizes resolve the same way). The canvas and the tool controls
**share one budget, split letterbox-first**: the canvas takes its **square cap**
when the window affords it, otherwise its **50% floor**, and the **controls
absorb every dp the letterbox leaves unused** — they have **no percentage
ceiling** (they fill the remainder past a square canvas, typically 35–50% of the
height). The **eye row** keeps its own fixed 56 dp height under the canvas, and
the controls keep a **minimum** so they never disappear on a cramped window (a
`200 dp × uiScale` minimum in portrait, a **`600 dp × uiScale`-wide minimum** for
the landscape panel). **Portrait** (window taller than wide): the canvas spans
the full available width above the eye row and its height is the **letterbox
split** of the shared budget `available height = visible app height − 56 dp ×
uiScale`: square cap (`= width`) when `budget ≥ width + controls minimum`,
otherwise the **50% floor**, with the controls filling the remainder — a perfect
square when the width (the square cap) bounds it, a wide letterbox when the 50%
floor binds. In portrait the canvas never falls below its floor: the controls
minimum is at most 30% of the budget, always under half. **Landscape** (window wider than tall): the tool bar and the tool
controls move to a **panel on the right**, the canvas sits **left** filling the
height between the top bar and the eye row, and the width is the **letterbox
split** of the window width with the same two-level rule (square cap =
`visible app height − 56 dp × uiScale` → 50% floor → panel minimum/remainder,
with the right panel keeping its `600 dp × uiScale` minimum — or `70%` of the
window width when that is smaller, the one case the canvas dips below its
floor). The
**eye row stays below the canvas** in both orientations. When the window is smaller than the app's **minimum workable
size** — 320×640 dp in portrait, 912×432 dp in landscape (a split-screen window,
for example) — a **UI scale factor applies across the entire app** (gallery,
settings, camera, collage, editor and every dialog), shrinking the tool bars,
controls, dialogs, icons and their paddings/fonts (down to a 0.6 floor) so the
controls never crowd out the photo — the canvas and the photo keep their usable
size. The controls always reserve their minimum space, the image
is fit (letterboxed) inside the canvas, and panels that are taller than their
space (Stickers and Text are the tallest) **scroll vertically** inside their
area. Every tool's controls — including **Crop**'s aspect-ratio selector and
**Transform**'s rotate/flip row — sit in the same controls panel **below the
bottom tool bar** (or in the right panel, in landscape), like every other
tool's panel. All canvas gestures —
**pinch to zoom, drag to pan**, hold-to-compare, and
each tool's own drag/place gesture — respond **only while touching the canvas**;
dragging over the controls scrolls the controls instead and never pans/zooms the
photo.

The **eye** button controls whether later (tail) effects are folded into the
preview — on by default for adjustments and other photo-look tools, off by
default for transform and draw/place tools. On draw/place tools (crop, frames,
stickers, text, brushes) and on transform the eye also enables pan/zoom and
hold-to-compare. See
  `docs/effects_reference.md` for the full per-effect rules.

## Keyboard navigation

On devices with a hardware keyboard:

- **Left/Right arrow** — cycle between editor tools.
- **Up/Down arrow** — nudge the active adjustment value (when an
  adjustment tool such as brightness or contrast is selected).

## Compare original

Press and hold the image preview to view the **plain original** (uncropped and
untransformed) and confirm your edits, then release to return to the edited
view. On draw/place tools (which use a single-finger drag for their own
action), enable the **eye** first — then press and hold works the same way.

## Undo and redo

Use the Undo/Redo buttons in the top bar. Undo restores the whole edit document —
adjustments, transforms, crops, filters, and layers — one step at a time.

## Privacy

WhimsicalArt works offline. Your photos stay on your device and are never
uploaded.
