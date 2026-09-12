# Effect Stack — Reversible / Non-Destructive Editing

> Authoritative reference for the editor's **reversible, non-destructive effect
> stack**. It describes how layers are represented, folded, **committed on lost
> focus**, reordered, merged, previewed and undone — as implemented in
> `feature:editor`.
>
> Per-effect **preview interaction** (pan/zoom, the eye / visibility toggle,
> hold-to-compare with the plain original) and the UI behaviour of each tool are
> catalogued in `docs/effects_reference.md`. The module/architecture overview is
> in `docs/development.md`.

## Goal

Every editor tool is **reversible and remembers its own parameters**. A tool
with a slider remembers its last position; when the user switches away and back,
the slider shows the previous position and moving it re-applies the effect from
the remembered baseline.

When the user **leaves a tool** (deselect it, open the Layers window, or Save) —
"**lost focus**" — the tool's current parameters are **committed** into the
stack, and that committed result becomes a **baked input bitmap** for every
effect applied after it. The effect remains **fully reversible** by returning to
it, which recomputes the composite *before* it, restores the saved parameters
into the controls, and re-commits at the same stack position. Nothing is
permanently baked into the source (`originalImage`, which is never mutated);
only the working composite advances one committed milestone at a time.

Because effects can be applied in arbitrary order and order affects the final
result, this needs an **ordered, parameterised effect stack** — not a growing set
of independent boolean toggles.

> **The one rule that has no exception: every effect commits to a bitmap.**
> Every tool's *committed* result is a **bitmap** — the effect draws/paints/writes
> its result into a pixel buffer, and that buffer is the **input for the next
> effect**. No effect may persist as a *display-only* viewport transform (a
> rotation, flip, crop or frame that is applied purely in the preview and never
> rasterised onto the working bitmap). A lightweight on-canvas preview **while the
> tool is focused** is fine and expected; the moment the tool **commits** (lost
> focus), the change **must be rasterised into the committed bitmap**. Concretely,
> a rotate/flip/crop takes the current bitmap and **emits a new bitmap with the
> rotation/flip drawn inside the pixels** — it does **not** leave the canvas
> rotated and the effect "still pending". A frame draws its border **into** the
> bitmap. So `effect.render(current, ctx)` always returns a bitmap that already
> contains the effect's result — later effects never re-derive geometry or
> transforms from an earlier effect that was applied "in the viewport only".

## Core model

The editor keeps the pristine source plus the **ordered effect list**; every
render is a **left-to-right fold** over that list starting from the pristine
photo:

- **`originalImage`** — the untouched source bitmap. Always kept, **never
  mutated**. Used for hold-to-compare (press and hold reveals the original). At
  the start it **is** `firstFull` (a reference); it stays **never discarded**
  even after a merge, while `firstFull` is then repointed to the merged result
  (see *The full-size image model*).
- **`effectStack: List<StackEffect>`** — the **single, ordered, parameterised**
  effect list. **One** list holds **every** effect — adjustments,
  filters, overlays, frames, **and every beauty sub-tool layer** — as a
  `StackEffect` entry. A `BeautyStackEffect` is just another
  `StackEffect`; **no separate beauty store** exists. Each entry stores the
  full parameters of the tool at the moment it was **committed** (its last
  values on lost focus). Order matters: effect _N_ input is the committed output
  of effects _0..N-1_, so a beauty effect can sit anywhere and be interleaved
  freely (normal → beauty → normal → beauty → …). The user reorders the one
  list; `firstFull` is the base of the whole fold (see *The full-size image
  model*).
- **Committed milestones.** Each tool, when left ("lost focus") with **changed,
  non-default** parameters, is **committed**: its parameters are stored on its
  stack entry **and** its result is baked into a new **committed input bitmap**
  that becomes the input for the next effect. The working composite advances one
  committed milestone per tool. Committing is reversible — re-entering the tool
  recomputes the composite *before* it, restores the params, and re-commits at
  the **same stack position**, triggering a cascade fold of the tail.
- **`lastImage`** — the **full-size final presentation image**: the full-size
  composite of folding the whole committed stack **including the current effect**
  over `firstFull`. It is the last item of the fold (**`N`**), the final
  result, and is also the input for any next tool. It is what becomes a single
  **Merged** layer. `lastImage` is **full-size** and **lazily generated on
  demand** — the demand may be a **save / merge / share** operation (save always
  uses or triggers it) or a display whose pixels outgrow the preview-sized
  image (`displayScale` > 1). It is **never produced during the common
  interactive path**, and the canvas does **not** show it by default. What the
  canvas shows **by default** — given **no selected tool took over the canvas**
  and the **`displayScale` ≤ 1** — is the **last preview-sized image of the
  layer stack**: the per-layer preview of the last committed layer, a cheap
  small image produced by the preview path (see *Per-layer preview cache* and
  *Previewing a tool*). The eye toggle and the `displayScale` / staleness rules
  below decide whether the canvas shows that preview-sized image or, when it is
  present and fresh, the full-size `lastImage` (see *Zooming past 100% —
  `displayScale` > 1*).
- **`prevImage`** — the **base for the last effect** (**`N-1`**): the composite of
  every committed effect *before* the **last** one — the input the last effect is
  applied to produce `lastImage`. It is needed because the last effect
remains be applied **on top of** `prevImage`, never on top of `lastImage` (which
   would re-apply it a second time) — and, on revert of the last tool, because
   `lastImage` must fall back to `prevImage`. It is **full-size** and is used
   **on demand only** (lazily computed when a full-size result — `displayScale` > 1 /
  save / merge / share — is needed); the interactive path never allocates it.
- **`currentImage`** — the **base for an effect in the middle of the stack**: a
  composite sitting **between the origin and `prevImage`** (`>0` and `<N-1`) — the
  composite of every committed effect *before* the tool currently being edited
  (the base that tool is applied to). On first entry of a brand-new tool,
  `currentImage` = `lastImage`; on re-entry of an existing tool, `currentImage` =
  the fold of effects `0..index-1` (see *Choosing the base image* below). It is
  **full-size** and **on demand only**, populated only when a mid-stack effect
  actually needs a full-size base.

**`currentImage` and `prevImage` coincide when the current tool is the last
effect** — then "the composite before the edited tool" *is* "the composite before
the last effect" (both `N-1`). In that case the same cached image plays both roles.

**Scale terms (unified spec vocabulary).** Effect-purpose scales — a sticker /
text / placement element's own `scale` (its committed size multiplier), a
stroke width, a frame's line thickness — are tool-local parameters and keep
their tool-specific names. Every OTHER scale in this spec has exactly one of
these five names:

- **`uiScale`** — the **UI chrome layout scale** (0.6–1.0): a window-relative
  factor applied to every control, panel, dialog, padding and font (and the
  200/600 dp control reserves). Layout only — never part of image/preview pixel
  math.
- **`canvasZoom`** — the **user's viewport magnification** relative to the
  fitted letterbox image (1.0 = fitted; pinch-clamped 0.5×–5×). Display-only,
  held in the shared `ViewportTransform` together with pan/rotation/flips.
- **`previewScale`** — the **fold resolution ratio** (≤ 1): how much smaller a
  preview-sized fold is than its full-size base (1.0 = full pixel resolution).
  It is the resolution key of every preview-sized fold and cache.
- **`canvasFitScale`** — the **letterbox fit ratio**: a bitmap's on-screen side
  divided by its own pixel side (how much the preview-sized image is shrunk or
  grown to fill the letterbox), before any user zoom.
- **`displayScale`** = `canvasFitScale` × `canvasZoom` — the total on-screen
  magnification of the preview-sized image in device pixels (preview px →
  screen px). **`displayScale` > 1 is the one condition that means "the visible
  pixels outgrow the preview-sized image — use the full-size cache image"**;
  every "full size when zoom is past 100%" rule below is exactly that
  condition.

### The full-size image model

The full-size images are: **`originalImage`**, **`firstFull`**, **`currentImage`**,
**`prevImage`** and **`lastImage`**. A preview-sized image,
**`firstPreview`**, is computed once on editor load (see *Preview resolution*).

- **`originalImage`** — the pristine, true full-size source photo. **Guaranteed to
  always exist and is immutable** — never mutated nor discarded, including after
  a merge. It is used **for hold-to-compare** (press-and-hold reveals the plain
  pristine photo) and is the **ultimate base of every from-base replay**. It
  carries **no staleness flag and no resume cursor** — it is always the pristine
  starting point.
- **`firstFull`** — the full-size image **before any effect is applied** (the
  "first" full image). It is **always present** and never needs generating —
  a bounded decode of the pristine photo, functionally interchangeable with
  `originalImage` before any merge. The **only** thing that ever changes
  `firstFull` is **Merge Layers**: a merge
  bakes every effect into a single flattened result, and at that point
  `firstFull` is repointed to the **full-size result of the merge** (a fresh
  instance; `originalImage` itself stays frozen). Concretely:
  - **hold-to-compare always uses `originalImage`** (never `firstFull`, so compare
    still reveals the pristine photo even after a merge);
  - **folding the entire prefix** (a from-base re-fold) **uses `firstFull`** as
    the base — equivalent to `originalImage` before any merge, and to the merged
    result after one.
- **`lastImage`** — the **full-size final** image: the composite of the
  whole committed stack (the last effect applied). It is the final result, the
  input for the next tool, and what becomes a single **Merged** layer. Lazily
  generated; when fresh it is the answer to any "fold the whole stack" request.
- **`prevImage`** — the full-size composite of every committed effect
  **before the last one** (`N-1`) — the input the last effect is applied to
  (so the last tool folds over it, never over `lastImage`). Coincides with
  `currentImage` when the edited tool is the last effect. Lazily generated.
- **`currentImage`** — the full-size composite of every effect
  **before an effect in the middle of the stack** (`>0` and `<N-1`) — the base
  the edited tool is applied to. Unlike `prevImage`/`lastImage`, **`currentImage`
  carries a `currentPosition`** (which layer it was folded to), because it may
  reference an arbitrary layer that is neither first, last nor `N-1`. Lazily
  generated.

**`currentImage`, `prevImage` and `lastImage` are full-size on-demand caches**
produced lazily when a full-size result is actually needed (`displayScale` > 1 /
save / merge / share), never eagerly during interactive editing. The interactive/common
path never touches them: it folds from the **per-layer committed preview bitmaps**
(see *Performance / preview cache*). Each of `firstFull`, `currentImage`,
`prevImage` and `lastImage` carries a **staleness flag** plus a record of **up to
which tool it was applied** (a resume cursor) — so its fold can resume instead of
restarting from the pristine base (see *Resumable fold* and *Choosing the base
image*). `originalImage` is the sole image with neither.

**In-flight (uncommitted) effects draw on the canvas directly.** While a tool is
focused, its live uncommitted result is applied straight onto the canvas on top
of the displayed preview image — the in-flight effect usually **does not need to
cache a bitmap at all**, it applies itself to the canvas directly. If some effect
does need a temporary backing image, that image is referred to as a **preview
image** — deliberately a different name from `lastImage`, so the full-size final
composite is never confused with the canvas display.

Re-render is a **left-to-right fold**: `current = effect.render(current,
 context)` for each effect, starting from the pristine base. The fold recycles
superseded intermediates but never the caller-owned base.

> **`currentImage`/`prevImage` are full-size on-demand caches, not the interactive
> mechanism** (see above for the on-demand-only rule). When a
> full-size result is needed, a re-run touches **only the current effect plus
> everything after it** (the tail) instead of re-folding from the pristine base;
> using the caches never changes the produced image — the fold itself is identical
> either way; the cache only avoids repeating the *prefix* part of the fold. The
> canvas's default display is the **preview image** — the last preview-sized image
> of the layer stack, produced by the preview path — see *Previewing a tool*.

## The fold renderer

The fold the editor actually runs is `renderStackWithBeautyGeometry` — a method
on `EditorViewModel` — the beauty-aware fold built around the generic
left-to-right `CascadeRenderer.fold`
(`feature/editor/.../domain/CascadeRenderer.kt`). The same engine is reused for
**both** the per-layer preview images and the final full-size image, so a layer
preview is byte-consistent with the saved result (different layer previews and
the final image are the same fold at different resolutions).

- Each `StackEffect` implements a single self-contained method
  `render(current: Bitmap, context: Context): Bitmap`: it knows its own
  parameters and applies them to the running image, returning the next image.
  A transforming effect returns a **new** bitmap (leaving `current` owned by
  the fold); an overlay / draw-in-place effect copies `current`, draws into the
  copy and returns it (`current` itself is never recycled, so the fold stays
  correct); a no-op returns `current`.
- **Order matters** — effect _N_ input is the committed output of effects `_0..N-1` —
  i.e. each effect consumes the **bitmap baked by the previous committed effect**,
  never a raw geometric override that re-applies itself.
- Each adjustment and filter applies its **own single matrix (or per-pixel)
  pass** in stack order — there is deliberately no "combine everything into one
  colour matrix" shortcut, so each saved layer can be re-edged independently.
  `EditorColorMatrix.singleAdjustmentMatrix(tool, value)` provides the per-effect
  matrix path.
- **Transform and crop are ordinary, independent layers** — not "base steps".
  `TransformEffect` rotates/flips the running composite; `CropEffect` restricts
  it to the selected region. There is **no fixed order between them or any other
  effect**: whichever comes first in the user's stack consumes what is before
  it, and the next effect consumes its result. A crop's rect is normalised
  against the composite **at the crop's own position** (not a pre-oriented
  image); effects placed before a transform are transformed together with it.
  All decided purely by stack order — no effect is the "base" of another.
- The one-layered-per-effect rule is strict — **including for beauty**: a beauty
  sub-tool is **its own `BeautyStackEffect` layer with its own parameters**, and
  each is applied **one by one, independently, onto stacked layers**. Even when a
  family of edits target the same region, they are **not** merged into one
  composite layer: for example face reshape is **not** a single combined "slim +
  enlarge + nose + jaw" effect — instead each operation (slim, eye enlarge, nose,
  jaw, …) is a **dedicated effect** with its **own parameters** and its **own
  stack entry**, applied in order so they stack independently and stay separately
  reversible / re-editable.
- Bitmap lifetime: `base` is owned by the caller and never recycled here;
  transforming effects recycle superseded intermediates; draw-in-place effects
  return the same bitmap they were given, so they are never recycled.

### Beauty-aware fold

Where the composite contains beauty, the editor uses
`renderStackWithBeautyGeometry(context, base, effects)` (the salutary sibling of
`CascadeRenderer.fold`). It is the **same left-to-right fold**, with one extra
obligation: before rendering a `BeautyStackEffect` it resolves that layer's ML
geometry **lazily against the running image at its fold position** (see the
*Beauty — per-sub-tool layers + geometry* section below).
Within one fold pass, consecutive beauty layers with **no geometry-changing
effect between them**
**share one resolved geometry** (ML runs once per segment); an effect that
**changes geometry** marks the shared context **stale** and
clears the current segment so the following beauty layer regenerates against the
changed image. Each beauty layer renders with the geometry it just resolved
(stored as its own parameter), so the fold and editor never pick geometry. The
same method is used for the committed save/merge/share fold **and** the
per-layer preview images (which double as the Layers-window thumbnails), so a
post-deform beauty layer's preview reflects the same per-position result.

**Which effects change geometry.** Geometry is not invalidated by most effects —
only by those that actually reposition or occlude the subject the geometry must
outline:

- **Pixel deformations** — crop, rotate/flip/transform, and every face-reshape
  dimension (`BeautySlimEffect` / `BeautyEyeEnlargeEffect` /
  `BeautyNoseEffect` / `BeautyJawEffect`).
- **Background replacement in Replace mode** — the placement transform lets the
  user **move, scale and rotate the cut-out subject** over the replacement
  backdrop (`BackgroundReplacer.place` transforms the subject; the backdrop
  stays static), so a REPLACE layer **with a placement image selected** is a
  **deforming** task: downstream geometry / subject masks must fold the
  placement to reach the subject exactly where it is drawn. **Blur mode** (and
  a REPLACE layer without a placement image — the plain remove-flatten) are
  **not** deforming — they leave the subject's pixels in place. The background
  layer's `changesGeometry` reflects this: true only for REPLACE with a
  placement image.
- **Occluding overlays** — stickers, text, frames — which can place something
  **over the subject** that the geometry needs to outline around.
- **Hard-stroke brush tools** — the set that hard-covers the subject:
  **pen, mosaic, and object removal**. **Face makeup** brushes (and the **blur
  brush**) do **not** hard-cover and do **not** invalidate geometry — face makeup
  maps onto the existing facial geometry rather than redrawing it.

Everything else (colour adjustments, filters, smooth/teeth/spot/skin-tone beauty,
etc.) leaves geometry untouched.

## Performance / preview cache

Interactive editing feels instant and memory stays flat because the common path
never touches a full-size bitmap. The architecture is a **per-layer preview-sized
cache** plus a **lazily computed, resumable full-size fold**. Both
**`originalImage`** and **`firstFull`** are always present at full size; every
other image
(`currentImage`, `prevImage`, the full-size `lastImage`, the final composite) is
produced **on demand** — the canvas's default display is the **last preview-sized
image of the layer stack** (the per-layer preview of the last committed layer),
always produced during editing, which is a **preview image** — never confused
with the lazy full-size `lastImage` (see *All `lastImage` logic is lazy*).

> **Preview rendering model — base image, tool overlay, official fold.**
> Every effect follows the same three rules:
>
> 1. **Base image is always preview-sized.** The fold base is the original image
>    shrunk to the preview ceiling (see *Choosing preview resolution* below). For
>    the very first layer this is **`firstPreview`** — the always-present,
>    load-time, lohalo-downscaled preview base (when the preview ceiling changes
>    between sessions it is produced once via an equivalent downscale of
>    `firstFull`); for every later layer it is the *previous* layer's
>    committed preview bitmap — the small, already-folded result carried on the
>    layer's own parameter (see *Per-layer preview cache*). No effect ever
>    receives a full-size base during interactive editing.
>
> 2. **The preview does not require a prefix fold at `displayScale` ≤ 1.** The
>    composite image shown on the canvas is either (a) the fold of all preceding
>    effects at preview resolution (Category A tools, eye ON) or (b) the raw
>    source with a GPU colour-matrix / overlay applied on top (Category B
>    source-space tools, eye OFF) — both derived from the preview-sized base
>    without re-folding the full prefix. The official fold method
>    (`StackEffect.render`) always produces a bitmap at the current scale and is
>    the canonical result; and a tool may additionally draw a **lightweight
>    overlay** (stroke / grid / sticker / text) on top of the preview-sized base
>    as a faster stand-in — **but only while the eye renders the current effect
>    alone (the tail hidden), and under a strict parity contract**: the overlay
>    is never an approximation, it draws the same pixels the layer's `render`
>    would produce at the preview resolution — identical normalised
>    source-fraction placement / size / rotation, style and glyphs — so the
>    float and the committed raster are interchangeable. A visible difference
>    between the live overlay and the rasterised layer is a bug. Every pen brush
>    stand-in *is* the committed raster drawn back onto the canvas (max-alpha
>    accumulation — see *Pen controls*), so overlap behaviour at bends and
>    crossovers between preview and fold is shared by construction.
>
> 3. **Full-size is produced only on demand, in background, and replaced when
>    ready.** A full-size composite (`lastImage`, scale = 1) is computed
>    exclusively when the display outgrows the preview-sized image
>    (**`displayScale` > 1** — colloquially zooming past ~100%, i.e. past the
>    preview bitmap's native resolution; a debounced background fold,
>    [requestFullFold]) or a full-res export is needed (save / merge / share).
>    Until the full-size result is ready the preview-sized image stays on screen
>    unchanged; once ready it replaces the preview and remains in use even if
>    the `displayScale` drops back below 1 (it is strictly sharper). When the
>    tail is visible (eye ON on a Category B tool), the preview-sized tail fold
>    is always computed first regardless of `displayScale`; the full-size prefix
>    fold is launched in the background only when `displayScale` exceeds 1.
>
> > **When the eye cascades the tail and tail effects exist, there is no
> > lightweight alternative for the current effect**: the fold **rasterises the
> > current tool's own layer into the preview-sized bitmap** (source-space tools
> > fold with `excludeCurrentTool = false`), so the tail feeds from a real image
> > — the current effect is never left as a floating overlay once a tail must
> > cascade over it. The floating stand-in is reserved exclusively for the
> > eye-OFF single-effect preview. If the eye only shows the current effect, or
> > there are no tail effects, the tool MAY keep using the lightweight overlay
> > (or rasterise directly if that is cheap) — the parity contract decides.
>
> 4. **Every effect is dimension-agnostic — one effect, two scales, applied
>    twice only when needed.** The **very same effect code** renders the
>    preview-sized base and the full-size composite — no effect behaves
>    differently, or enables a different algorithm, based on its input's
>    resolution. All size-bearing parameters (stroke widths, blur radius,
>    sticker/text scale, line thickness, frame/edge extents, soft-edge bands,
>    geometry coordinates) are **normalised fraction-of-the-image quantities**
>    (or relative to the short edge) — never fixed pixel amounts nor
>    "full-detail only" quantities — so an effect applied to the preview-sized
>    base and applied to the full-size composite is **the same effect at two
>    scales**, scaling proportionally in both axes. "Evaluating the full-size
>    picture" happens **only** on **`displayScale` > 1**, **merge**, or
>    **save/share** — and that rule is literal: a display that outgrows the
>    preview-sized image applies the current effect
>    **twice** — a preview-sized pass for the display, then a full-size pass in
>    the background that **replaces** the preview-sized result **only when it is
>    ready**. The ML pipelines follow the same rule: geometry (beauty face
>    landmarks/masks and the background tool's subject mask) first resolves
>    against the **preview-sized** base and is used immediately, then the
>    full-quality geometry is resolved in the background at the **geometry-track
>    resolution — the adaptive 1–4 MP budget, never the full-size photo** (see
>    *Lazy beauty geometry* and *Adaptive ML input resolution*).

### Per-layer preview cache

> **Each layer produces and carries a "committed preview bitmap" on commit — the
> base every later layer folds from.** When a tool **commits** (lost focus, non-default
> change), the fold renders that layer's result at **preview resolution** and that
> **preview-sized bitmap is the layer's own committed preview** — a field carried on the
> layer for the session (never serialized: undo/redo restores the same layer object),
> the image the *next* layer treats as its **base**. Because it is committed with the
> layer, a preview-sized image **always exists** for the current edit session for
> every layer up to and including the last committed one, so:
> - the **first** layer has no previous layer, so it folds from **`firstPreview`**
>   as its base (never full-size); **this invariant is unconditional — the very
>   first committed layer, whatever its tool is (a frame included), always renders
>   from a preview-sized copy of the original, never from a missing or stale
>   "previous layer" base**; in particular the first layer after a **merge**
>   folds from the post-merge `firstPreview` (rebuilt from the merged
>   `firstFull`);
> - every later layer folds **from the previous layer's committed preview bitmap** —
>   one cheap preview-sized effect applied on top of it — byte-consistent with a
>   from-base re-fold at the same resolution;
> - inspecting / reordering layers, panning and the Layers-window thumbnails all
>   serve directly from these always-present per-layer previews.
>
> **Re-editing only re-renders when the parameters actually changed.** Re-entering
> a committed layer compares its live control values against the values **stored in
> the layer** (for a never-committed tool, against **defaults**). If they match, the
> layer's committed preview bitmap – and the whole tail after it – is **untouched**:
> **no re-render, no staleness**. Only when the values differ does the editor
> regenerate that layer's preview bitmap and re-fold the **tail after it** — every
> later layer's committed preview is re-folded in order from the edited layer's new
> preview (one cheap preview-sized effect each). A change that is undone **without
> leaving the tool** (dragging a slider away and back to its committed value before
> committing) matches the stored value at the next compare, so **no replacement and
> no tail staleness occurs** — nothing is re-rendered for it.
>
> **Structural changes regenerate the previews of the moved/removed layer and the
> affected tail.** Moving a layer to another position or **deleting** a layer
> re-folds the per-layer committed previews for that layer **and every layer that
> follows it** (the prefix before the change keeps its previews untouched): after a
> **removal**, the layers that came after the removed one fold from the previous
> layer's new preview; after a **move**, the affected segment re-folds from the new
> ordering. The full-size caches after the change are likewise marked stale (and
> regenerated on demand only). The untouched prefix is never re-rendered.
>
> **Full-size images remain lazy.** The full-size composite (and each layer's
> full-size output) is produced **only on demand**: when the **`displayScale`
> exceeds 1** (the display outgrows the preview-sized image) or a **full-res
> result (save / merge / share)** is needed. Everything in the
> common path (viewing, editing, inspecting, reordering, thumbnails) runs against the
> always-present preview-sized bitmaps. The fold cache then resumes from the nearest
> committed preview, so a full-size pass never re-runs an unchanged prefix.
>
> **Geometry tools have a different flow — and use provisional preview geometry.**
> Geometry-requiring tools (beauty reshape and the background tool's subject
> mask) may or may not need a full-size
> image, but they do **not** apply every effect: they reach a layer's position by
> folding **only the deforming effects** across the gap (crop / transform / reshape /
> occluding overlays / hard-stroke brushes / a background Replace placement — see
> *Original geometry image and original geometry*). On entry the geometry is resolved against a **preview-sized
> base** — the previous layer's committed preview bitmap (the pristine source
> shrunk when no layer precedes the gap) — giving **provisional geometry**: correct
> for interactive editing, but only a stand-in, because that base already contains
> all effects (including deforming ones) and may be smaller than the selected
> geometry dimension (the adaptive 1 MP–4 MP ML budget). The real geometry is then
> resolved in the background **on the geometry track** — folding the deforming
> effects over `originalGeometryImage` at the **geometry-track resolution (the
> adaptive 1–4 MP budget — never folded over the full-size original)** —
> and swapped in; the committed layer always carries the final full-quality
> geometry
> (for background the subject mask is resolved against that track base, so it
> already sits in the deformed/cropped/rotated pixel space).
> See *Lazy beauty geometry* below.

- Each layer in the stack keeps a **preview-sized cached image** — a small
  downscaled fold result for that layer, sized to the on-screen pixel footprint
  (see *Choosing preview resolution* below).
- **Moving across layers** — inspecting a layer, tapping around the Layers
  window, dragging to reorder — uses the **preview cache first**. It is cheap,
  already present for every layer, and gives an instant response without running
  a full fold.
- The **preview image doubles as the row thumbnail** in the Layers window (scaled
  to the ≤100×100 box), so separate thumbnail generation is dropped entirely —
  we always have one per layer anyway.
- A layer's preview is derived from the layer's committed output at preview
  resolution. It respects the commit-on-lost-focus / one-rule-of-thumb model —
  it is just a smaller render of the same fold, so it stays byte-consistent in
  spirit with the full-size result (see *Consistency* below).

### Choosing preview resolution (`previewSizeMaxSide` and `firstPreview`)

**`previewSizeMaxSide` is fixed at editor load.** On loading the editor, the
preview ceiling is computed **once** from the **device dimensions**, as the
larger of the two orientation-aware bounds —

`previewSizeMaxSide = max(min(deviceWidthPx, deviceHeightPx),
max(deviceWidthPx, deviceHeightPx) − 600dp)`

(the `600dp` term is converted to **pixels at the device density**; it is the
**right controls panel** that the landscape layout reserves — always the raw,
**unscaled** constant, independent of the per-window `uiScale`). In landscape the
canvas is bounded by `availableWidth − 600dp` on the wide side, so the preview
never needs a side larger than `max(deviceWidthPx, deviceHeightPx) − 600dp`;
in portrait the binding bound is the device's shorter side
`min(deviceWidthPx, deviceHeightPx)`. The ceiling is the larger of the two,
computed **once** at load — *not* from the current orientation's shorter side.
Because a device can be used in either portrait or landscape and the user can
rotate the app at any time, the preview-sized images are dimensioned against
this **worst-case bounding side**, so they stay correctly sized for **any**
orientation without ever being re-dimensioned on rotation. It is computed from
the **device's physical pixel dimensions**, never from the size of the app
window: a multi-window or split-screen resize must not change the preview
ceiling (the window's `uiScale` factors the layout reserves, not this bound),
so the preview/fold resolution stays stable no matter how the window is
sized.

**The preview ceiling never follows the window — the layout does.** The ceiling
is deliberately **decoupled from the canvas box**: the canvas layout, the
controls panel and the letterboxing adapt to whatever the window provides (see
*Canvas layout and gesture scope* in `effects_reference.md`), and the
**on-screen footprint at a given `canvasZoom` is served by the per-frame
`previewScale` multiplier** (below), which already downscales the folded image
to the visible viewport. That multiplier is what handles a small split-screen window; the
ceiling itself stays the fixed device bound from load. Concretely: after a
window resize the ceiling is **not recomputed** and `firstPreview` is **not
re-generated**; the next fold simply uses a smaller `previewScale` from the
same fixed base. Making the ceiling window-derived would re-dimension every
preview-sized image on every resize/split/orientation change (and drop the
memoized base mid-render), which is exactly the instability this model avoids.

From it, the editor generates the **`firstPreview` image on load**:
`firstPreview` is a **preview-sized** image, produced by **lohalo
interpolation** of the pristine `originalImage`, whose **maximum side equals
`previewSizeMaxSide`** and whose **other side respects the original image's
aspect ratio**. `firstPreview` is therefore **always present** (like
`originalImage` and `firstFull`) and can **always** be used as the base image for
the **first effect** — whether it is the literal first effect of the session, or
the first after a **merge** (the merged stack's first layer folds from
`firstPreview`).
It is the preview-resolution counterpart of `firstFull`: just as folding the
entire prefix uses `firstFull`, previewing the entire prefix uses
`firstPreview`.

The **`previewScale` multiplier can change after a dimension-affecting commit**
(a crop restricts the footprint; a transform rotates it, swapping or growing the
canvas), so the per-layer preview dimensions are **not a fixed constant**. Each
layer's desired preview dimensions are computed from the **visible area** — the
pixel footprint of the region where the preview will actually be drawn — over
the layer's **estimated full-size output**:

```
previewSize = visibleDrawnPixels(viewWidth, viewHeight, canvasZoom)
            + margin                       // some headroom so it looks sharp while panning
previewScale = previewSize / estimatedOutputSize(prefix)   // per-layer multiplier; updated on dim-changing commits
```

The **`canvasFitScale`** is that same preview-sized image fitted into the letterbox
(its on-screen side divided by its own pixel side); multiplying by `canvasZoom`
gives the **`displayScale`** — the magnification that decides the full-size
threshold (see *Zooming past 100%*).

The per-layer **output size is estimated, not rendered**. The editor offers a
**centralized dimension helper** — `estimateStackOutputSize(startW, startH,
effects)` — that returns the **expected full image dimension without rendering
it**. Some effects need it (crop and geometry sizing, the preview multiplier,
the adaptive ML budget). Like `render`, the dimension method is **folded across
all effects**: each `StackEffect` implements a size-estimating method (distinct
from `render`) that receives the **previous effect's output dimensions** and
decides whether to change them, returning the dimension the image is supposed
to have **after that effect is applied with its stacked layer parameters** —
most effects (adjustments, filters, enhancers, overlays, frames, brushes,
beauty) return the input unchanged; a `TransformEffect` swaps the dimensions for
an exact 90° turn and returns the **grown transparent bounding canvas** for any
other angle; a `CropEffect` returns its selected region scaled against the
composite it is applied to. The expected footprint of a layer is the **prefix
fold of these estimators** over the stack from `firstFull`, using the
**previous image's dimensions** — so a crop after a rotation is normalized
against the already-rotated footprint, exactly as in the fold renderer (*The
fold renderer*). Estimation never runs a fold — it only derives integer sizes —
so it stays cheap, and the multiplier is recomputed whenever a
dimension-affecting effect (crop / transform) is committed or re-committed.

### Zooming past 100% (`displayScale` > 1 — background full-size fold)

Full-size work runs **only when the display actually outgrows the preview-sized
image — `displayScale` = `canvasFitScale` × `canvasZoom` > 1** (colloquially
"zooming past 100%", i.e. past the preview bitmap's native resolution; the
precise trigger is `displayScale`) **or needs a full-res result (save)**. At
that moment the full-size `lastImage` is computed in the **background**:

```
fullSize = fold( currentImage, [currentEffect] + effectsAfterCurrent )
```

The full-size fold is **debounced** before it is launched, so rapid zooming does
not spawn a new fold per frame: it starts **≈500 ms after the latest change that
scheduled it** (a pinch/scroll zoom or a control-value change); the debounce is a
single flat window meant to fire only once the user has settled on a scale.

**What the canvas shows (the display decision tree).** The canvas shows the
**preview image** (eye ON — the full composite, as the last preview-sized image
of the layer stack) or the **limited preview** (eye OFF —
current effect only, no tail — see *Previewing a tool*). In both cases the
resolution follows the same rules — the default display is the **preview-sized
image** (the last preview-sized image of the layer stack); the **full-size
`lastImage`** is only ever shown when it exists, is non-stale, and the
`displayScale` "wants" it:

- **`displayScale` ≤ 1, full-size image absent or stale**: show the **preview-sized
  image** — this is the common path; the small image is sharp enough at this
  scale.
- **`displayScale` ≤ 1, full-size image present and non-stale**: show the **full-size
  `lastImage`** — no reason to fall back to the small preview while the full-size
  result is still valid (e.g. `displayScale` once exceeded 1, the full-size image
  was generated, and the zoom dropped back below the threshold).
- **`displayScale` > 1**: show the **preview-sized image scaled up** with
  proportional scale and panned to the current viewport — it is scaled only to
  *simulate being at the full-size resolution*, serving as a stand-in; the canvas
  never blocks and there is no dead frame. Once the background fold completes, the
  full-size `lastImage` is **swapped into the preview in place**, silent and
  following the current scale and pan.

**Control changes during a background fold.** When a **control value changes**
while the background full-size processing is in flight, the in-flight fold is
**cancelled and restarted** with the new values; while it is not ready again, the
preview keeps showing the small preview-sized image (the full-size is stale).
When a control changes but **no** full-size work is in flight, the tail reapply
immediately produces the new preview image (the last preview-sized image of the
layer stack, shown promptly) and the full-size `lastImage` is regenerated in the
**background** (again debounced) and swapped in when ready. For a **beauty
tool**, the full-size result also becomes stale when the background geometry
became ready in the meantime — the preview must be regenerated against the fresh
geometry.

### All `lastImage` logic is lazy

`lastImage` is the **full-size** final composite and is **lazily generated on
demand** — the demand may be a **save / merge / share** operation (save always
uses or triggers it) or a display whose `displayScale` outgrows the preview-sized
image (`displayScale` > 1). It is **never** produced during the common
interactive path.

The image the **canvas shows by default** — given **no selected tool took over
the canvas**, and the **`displayScale` ≤ 1** — is the **last preview-sized image of
the layer stack**: the per-layer preview of the last committed layer (the
preview-sized result of the tail reapply). This preview-sized image is a
**preview image**, distinct from the full-size `lastImage`; it also serves as
the stand-in that is scaled up while the full-size version is generated when
`displayScale` exceeds 1. The full-size `lastImage` is shown only when it exists
and is non-stale and the current display wants it (`displayScale` > 1, or a
previously generated result still valid at `displayScale` ≤ 1).

**`currentImage` and `prevImage` are full-size and used on demand only.** They are
never produced during interactive editing (no preview-sized versions exist for
them); a full-size composite cache is populated only when a full-res result is
actually needed, and the interactive/common path folds from the **per-layer
committed preview bitmaps** instead.

When a **control value changes**, the tail reapply immediately produces the new
preview image (the last preview-sized image of the layer stack, and the
**per-layer committed preview bitmaps**
after the current effect's position are re-folded as part of the tail — see
*Per-layer preview cache*). The **full-size** caches after the current effect are
also marked stale; if a full-size image is in flight it is **cancelled and
restarted** (see *Zooming past 100%*). A change that merely **returns to the
stored value** (dragging a slider to a new value and back, without committing)
does **not** change the committed layer and therefore does **not** stale any tail
— the compare is against the value stored in the layer at its last commit (or the
default, for a never-committed tool), per *Per-layer preview cache*.

- **Save / merge / share** require the true full-size result and **await its
  completion** — this is the one blocking case — but they do **not** always fold
  from the base: they **follow the same resumable-base logic as any
  `displayScale` > 1 fold**. If `lastImage` is already fresh (non-stale), save can use it directly
  with no fold at all. If not, it folds **only the minimum**: if `prevImage`
  exists and is non-stale, resume from `prevImage` and apply the tail; if
  `currentImage` exists and is non-stale, resume from `currentImage` and apply
  the remaining tail; only if **neither** is available and non-stale does it fold
  the full stack from `firstFull`. This is the same resumable logic driven by
  the staleness flags and resume cursors — see *Resumable fold*.
- **Every other operation** (panning, layer inspection, reorder preview, a
  thumbnail) serves from the preview cache and — where a full-size image is
  momentarily needed but absent — **scales the preview** until the full image is
  generated, then uses it.

> **Non-blocking golden rule.** The only operations that ever await a full-size
> fold are **save / merge / share**. A `displayScale` > 1 (zooming past 100%) and
> control changes serve the upscaled preview immediately and regenerate the
> full-size image in the background — the UI never blocks on a full fold.

### Resumable fold (staleness up to a tool)

The three cached images (`currentImage` / `prevImage` / `lastImage`) are all
**lazily computed** and each carries:

- a **staleness flag** telling whether the cached value is still valid, and
- **up to which tool it was applied** (a resume cursor).

`originalImage` carries neither — it is always the pristine first base (see
*Core model*).

When a cache is stale, the fold **resumes** from the recorded tool rather than
recomputing from the pristine base every time — the unchanged prefix is reused
and only the tail after the resume point is re-folded. This is the mechanism
behind the base-selection rule (see *Choosing the base image*): picking the last
non-stale cache before a proposed position and folding only the missing tail
*is* the resume cursor. It generalises the prefix-contagious invalidation to the
lazy model while keeping the same correctness guarantees: continuing the fold
from a valid prefix yields byte-identical output to a from-base re-fold.

### Centralized full-size controller and fold-to-index

**One centralized controller owns the staleness and laziness of all the full-size
images** (`firstFull`, `currentImage`, `prevImage`, `lastImage`). Every caller
that needs a full-size image — the **save process**, the **merge process**, and
the **`displayScale` > 1** path — goes through this single fold entry point and follows
the **same** process, so the caches are populated, refreshed and invalidated in
exactly one consistent way. (Only `originalImage`, `firstPreview` and the
per-layer committed preview bitmaps are outside it — they are always present and
never stale.)

**Who requests the full-size image.** The requesters are known for sure:
**save** and **merge** always fold (or use a fresh cache); and the editor zoom,
through the precise `displayScale` trigger. `displayScale` > 1 is itself the
definition, not the raw zoom — because `firstPreview` can sometimes be **bigger
than the viewport**, the threshold is the point where each on-screen pixel lands
on more than one preview pixel: `canvasFitScale` × `canvasZoom` > 1 — when each
preview pixel covers more than one device pixel, a full-size image is required.

**The dimension helper is part of the controller.** The controller exposes a
centralized method to retrieve the **expected full image dimension without
actually rendering it** (`estimateStackOutputSize`); some effects need it. Like
`render`, the dimension method is **folded across all effects** — each effect
receives the previous effect's dimension and decides whether to change it (see
*Choosing preview resolution*).

**Folding to a target layer.** Because the caller may intend to fold **all**
layers or just **up to some specific layer**, the controller folds to a **target
layer index**, where **index 0 = no layer**. Indices are 1-onwards when the
intention is to apply layer **N-1** (i.e. `index = N` means "apply layer N-1";
the fold has produced the composite after layer `index-1`). Given a target index:

1. **Index 0** request → **promptly return `firstFull`** without any further
   work (there is no layer before the first; the base is the answer).
2. Otherwise, look for the **latest cached full-size image** (`currentImage`,
   `prevImage`, `lastImage`) from the requested position that is **not stale**. If
   the requested position already equals the position of any of them and they are
   not stale, **return it immediately** — no fold.
3. If not cached, **fold the minimum number of effects** needed to reach the
   desired position, resuming from the nearest non-stale cache.

**Deciding whether to cache the result:**

- If the requested index **is the last layer** and the `lastImage` cache **was
  stale**, store the result in `lastImage` to make it **fresh again** (clear the
  stale flag).
- If folding **processed (rendered) the `N-1` image** during the process, and the
  `prevImage` cache **was stale**, also refresh the `prevImage` cache.
- For **`currentImage`** the rule is a bit trickier:
  - If the requested index was **not 0**, **not last** and **not `N-1`**, store
    the result in `currentImage` and **shift `currentPosition` to the requested
    index**, so a next request at that position returns it promptly without
    regenerating.
  - **Special case:** if the requested index **is the last or `N-1`** AND the
    `currentImage` cache **is stale** AND we **called the render of image `N/2`**
    during the fold, then set the result of `N/2` in `currentImage`, update its
    `currentPosition` to `N/2` and set it fresh — so `currentImage` is
    opportunistically anchored mid-stack for a later edit.

**What makes the full-size caches stale — editing values.** Editing **any
already-stacked layer's parameters to a value different from before** marks the
caches **stale — but only those AFTER the edit**. If the `currentImage`'s
`currentPosition` was **before** the edit, it does **not** need to be marked
stale (its prefix is unchanged). This is the prefix-contagious invalidation
applied at full size.

**Staleness transitions on structural changes (commit / removal / move).** A
layer-list mutation shifts which prefix each cached image represents. The
controller propagates staleness precisely instead of wiping every cache:

- **Commit at the tail** (a new effect is appended, `N` → `N+1`): the next
  `prevImage` (the input of the new last effect) is the previous full stack, so
  **`prevImage` is set to the value of `lastImage`**, and **`lastImage` is marked
  stale** (it must be recomputed with the new effect applied). If `lastImage`
  was **already** stale, then the newly-assigned `prevImage` has inherited an
  invalid value and is **marked stale too**.
- **Removal of the last layer** (`N-1`, `N` → `N-1`): the mirror of a tail
  commit — **`lastImage` is set to the value of `prevImage`** (removing the last
  layer leaves the previous composite as the new final image), and **`prevImage`
  is marked stale** (the new `prevImage` must be fold-to-index `N-2`). If
  `prevImage` was **already** stale, then the newly-assigned `lastImage` has
  inherited an invalid value and is **marked stale too**.
- **Removal of the previous layer (`N-2`) or any layer before it** (index
  `≤ N-2`): **both `prevImage` and `lastImage` are marked stale** — removing the
  previous layer changes the last layer's base to the fold just before the older
  `prevImage`; removing a layer even further before it changes both bases.
- **Removal of the current layer (index `currentPosition`) or any layer before
  it** (index `≤ currentPosition`): **`currentImage` is marked stale** (its
  prefix is gone / repositioned). Combined with the two rules above: a removal
  that sits both at/before the position of `prevImage`/`lastImage` *and* at/before
  `currentPosition` marks all three stale.
- **Move (reorder)**: the same removal rule is applied, treating **the lowest
  index between the origin and the target position** (`min(fromIndex, toIndex)`)
  as the layer that was removed — layers strictly before it keep their composition
  and cached composites; from that bound upward the order changes, so the stale
  flags are set exactly as for a removal at that index.

**Re-commit of a re-edited layer at a non-tail position.** Re-entering an
**already-stacked** layer and leaving it (its commit) triggers staleness by the
**same position-propagation rule as if that layer's position were the removed
one — but without actually removing it the layer stays, the stack length is
unchanged, and no value transfers happen** (`lastImage` ← `prevImage` /
`prevImage` ← `lastImage`, which only ever accompany a real removal/tail-commit).
The trigger fires **only when the committed result actually differs from the
value already stored on the layer**: the parameters were changed while re-editing
and, on leaving the effect, the final stored value is different from before. A
no-change re-entry commits nothing and is **not** a staleness event. Concretely,
for a re-commit at position `p` in an `N`-layer stack:

- `p == N-1` (the last layer): **only `lastImage` becomes stale** — `prevImage`
  is the composite *before* the last effect and is unaffected.
- `p ≤ N-2` (the previous layer or any earlier one): **`prevImage` and
  `lastImage` become stale**.
- `p ≤ currentPosition`: **`currentImage` becomes stale too** (its prefix
  contains the re-edited layer), combining with the rules above when it applies.

This is the commit-time counterpart of the live "editing values" rule above:
while the slider is being dragged, edits mark only the caches AFTER the edited
layer stale; the re-commit is what applies the position rule over the whole
cache set once the tool is left.

These are **flag** transitions, not eager renders: setting a cache stale never
recomputes it; it only records that the next full-size request must re-fold that
cache (resuming from the best still-fresh prefix) instead of reusing the value.
The **preview-sized images follow the same position-propagation rule**, with one
difference: the preview path (per-layer committed preview bitmaps and the
preview-resolution fold cache) is cheap and always regenerated, so **no stale
flag is ever set at preview scale — the affected prefixes are simply re-folded
on the fly** by the next preview request, covering exactly the same positions the
flags would mark. Only the full-size caches carry real stale flags.

**Eye toggle and the current tool.** When an effect is being applied and the
preview should show **only the current effect** (per the "eye" toggle), the fold
must go **up to the previous layer, never fold over itself** — folding over the
tool would move the `currentImage`'s `currentPosition`, wasting the time to
regenerate the base over and over again (the tool's values are expected to change
often while being manipulated). The effect may instead use a **lightweight
mechanism to render itself**, or **call its own `render` directly**, since its
output image will always change anyway. But if the expected preview result is the
**full result of the tail**, the current effect is **rasterised into that fold** —
it can never stay a floating overlay when a tail must cascade over it (the tail
feeds from its real bitmap output); the lightweight stand-in is reserved
exclusively for the eye-OFF single-effect preview. When the caller folds up to
**last**, the fold will not touch `currentImage` when requesting `lastImage` and
`currentImage` is not stale. Remember this is all **on demand**: the full-size
work happens only when **`displayScale` > 1** or some other condition actually
requires full-size.

**The eye-OFF rule also applies when a brush tool's live-apply has fired.** A
brush tool that baked its strokes into the fold (object removal / blur / mosaic
live-apply) keeps its layer in the fold, but the eye-OFF rule still holds: the
fold **stops at the current tool and drops the tail** — showing the tail with the
eye off would leak the hidden layers. Only the eye-ON composite folds the tail.

### Original geometry image and original geometry

Geometry is grounded in a dedicated base that survives merges — distinct from the
three lazy colour caches:

- **`originalGeometryImage`** — the bitmap the facial geometry is traced from.
  Its **resolution starts identical to `originalImage`** (full-size), and it
  initially **is the same object as `originalImage`** (a **reference**, sharing
  the memory). `originalImage` itself is **immutable and never changes**. After a
  merge, if any **geometry-changing effect** (a deformation — crop / transform /
  reshape / rotate-flip / a background Replace placement — or an occluding
  overlay (sticker / text / frame) / a hard-stroke brush (pen / mosaic / object
  removal)) was
  folded, the geometry base can no longer equal the pristine photo, so the merge
  **regenerates `originalGeometryImage`** as an **independent bitmap instance**
  — independent from `originalImage`, which stays frozen. The regenerated image
  is produced by **folding all the deformation effects over `originalImage`**,
  which **cascades the resolution-affecting effects too**: its resolution may
  therefore differ from the original (a crop shrinks it; a rotation that enlarges
  grows it), but it is **always grounded in the initial full-size image**. If
  **no** geometry-changing effect was merged, `originalGeometryImage` is **kept
  as it was** (still a reference / still matching `originalImage`) — there is
  nothing to regenerate. **ML is never run against this full-size bitmap**: the
  geometry resolvers always derive their working input from it at the **adaptive
  1–4 MP budget** (see *Adaptive ML input resolution*), and the geometry-track
  fold never produces a base larger than that budget — the full-size dimensions
  only ever feed the **dimension estimators** that derive the budget.
- **`originalGeometry`** — the ML-computed facial geometry (the `BeautyGeometry`
  traced from `originalGeometryImage`). It is **lazily computed**: ML runs only
  when a beauty effect actually needs it, and only against
  `originalGeometryImage`.

A beauty effect resolves its geometry as follows.

- If there is **no geometry-changing effect between the base
  (`originalGeometryImage`) and the beauty effect's position**, the effect uses
  **`originalGeometry` directly** — this reuses the geometry-context staleness
  mechanism already in place (one shared last-geometry slot; a beauty effect on a
  clean segment reuses it, ML runs once per segment).
- If there **is a geometry-changing effect in between**, the otherwise-cached
  geometry is **stale** (it was computed lazily against the pre-deformation base)
  and the effect must **fold all the geometry-changing effects** in that gap over
  `originalGeometryImage` — i.e. replay the deformations (and any occluding
  overlays / hard strokes) **up to the effect before it** — to reach the running
  image at the layer's fold position. That result is **used as the parameter** of
  the current beauty effect, and stored on the layer.

The **background tool resolves its subject mask the same way** (same shared
context, same editor callback, same stale/regenerate rules): the mask is
segmented against the composite that the stack produces **up to the background
layer's position** — a composite that already contains any crop / rotation /
transform applied before it. The mask is therefore **never** taken over the raw
`originalImage` or a resident bitmap; when the background layer is folded after a
previous deformation, it re-resolves against that deformed base (marked stale),
so the mask always lines up with the subject as drawn on the canvas.

**How the beauty effect obtains that base.** Any geometry-requiring tool (beauty
or background) is handed an **interface/callback** the editor implements: a
method the tool can call to ask the editor to **fold the stack up to the tool's
(proposed) layer position applying only the deforming tools**, and return the
resulting bitmap — or, when **no deforming tool exists in that range**, to return
**`originalGeometryImage`** directly (no fold). The tool calls this method to
obtain the image to resolve its geometry against (handling the stale / absent
cases above), so the editor stays the single owner of the deformation fold while
the tool only consumes the returned base. The returned base is then
folded/resolved at preview size first and full size in the background, per
*Lazy beauty geometry*. **This is the same callback the background tool uses** to
obtain the base it segments its subject mask against: the base already contains
any prior rotation / crop / deformation — the mask is **never** taken over
`originalImage` or over a raw resident bitmap, so no inverse-mapping of the mask
onto a pre-deformation pixel space is ever needed.

> **Geometry is a separate process and does not reuse the three cached images**
> (`currentImage` / `prevImage` / `lastImage`). The geometry pipeline works from
> `originalGeometryImage` (+ the lazily-computed `originalGeometry`) and, when a
> gap contains deforming/occluding effects, folds only those across to reach the
> layer's position — it neither seeds itself from nor populates the full-size
> `currentImage`/`prevImage`/`lastImage` caches. The lazy-cache base-selection (see *Choosing
> the base image*) governs only the **colour/fold** images, while geometry
> resolution stays on its own track.

### Lazy beauty geometry (on stale geometry)

When a beauty tool's geometry is **stale** (a geometry-changing effect sits
between `originalGeometryImage` and the layer) or **absent** (needed for the
first time), the real geometry must be resolved. A beauty tool's
geometry is **not recomputed on every control change** — it is recomputed only
when it goes stale/absent, so a full-quality resolution happens rarely (once per
tool entry into a gap, or after a geometry-changing effect is
edited/added/removed). Still, a full-quality ML resolution (FaceLandmarker +
segmentation) is expensive, so the editor never blocks the UI on it at tool
selection. Within a resolution, only the **fields the tool needs** are run (see
*Per-field resolution* in *Shared geometry context*), and both the preview and
the full-quality stage reuse the context's retained base bitmap:

1. **First resolves geometry over the already-ready preview image** (fold the gap
   at preview resolution) and uses that for the time being — the user sees a
   correct, if lower-resolution, deformation immediately and can start
   interacting with it instead of waiting on a stall.
2. **In the background**, folds the gap **at the geometry-track resolution — the
   adaptive 1–4 MP budget, never the full-size original** — applying only the
   geometry-changing effects (no full-res colour work for the geometry pass),
   then **resolves the geometry against that**.
3. Swaps the exact full-quality geometry in once ready.
4. **On commit, blocks until the final (full-quality, geometry-track) geometry is
   ready** before
   actually committing — the committed layer must carry the correct geometry. The
   user can keep interacting with the temporary preview-geometry in the meantime;
   the block only gates the commit itself. This full-quality evaluation has
**exactly two initiations**: the **`displayScale` > 1 background full-size
    fold** (colloquially "zooming past 100%" — a presentation trigger: it
    binds/resolves the geometry while rendering in the background and never
    blocks viewing the preview-sized result), and the **commit itself**. When the
    zoom trigger already ran (or is mid-run), the
   commit **awaits and reuses that same segment-identity result** — the shared
   geometry context serialises resolution (one mutex) and serves a **single
   result per segment × resolution** from its retained-base identity guard, so
   the commit never duplicates an in-flight or completed evaluation; only when
   the zoom trigger never started does the commit trigger the full-quality
   evaluation itself, block until it returns, and carry that geometry as the
   layer's parameter. The two triggers therefore always **merge** into the same
   one evaluation.

> **Why keep the two-stage (preview-then-full-quality) flow rather than always
> blocking on the full-quality geometry.** Because geometry is *not* recomputed on
> control changes, the number of full-quality resolutions is already small; the
> question is only whether to *wait* on the one that does happen. Full-quality ML
> resolution (at the adaptive 1–4 MP cap) is slow enough that a hard block at
> **tool selection** — the moment
> the user is deciding to engage — would be a noticeable stall. The provisional
> preview-sized geometry costs one cheap, fast pass (small-image ML + a
> preview-resolution gap fold) and buys instant, non-blocking entry; the
> expensive full-quality pass then runs once in the background and is the only thing
> ever awaited, and only at commit. This mirrors the zoom-preview principle
> (never block except on save/commit) and the preview pass is inexpensive
> compared with the raw full-quality result it avoids waiting on.

### Adaptive ML input resolution

The geometry resolver never feeds the raw photo to the ML pipeline. The
segmenters (hair + skin `ImageSegmenter`) emit their confidence masks **at the
input image size** and the mask processing allocates a second full-res ARGB
copy per channel, so a full-size run on a 48–108 MP photo exceeds a GB of
transient allocations — a silent native OOM on real devices (see
`docs/effects_reference.md` → *Beauty — face / ML sub-tools*). The input's
**pixel count** (`width × height` — the honest unit of the pipeline's
compute and memory, which both scale with area, not with any single linear
dimension) is therefore bounded **per device**:

- **Budget.** A fixed fraction of the device's heap (`Runtime.maxMemory()`,
  which reflects the memory class / `largeHeap` flag) is reserved for the
  pipeline's transient working set — which touches a known number of full-res
  ARGB pixel planes at the input size — and the largest input whose **pixel
  count** fits is derived from it. Large-heap devices therefore run the
  pipeline over a larger image (better landmark/mask fidelity), low-memory
  devices over a smaller one (stays alive). The context's **retained geometry
  base bitmap** is a product of this same cap — it is the 1–4 MP working input
  (or its undownscaled source when the source is already at or below the cap),
  never a full-size original — so holding one per live segment stays inside the
  same memory envelope the pipeline already budgets for.
- **Hard clamps.** The adaptively-derived pixel count is clamped between a
  **minimum** (≈ 1 MP, the pixel count of a 1024×1024 square — the quality
  floor: images at or below it are never downscaled at all) and a **maximum**
  (≈ 4 MP, the pixel count of a 2048×2048 square — the CPU ceiling: no source
  can drive the pipeline's time unbounded). Both correspond to the square of
  the previous long-edge clamps, so the worst-case memory envelope is
  unchanged while every non-square aspect ratio now spends its budget fairly.
- **Uniform downscale in both axes.** The working input is never stretched:
  when the source pixel count exceeds the cap, both dimensions are scaled by
  the **square root** of `capPixels / (width × height)` so the aspect ratio is
  preserved and the rescaled pixel count lands exactly on (or just under) the
  budget, regardless of how wide or tall the photo is.
- **Reference footprint = the prefix fold.** The cap is evaluated against the
  base's full-size **prefix-fold pixel count**, not the running base's own
  resolution: the geometry track derives it from the original geometry image's
  dimensions folded through the effects' **dimension estimators**
  (`StackEffect.estimateOutputSize`, `estimateStackOutputSize` — the same
  pure, allocation-free approach the crop tool uses to know the final
  dimensions). This **recomputes the desired size after the dimension-changing
  effects** (crop, transform with rotation, background replace) whenever the
  footprint changes, and again when a merge re-grounds the original geometry
  image — the editor hands the predicted pixel count to the resolver alongside
  the base instance.
- **Pixel-exact result.** The input is downscaled once to the capped size; all
  returned mask paths, face bounds and landmarks are scaled back into the
  base's pixel space, so a capped resolve is identical to a full-size run.

### Consistency

Because the preview image and the full-size image are both produced by the same
fold from the same `effectStack`, they are the same image at different
resolutions. Save/merge/share always use the **full-size** fold and never the
preview, so the persisted output stays byte-identical to a from-base re-fold —
exactly as before; the preview cache is purely a display/UX accelerator.

## Behaviour rules

### Choosing the base image for the current tool (`currentImage`)

When an edit window is opened nothing has been applied yet, so **`lastImage`
starts equal to `originalImage`** (and there is no separate `prevImage` yet).
Selecting a tool picks the image the effect is applied to — the
**`currentImage`**, the composite of every effect *before* that tool.

To select the **best-fitting base**, the editor picks the **last non-stale**
image from the set **(`firstFull`, `currentImage`, `prevImage`)** that sits
**before the current effect's proposed position** in the stack. *Proposed*
because the effect may not yet be committed — it commits **only if it changed AND
the change differs from the default values**. (`firstFull` is the full-prefix
base — a reference to `originalImage` before any merge, the merged result after
one — so "starting from the first base" folds from `firstFull`, never from the
stale lazy caches.) Concretely:

- If the effect is / will be the **last** (`N`), it tries **`prevImage`**
  (`N-1`) — it is not applied over `lastImage`, which would re-apply the effect on
  top of itself.
- If the effect **is the last** (`N`) but **`prevImage` is absent or stale**
  — e.g. a layer parameter changed in between, after `currentImage` but before
  `prevImage`, leaving `currentImage` still valid — and a **non-stale
  `currentImage` sits before that gap**, the editor **reuses `currentImage`** as
  the base and **re-folds only the tail from `currentImage` up to `N-1`** to
  rebuild `prevImage`. In this path `currentImage` itself is **not recomputed or
  refreshed** — it is consumed as-is and **left in place**, so it remains
  available to be reused again when it is the best-fitting base for a later
  mid-stack effect.
- If the effect **is not the last** but a **non-stale `currentImage` already sits
  before it**, that `currentImage` is **reused** and **only the missing effect(s)
  are folded** on top to reach the effect's base — i.e. from the reused
  `currentImage` up to `prevImage` (`N-1`), no more.
- If all the candidates are **stale**, the fold starts from **`firstFull`**.

**The fold updates staleness opportunistically.** When a cache is recomputed (or
reused) the run takes the chance to refresh the relevant cached images so future
mid-stack effects do not reprocess everything from the beginning:

- If `currentImage` was **stale** but the current effect is the **last**, the
  editor uses `prevImage` as the base (as above), yet **leverages this
  opportunity to update `currentImage`** — saving the result of a middle layer
  (e.g. at `⌊(N-1)/2⌋`) while it passes through, so a later mid-effect on that
  position does not have to re-fold from the start.
- Conversely, when `currentImage` is **already non-stale** and is used only to
  rebuild a stale/absent `prevImage` (the last-effect case above), `currentImage`
  is **not** opportunistically refreshed — it is left exactly as it is, so it
  stays a reusable mid-stack base.
- If the effect being edited is the **first layer** (position `0`), the base is
  **`firstFull`** and neither `currentImage` nor `prevImage` is touched —
  there is nothing before it to cache (this is the index-0 fast path of the
  centralized controller, see *Centralized full-size controller and
  fold-to-index*).

Because only `firstFull` (and `originalImage`) is guaranteed present at full
size, and `currentImage`/`prevImage` are **full-size on-demand** caches, the
above selection runs only when a `displayScale` > 1 (zoom past 100%) or save
actually needs a
full-size base; in the common interactive path the fold uses the **per-layer
committed
preview bitmaps** instead (see *Per-layer preview cache*).

### Previewing a tool (tail reapply)

To show a preview of the current tool, **`currentImage` alone is not enough** —
the current effect and everything after it in the stack must also be reapplied
on top. The **full composite** (used for save, and shown on the canvas when the
eye is ON) is:

```
fullPreview = fold( currentImage, [currentEffect] + effectsAfterCurrent )
```

The prefix (up to `currentImage`) is cached and reused across preview frames;
only **the tail** — the current effect plus all later effects — is re-folded on
each change. In the **full-res path**, `currentImage`/`prevImage` (full-size,
lazy) anchor the resume. In the **common interactive path**, the fold instead
takes the base from the **previous layer's committed preview bitmap** and applies
the current effect and tail, producing the new **preview image** (the last
preview-sized image of the layer stack) — always, on every change, regardless of
zoom level:

- Editing a **non-last** tool: `preview = fold(prevLayerPreview, [currentEffect] +
  effectsAfterCurrent)` (preview path) — or `fold(currentImage, ...)` when a
  full-size result is needed.
- Editing the **last** tool: `currentImage = prevImage` and the tail is just the
  single effect, so `preview = currentEffect.render(prevImage)`.
- **Adding a new tool**: `currentImage = lastImage` and the tail is the single new
  effect, so the new `preview = newEffect.render(lastImage)`; the previous
  `lastImage` becomes the new `prevImage` (and the edited tool's input).

During interactive editing **each control change re-folds only the tail from the
edited layer's (new) committed preview** — every later layer's committed preview
is regenerated in order, and the full-size caches after the change are marked
stale (see *Staleness is prefix-contagious* in *Per-layer preview cache*); the
new **preview image** is produced — always, on every change, regardless of zoom
level.

**What the canvas shows depends on the eye button** (see
`docs/effects_reference.md`, *Two preview/compare categories*):

- **Eye ON**: the canvas shows the **preview image** (and, when present and
  non-stale and the zoom warrants it, the full-size `lastImage`) — the full
  composite (prefix + current effect + tail). Resolution follows the zoom/​staleness
  rules below.
- **Eye OFF** (Category A only — Category B eye is off by default but controls
  pan/zoom too): the canvas shows a **limited preview** — the current effect
  applied to the layer before it with **no tail folded**. This lets the user see
  the isolated contribution of the current effect and everything before it. The
  limited preview is computed cheaply as `currentEffect.render(prevLayerPreview)`
  (in the common path the base is the **previous layer's committed preview
  bitmap**; in the full-res path `currentImage`). `lastImage` is still computed as
  the full composite (it is needed for save and for the next tool's base); only
  its *display* is suppressed.

The **canvas always shows either the preview image or the limited preview**,
choosing the resolution per the `displayScale` and staleness rules:

- At `displayScale` **≤ 1**: the preview-sized version is shown. If a full-size
  `lastImage` exists and is **non-stale**, it is shown instead (no reason to
  fall back while the full-size result is still valid).
- At `displayScale` **> 1**: the preview-sized version is shown immediately while
  the full-size version is generated in the background (debounced); the full-size
  image is **swapped in silently** when ready — see *Zooming past 100%*.

So the preview image is always the **full composite** — derived by applying the
tail on top of the base (the previous layer's committed preview in the common path,
`currentImage` in the full-res path), never by mutating an older `lastImage` in
place — which is exactly why the previous `lastImage` (the one before this edit)
must be kept as `prevImage` until the new result supersedes it.

### Commit on lost focus

A tool's live edits remain **uncommitted** while it is focused. The effect is
**committed** when the tool **loses focus** — the user selects a different tool,
opens the Layers window, or taps **Save** — **and only if** its parameters
**changed and are not the default** values.

On commit:

- The tool's current parameters are **stored on its `StackEffect` entry** (this is
  the "save params on lost focus" rule — every tool behaves like this).
- The edited result is **baked** into the working composite: the committed
  `lastImage` becomes the **input `currentImage` for the next effect**. So the
  next tool folds on top of the committed, baked bitmap — **not** by re-applying
  any raw parameter override from the previous tool.
- **The commit always produces a bitmap.** The committed result is **rasterised**
  into the working pixel buffer. This is non-negotiable for **every** effect,
  including geometry/transform tools: a rotate/flip/crop/frame **emits a new
  bitmap with the change drawn inside the pixels** (e.g. a rotate/flip emits a
  new oriented canvas bitmap; there is **no** "keep the canvas rotated and just
  remember the transform" display-only route). A transform's committed output is
  a **bitmap**, and later effects consume that bitmap. **No effect may persist as
  a display-only viewport transform** (see the boxed rule at the top of this
  document); the preview `graphicsLayer`/viewport transform is a **transient
  editing affordance only** and is never the source of a committed change.
- If nothing changed (params still at default, or unchanged since the last
  commit), **no commit happens** — the stack entry is left as it was.

Because a committed effect's result feeds every later effect, the working
composite only ever advances through **committed milestones**; the pristine
`originalImage` is never mutated.

### Re-committing at a fixed position (returning to a tool)

Re-entering a committed tool does **not** move it to the end of the stack. It
reopens **at the same position** it currently holds:

1. The editor **recomputes `currentImage`** = the fold of all committed effects
   `0..index-1` before it (from `firstFull`).
2. The tool's **saved parameters are restored** into the controls.
3. While editing, only that effect plus the tail is re-folded for the live
   preview (see *Previewing a tool*).
4. On the next lost focus, the effect **re-commits at the same position**, and a
   **cascade fold is triggered**: every committed effect after it (`index+1..N`)
   is re-folded over the new result so `lastImage` (and each affected committed
   milestone) regenerates for the next effect. The effects before it are
   untouched.

This is how the "bake on lost focus, still reversible" model holds together:
each effect produces a committed input bitmap for the next effect, yet the user
can always go back, restore their saved params, re-edit, and re-commit in place.

**Recompute-geometry corner case (mid-stack edit over a geometry-changing
effect).** If the
edit happens **in the middle of the stack** and **the current effect, or any
effect after it, changes geometry** — a **deformation** (crop, rotate/flip, or a
geometry-affecting face reshape dimension — slim / eye enlarge / nose / jaw), an
**occluding overlay** (sticker / text / frame), or a **hard-stroke brush** (pen,
mosaic, or object removal) — the fold must account
for the invalidated shared geometry context. Reapplying
that tail is
equivalent to **removing and re-adding the affected layers in the same
position, but with fresh geometry**: the effect calls `markStale()`, so the
next geometry-needing beauty layer (or the background layer, when the
geometry-changing effect was applied before it) **regenerates** against the
changed image rather than reusing a stale geometry — exactly as a fresh add
would. So a mid-stack edit over (or followed by) a geometry-changing effect never
reuses geometry that no longer matches the pixel space; it recomputes the
affected layers with new geometry. The same rule covers the **re-entered
geometry-deforming effect** itself: changing its values marks the context stale,
so every later geometry-based effect regenerates on the next fold. (Mechanism is
the `markStale()` / regenerate flow in *Shared geometry context (beauty +
background)* below.)

### Editing an existing effect (already in the stack)

Re-entering a tool restores the slider(s) to the stored parameters of its stack
entry, and reopens it **at its current stack position** (see *Commit on lost
focus*). Moving a slider picks `currentImage` as above, applies **only the
current effect plus the tail**, and refreshes the preview image (the full-size
`lastImage` is re-generated only on demand — `displayScale` > 1 / save / merge /
share);
committed effects
before the tool are untouched. On lost focus the effect **re-commits in place**
and a **cascade fold** regenerates every committed effect after it.

### Tail invalidation on re-edit (all effects)

A re-visited effect that ends up with control values **different from the values
stored when it was committed** invalidates the **tail** — every effect after it —
so it is re-folded. This is **not a crop-specific rule**: **every** effect that
is re-committed with changed, non-default parameters triggers a cascade fold of
the effects after it (`index+1..N`). Only a re-visit with **no control touched**
(or changed back to the stored values) takes no action.

**Brush / diff-bitmap positioning under a later crop/transform.** A brush / pixel
effect commits as a **diff bitmap parameter** — a same-resolution, transparent
bitmap that, applied over the previous bitmap, produces the desired effect
(`StrokeLayer`/`BitmapDiffEffect`). When a later crop or transform changes the
footprint, the diff's drawn pixels are realigned to the new footprint — the diff
bitmap is **middle-aligned / rescaled to match the new resolution**. This is
approximate: in a shrink-then-grow scenario (draw after a shrink, then grow the
crop back), parts of a drawing can be left missing.

### Adding a brand-new effect (not yet in the stack)

Appended to the stack (in the tool's canonical position) and folded in, with
`currentImage` = the current `lastImage` (no prefix before a new tool). While
tweaking, only that new effect's parameters change on each update. On lost focus
the new effect **commits** and its baked result becomes the new `lastImage` /
input for the next effect.

### Re-ordering

The stack is fully **user-reorderable** (`moveEffect(from, to)` + the drag
gesture in `LayersPanel`), and reordering is **undoable**. Because each layer's
output feeds every later layer, changing an earlier effect forces every later
effect to recompute — there is no shortcut, their base differs. Reordering
therefore **invalidates every preview image after the moved layer** (and the
thumbnails derived from them), and the previews/thumbnails are **recomputed in
the background**, not synchronously: while a row's preview is not ready its
thumbnail shows a **loading state**.

**Safe-guard when returning to the edit tools.** Whenever a preview is not yet
available (after a reorder, a removal, or any invalidation), the editor keeps
showing the **latest ready preview** it has and updates in place as each new
preview becomes ready. The **minimum guaranteed preview is always
`originalImage`** — it is always present at full size — so no state ever shows a
blank canvas while previews regenerate.

### Merge Layers / Apply

Flattens the whole stack into a single **Merged** layer; **repoints `firstFull`
to the full-size merge result** while **keeping `originalImage`** for
hold-to-compare; cleans the stack (removing every effect, including each beauty
layer, from the single `effectStack`), calls `flatten()` on the shared geometry
context, **and resets the controls of every tool to their default original
value**. The reset is all-inclusive and unconditional: it covers **every**
editor tool, not just the colour/photo-look ones — the transform rotation and
flips return to 0°/no-flip, the crop rectangle is cleared, the frame tiers and
colours revert to None/white, the background placement returns to fit/no
background, the beauty sub-tool values and colour selections revert to their
defaults, and the beauty pen strokes are cleared — so no control from the
merged stack still shows a stale value. After the merge every tool behaves as
if never used and edits apply fresh over the merged result. The `firstPreview`
and the lazy full-size caches (`currentImage`/`prevImage`/`lastImage`) are
invalidated/rebased on the merged `firstFull`. Revisiting a previously-applied
tool after a merge starts from a clean slate and applies fresh over the merged
result.

## Brush / pixel tools — normalized stroke layers

Brush / pixel tools (**pen, mosaic, blur brush, object removal**) are carried as
a `BitmapDiffEffect` whose source of truth is a **normalized stroke list**
(`strokes: List<StrokeLayer>`), **not** one entry per stroke-base. The renderer
rasterizes the strokes once over the running stack result inside `render`, so a
stack entry stays constant-size regardless of stroke count while remaining
reversible and re-applied in order.

- Re-editing a brush layer re-opens those strokes as the base for new strokes.
- Merging bakes the strokes into `lastImage`.
- The `diff` field is an **optional pre-baked painted-diff bitmap** — an
  equivalent representation used at merge time; the strokes remain the source of
  truth until then.
- `suggestedRegions` (privacy-mask suggestions from face detection) ride along on
  the mosaic layer.

Stroke shaping is carried as per-stroke parameters. For the **pen**, each stroke
stores a colour **opacity** (0–100%, translucent art) and **hardness** (0–100%):
100% = a hard-edged stroke solid across its whole size; 0% = only the single
centre pixel solid, fading outward to the size. A stroke is "solid" at the edge
only when no opacity is applied; with an opacity set, the whole stroke (solid
border included) is scaled by that opacity. Hardness applies only to the
**solid** and **rainbow** brushes — **glow** and **neon** strokes never carry it
(their hardness control is hidden in the panel). A **rainbow** stroke derives its
hue from the stroke position, so no colour is selected while it is active and the
colour picker is hidden. See `docs/effects_reference.md` (*Pen controls*).

## Text and sticker overlays — parameterised lists

**Text** (`TextEffect`) and **stickers** (`StickerEffect`) are carried the same
way the brushes are: the effect's source of truth is a **list of parameters**
(the `TextItem`/`StickerPlacement` list), rendered each fold by rasterising the
list over the running stack result — not a growing set of baked diff bitmaps.
A sticker's parameters are its sticker id (preset or emoji), position, uniform
scale, rotation, opacity, and — for colour-enabled stickers — its **fill
colour** (from the shared **colour picker**, see `docs/effects_reference.md` →
*Shared business rules* → *Colour picker*) plus the optional **line style**
(None/solid/dashed/dotted), **line thickness** and **line colour**. A **gallery
sticker** is carried the same way with
its picked image's **content URI** as the source (in place of a preset id);
the URI is decoded at preview/fold time and only baked when the layer merges.
Stickers are vector where possible (smooth, resolution-independent scaling);
raster stickers (from the **Gallery** entry) are resampled with **lohalo**
interpolation whenever rendered at other than 100%.

A `TextItem`'s parameters are content, colour (with opacity — a **translucent
glyph rendered over the outline / background as its own transparency, never
blended toward the outline colour behind it**), font family, size, alignment,
position, scale, rotation, the bold/italic/underline/strikethrough style set,
a shadow (**angle / distance / colour**), an outline (**size / colour / blur** —
blur `0%` = hard edge fading `100%` to transparent outward), and a background
shape with colour and opacity. Every colour (font, shadow, outline, background)
comes from the **shared colour picker** (see `docs/effects_reference.md` →
*Shared business rules* → *Colour picker*).

- Re-editing the tool re-opens those parameters: returning to the text tool
  loads every text item (content and all settings) back into the field and
  controls for in-place editing; returning to the stickers tool restores every
  placement (position, scale, rotation, opacity, tint); a re-committed change
  re-folds the tail.
- Merging bakes the items into `lastImage`, so a merge is the only operation
  that turns text/stickers into plain pixels and loses the parameters — the
  same behaviour as every other layer.

## The ordered assembly

There is **one** list (`effectStack`). Newly-applied effects are inserted at
their tool's canonical position; the user may then drag **any** entry —
including beauty and frames — to **any** position. The canonical/new-tool
assembly order is a *default* only; the live fold order always comes from the
user's `effectStack`:

1. **Beauty** — per-sub-tool layers in add order (`BeautyStackEffect`), folded at
   their user-chosen position like any other layer.
2. **Transform and crop** — two independent layers like any other:
   `TransformEffect` rotates/flips the running composite, `CropEffect` restricts
   it to the selected region. No fixed order between them or anything else.
3. **Adjustments / filter / enhance / style / background** — brightness,
   contrast, saturation, sharpen, exposure, shadows, highlights, temperature,
   tint, vignette, then color-filter, skin-denoise, auto-enhance, style-filter,
   background.
4. **Brush / overlay** — pen, mosaic, blur brush, object-removal strokes
   (`BitmapDiffEffect`), then stickers (`StickerEffect`), text (`TextEffect`).
5. **Frames** (`FrameEffect`) — an **ordered layer** folded in the same
    add-order as every other effect, not a fixed always-on-top border. A frame
    is **one layer carrying two tiers** — the **inner** and the **outer** frame
    (both default to None). Each tier is parameterised by its **style** — the
    base **shapes** (**solid / rounded / beveled / oval / heart**, heart being
    inner-tier only), then the **edge & bevel profile family** (**inward curve
    edge / corner eased edge / gear edge / ogee edge / double ogee edge /
    dupont edge / cove edge / french cove edge / dupont + ½ bullnose edge / ogee
    cove edge**), then the **ornamental / moulding styles** (**wavy / sawtooth /
    cogged / floral / waterfall / single wave / twin wave / recessed / offset
    wave / water waves inward / water waves outward**), and finally the special
    **Custom (Curved)** style whose own **angle / curve distance / strength /
    count** sliders parametrise the inward-curve / cove / french-cove /
    dupont+½-bullnose behaviours. The rounded and beveled shapes and every edge &
    bevel profile additionally take an **edge size**: 0–100% of the short
    dimension that drives how far the corner arc / chamfer / edge profile runs
    along the edge — `0%` = squared corner, `100%` = the treatment reaches half
    the short edge. **Edge size is driven by the edge-size slider alone and is
    independent of band thickness** — `FrameGeometry.fillZone` scales the
    treatment's footprint by the edge-size-derived `reach` (`swing =
    amplitude × reach`), **not** by band thickness, so dragging the thickness
    slider preserves the edge shape; the footprint is only compressed by `maxI`
    when thickness grows so large that opposite edges would otherwise meet/touch. Each tier also carries
    a **thickness** (0–100% of the short image dimension, applied at **half**
    the slider value — so `50%` is a quarter band) — **inward** for the inner
    tier, **outward** for the outer — a **colour** (from the shared colour
    picker, see `docs/effects_reference.md` → *Shared business rules* →
    *Colour picker*), and the **frame lines** (a **lines on/off** toggle; when
    on, a **1–10 lines** count, a **distance** and a **line thickness** for
    lines that draw concentric lines inward /
    outward of the band). **The outer tier enlarges the output**: when it is enabled the fold
    canvas is allocated `width + 2 × extent` × `height + 2 × extent` (extent =
    outer band + outer line extent + outward edge reach) with the photo inset by
    the extent, so the committed/saved dimensions grow — the dimension
    computation in `BitmapRenderer.render` and `FrameEffect.render` must add the
    outer extent.

Because beauty is just a `StackEffect` in the one list, the user can freely
interleave it (e.g. beauty → crop → a brush stroke → another beauty layer → a
frame) and drag it anywhere. `buildStackEffects` reads **only** `effectStack`
for both membership and order — it does not prepend a separate beauty bundle.

## Layers window

A **Layers button** in the editor UI opens a **Layers window** listing every
effect applied so far:

- **Thumbnail on the left** of each row (≤100×100 px, lazily generated).
- **Drag handle on the far left**: drag a row's move handle **up/down to
  reorder** the stack (undoable via `moveEffect`).
- **Reorder drag feedback (drop slot):** while a row is being dragged, the list
  shows exactly where it will land — the other rows **shift aside** so a blank
  **insertion slot** (a highlighted empty band, always visible, never under the
  dragged row) tracks the drop target, and the dragged row itself follows the
  finger as a **translucent ghost** riding on top of the list. The slot indexes a
  **half-row below/above** the neighbouring row's midpoint, so a drop sits in the
  visible gap between two rows. Releasing drops the dragged row into that slot;
  pushing the ghost to the top/bottom edge auto-scrolls while the slot keeps
  tracking the row it would land on.
- **Delete button on the right** with **confirmation before removal**.
- **Tapping a layer navigates to that effect's tool window** (a shortcut to
  re-edit). The **Merged** layer is special and has **no redirection** on tap.
- The **Merge Layers button is shown only in this window**, not on the
  individual tool panels.

### Removal recomputation

When one layer is removed, every following layer's preview image and the final image
must be **recomputed** — because each effect's output depends on the effects
before it, removing entry _k_ changes entries _k+1..N_ and the final bitmap.
This is enforced on every removal by re-folding the tail and re-rendering the
final image.

### Per-layer thumbnail strategy — the preview cache is the thumbnail

There is **no separate thumbnail bitmap** to generate. Each layer keeps a
**preview-sized cached image** (see *Performance / preview cache* below), so the
row thumbnail is simply that preview image scaled to fit the **≤100×100 px** box —
an instant, memory-light operation with no extra fold. Because every layer has a
preview image by construction, a thumbnail is always available the moment the
Layers window opens.

- A layer's preview image starts as `null` (stale) and is computed lazily; the
  Layers window reads whatever is already present and only (re)computes missing /
  stale entries.
- Whenever a layer's parameters change (a slider moved, a brush stroke
  committed, a style/filter/background/frame/crop updated), that layer's preview
  image is flagged stale, so it is lazily recomputed on the next need (window
  open, zoom, or full fold).
- **Staleness is prefix-contagious:** if a layer's preview is stale, then
  **every layer after it is stale by definition** — that changed layer's output
  feeds each later layer. We stop scanning at the first stale entry: all
  subsequent previews are recomputed too. This bounds the recompute to one
  contiguous tail starting at the first stale layer — exactly the effects whose
  outcome actually differs.
- Rationale: the window is opened infrequently and the editing loop (especially
  brush strokes) must stay fast. This composes cleanly with removal recompute
  (removing a layer invalidates the previews after it) and bounds memory to at
  most `N` preview-sized images.
- Re-committing an **already-stacked** layer at a non-tail position follows the
  same position rule as the full-size caches (see *Re-commit of a re-edited
  layer at a non-tail position*), applied on the fly: the preview of the
  re-edited layer and every preview after it is recomputed when next needed —
  no flag is ever persisted at preview scale.

### Revisiting an effect window without changing anything

If a tool is **revisited but no control is touched**, we take **no action** —
no invalidation, no re-fold, no re-render. Recomputation is triggered
exclusively by an actual parameter change (and by removal/merge).

```
originalImage ──► [beauty] ─► [crop] ─► [adjust] ─► [brush/sticker/text] ─► [frame] ─► result (shown)
```

## Beauty — per-sub-tool layers + geometry

Beauty is **not** a single base bitmap. Each beauty sub-tool is its **own
`BeautyStackEffect` layer** (`feature/editor/.../domain/BeautyStackEffects.kt` —
`BeautyAutoEffect`, `BeautySmoothingEffect`, `BeautyTeethEffect`,
`BeautyEyeBrightenEffect`, `BeautyDarkCircleEffect`, `BeautySpotEffect`,
`BeautyWrinkleEffect`, `BeautySkinToneEffect`, `BeautyEyeShadowEffect`,
`BeautyEyelinerEffect`, `BeautyFoundationEffect`, `BeautyHairEffect`,
`BeautyPenEffect`, `BeautyLipstickEffect`, `BeautyBlushEffect`, and the
per-dimension reshape family `BeautySlimEffect`,
`BeautyEyeEnlargeEffect`, `BeautyNoseEffect`, `BeautyJawEffect`), each carrying
**its own parameters** and stored **in the single ordered `effectStack`**
alongside every other effect. Because the stack is user-reorderable, beauty
layers can be dragged anywhere — including below or above other effects and
above or below deformations.

> **Every beauty effect is a fully independent single-purpose layer** — one
> sub-tool, one effect, one set of parameters, applied one-by-one onto stacked
> layers. The **one-layered-per-effect rule is strict**: an effect never bundles
> several pixel operations into one entry. In particular the **face-reshape
> family is split per-dimension** — **slim**, **eye enlarge**, **nose**, and
> **jaw** are **separate dedicated effects**, each its own `BeautyStackEffect`
> with its own single parameter, its own stack entry/layer, and its own
> `BeautyLayerSpec` tool key. Each is re-editable, reorderable and removable on
> its own, exactly like any other layer. (There is deliberately NO combined
> `BeautyReshapeEffect` / "Face Reshape" single layer.)

> **Skin denoise is NOT a beauty sub-tool.** It lives on the main toolbar as
> `EditTool.SKIN_DENOISE` / `SkinDenoiseEffect`, so it is not in this list.

All beauty effects ultimately call `feature:beauty`'s `BeautyProcessor`
(Hilt-provided, injected — effects are handed an instance rather than resolving
a singleton object). Each per-dimension reshape effect calls the matching
single-purpose `BeautyProcessor` method (`applySlim` / `applyEyeEnlarge` /
`applyNose` / `applyJaw`), so one stacked layer ⇔ one pixel deformation. There
is **no** legacy monolithic beauty base-swap: beauty is exclusively per-sub-tool
layers, and the fold always starts from `firstFull`, running left-to-right
over the user's stack.

### Shared geometry context (beauty + background)

Every tool that **requires geometry** — the beauty sub-tools and the
**background** tool (subject blur / flatten-replace-remove, whose subject mask is
its geometry) — follows the **same process**, backed by **one shared geometry
context**. The context is a **reference shared to all geometry-requiring tools**,
not owned by any single one.

- **What the context carries.** A **single retained geometry base bitmap**, the
  **geometry instance resolved against it**, a **stale flag**, and a **base
  guard** — a record of the exact base instance and its dimensions that the
  geometry was generated against, kept so a preview-resolution or
  pre-deformation fold never reuses a geometry that no longer matches the
  caller's pixel space. It is deliberately **not a list** of past geometries —
  stale instances live on only as long as the layer that carries them; once that
  layer is regenerated the value drops out of use and JVM GC frees it.
- **The geometry is an immutable value holding independently-resolvable
  fields** — for beauty a `BeautyGeometry` whose `faceResult` (face detection +
  dense 478-point face mesh), `hairPath` (hair segmentation) and `skinPath` (skin
  segmentation) are each resolved **on demand, only if a consumer asks for that
  specific field**; for background a **subject mask** (the mask of the subject
  that blur / replace / remove keeps apart from the background). The retained
  base bitmap lives for the segment's lifetime so that a field a later tool asks
  for can be computed against the **same already-available bitmap** instead of
  re-deriving the geometry base; it is recycled when the segment goes stale, when
  the context flattens, or by the adaptively-capped memory budget (see *Adaptive
  ML input resolution*) — never while a fold still holds it.

When a tool/effect **requires geometry** it calls an **editor-provided callback**
that **computes the geometry bitmap** — the bitmap up to the position of that
effect in the layer stack (see *Original geometry image and original geometry*).
That bitmap is **distinct from `originalImage`, `currentImage`, `prevImage` and
`lastImage`** — it is a **geometry-only** fold whose dimension is the **device
bound between 1 MP and 4 MP** (suitable for geometry, see *Adaptive ML input
resolution*) and whose content is the result of **folding only the deforming
effects** (crop, transform/rotate-flip, hard-stroke brushes (pen / mosaic /
object removal), a background Replace placement, the geometry-deforming beauty
dimensions slim / eye
enlarge / nose / jaw, plus occluding overlays sticker / text / frame) across the
gap over the geometry's grounding base.

**Resolve-vs-reuse.** Before generating anything the effect checks the
context's **stale flag** and the **base guard**:

1. If the context's last geometry is **not stale** (no geometry-changing effect
   sits between the base and this layer), the effect **reuses that geometry
   directly** — no fold, no ML run.
2. Only if the geometry is **stale or absent** (a geometry-changing effect is in
   the gap, or this is the first use) does the effect fold the deforming effects
   of the gap to reach the running image at its layer position, **resolve the
   geometry against that** (beauty: face landmarks / area masks; background: the
   subject mask), **replace the single last-geometry (or last-mask) slot**, and
   clear the stale flag.
3. Whether reusing or newly generating, the effect **stores the geometry it used
   as its own parameter** on the stacked layer — so **re-entering the effect
   reuses the stored parameter** rather than recomputing.

**Per-field resolution.** Each geometry field is resolved for the **fields the
requesting tool actually needs** — the tool declares its needed set (beauty:
`FACE` for every beauty effect, plus `SKIN`/`HAIR` only for the effects that
consume those masks: auto beautify, skin smoothing, spot removal, skin tone and
foundation use `SKIN`; hair color uses `HAIR`; background uses
`SUBJECT_MASK`). Consequences:

- **A generate runs only the ML stages for the needed fields.** A reshape /
  makeup session never pays the hair/skin segmenters; hair segmentation runs only
  when a hair-color layer actually needs it. (Face detection + mesh are needed by
  every beauty effect, so they always run for beauty.)
- **Fields resolve incrementally against the retained bitmap.** When the base is
  still valid (`!stale`, same instance + dims) but a newly-requested field is
  **absent**, only **that field** is computed against the context's retained
  geometry base and merged into the last-geometry slot — no re-fold, no
  re-derivation of the base, and the previously-computed fields are kept. Only a
  stale / wrong-dimension / different-base generate derives a fresh base from
  scratch.
- **The mask fields are skipped when all consumers are absent.** If a segment
  ends up wanting no `SKIN`/`HAIR`/`SUBJECT_MASK` field, that ML stage never runs
  for it at all.
- **Resolution happens in the suspend binding pass, never in `render`.** The
  per-field laziness is exercised while the editor binds each beauty layer's
  geometry (see *Lazy beauty geometry* below); effects receive an already
  resolved `BeautyGeometry` as their render parameter, so `render` stays
  synchronous and never triggers ML.

**What invalidates it.**

- Any effect that **changes geometry** (a deformation, or an occluding overlay /
  hard-stroke brush) marks the shared context **stale** the moment it is applied,
  added, removed or re-committed with different values — so the next
  geometry-requiring layer regenerates against the changed image, while
  pre-change layers (which hold their own geometry) are unaffected.
- **Re-entering a geometry-deforming effect and changing its values** also marks
  the context stale for the later geometry-based effects, so they regenerate on
  the next fold.
- **A layer move/removal before a geometry-based effect, with a deforming effect
  in between**, forces the **stored geometry parameter to be regenerated**: that
  effect is now a **middle layer**, not the last geometry consumer any more, so
  the context's last-geometry slot no longer represents its working base — the
  layer re-resolves against its new position instead of trusting the context.

**The fold never worries about which geometry to use** — each layer already holds,
as a parameter, the exact geometry instance it used. Consequences:

- **Share-until-stale falls out naturally**: the first post-stale
  geometry-requiring layer regenerates and replaces the last-geometry slot;
  subsequent geometry layers (stale clear, slot populated) reuse the same last
  geometry, so the expensive resolution runs once per segment.
- **Pre/post-geometry-change correctness falls out naturally**: a
  geometry-changing effect marks the context stale; the next geometry-needing
  layer regenerates against the changed image, while pre-change layers (which
  hold their own geometry reference) are unaffected.
- **Old geometry is freed by layer regeneration, not by the context**: when a
  layer carrying a stale geometry is re-folded it resolves a fresh geometry and
  stores it, dropping its reference to the old one; the old value then has no
  live reference and JVM GC releases it. The context never needs to accumulate a
  history to reclaim memory.
- **The editor stays geometry-agnostic**: it never reads the geometry slot, never
  decides REUSE vs RESOLVE; its only obligations are `markStale()` on
  geometry-changing effects and `flatten()` on merge.

### Merge and the geometry base

Merging flattens the stack into a single **Merged** layer. `originalImage` stays
frozen, so the geometry base must reflect what the merge actually baked:

- **`firstFull` is repointed to the merge result.** A merge bakes every effect
  into a single full-size flattened result, so `firstFull` (previously a
  reference to `originalImage`) becomes that **full-size merge result** (a fresh
  instance) — while `originalImage` itself stays frozen. From then on, folding the
  entire prefix starts from the merged `firstFull`, and hold-to-compare still
  uses `originalImage`.
- If **no geometry-changing effect** (deformation / reshape / occluding overlay /
  hard brush) was present in the merged stack, `originalGeometryImage` can remain
  a **reference to `originalImage`** — the two still match.
- If a **geometry-changing effect was merged**, `originalImage` no longer
  represents the pixel space the geometry must outline, so the merge
  **regenerates `originalGeometryImage`** as an **independent bitmap** (a new
  instance, no longer a reference) and the lazily-computed `originalGeometry` is
  invalidated/recomputed against it. `originalImage` itself is untouched —
  it remains the immutable pristine photo, used for hold-to-compare and as the
  ultimate base of the colour fold.

**On a merge the lazy colour caches must be invalidated** (they are keyed to the
old `firstFull` prefix); `firstPreview` is likewise rebuilt from the merged
`firstFull` so the first post-merge layer still folds from an always-present
preview base.

## Relationship to hold-to-compare

Press-and-hold compare keeps showing `originalImage` regardless of stack state
(including after a merge), so `originalImage` is never discarded. The full
per-effect interaction rules (which tools support compare, how the eye /
pan-zoom toggle behaves, and that compare reveals the **plain untransformed /
uncropped** original) are documented in `docs/effects_reference.md`.

## Undo / redo

Undo/redo is **stack-aware, whole-document, and chronologically ordered**.
`StackDocument` is a full snapshot of the **single `effectStack`** (which
includes every beauty layer) + every scalar control (including each beauty
sub-tool's own parameter) + crop/frame/filter/enhance/style/background state.
Each checkpoint carries an **operation kind** — `fine_grained` or
`coarse_grained` — and the history is **one ordered list**: entries are appended
in the order the events happen and undo/redo walk that list in event order, so a
coarse-grained operation that lands in the middle of a run of fine-grained
operations is undone/redone exactly in sequence.

**Operation kinds.**
- **Fine-grained operations** (`fine_grained`) — fine-grained, high-frequency
  edits: a committed **control change** (one-shot rotate/flip/crop, style,
  filter, enhance, frame, lens-filter toggle) and a committed **brush stroke**
  (pen, mosaic, blur brush, object removal). A slider drag does **not** record a
  checkpoint per tick — the value is live in the preview and the checkpoint is
  recorded at the commit/gesture boundary. Retained up to
  **`FINE_GRAINED_LIMIT` (default 30, tunable 20–50)**.
- **Coarse-grained operations** (`coarse_grained`) — document-structure edits
  the user expects to travel further back through: **layer commit** (the tool's
  parameters folded into the stack on lost focus), **layer removal** (Layers
  delete / beauty sub-tool turned off), and **layer position reorder / move**.
  Retained up to **`COARSE_GRAINED_LIMIT` (default 100)**.

**Two caps on the one list, order preserved.** When a checkpoint is appended the
history is trimmed per kind: the **oldest `fine_grained` entries** are dropped
while the `fine_grained` count exceeds `FINE_GRAINED_LIMIT`, and the **oldest
`coarse_grained` entries** are dropped while the `coarse_grained` count exceeds
`COARSE_GRAINED_LIMIT`. A long run of tweaks therefore evicts old fine-grained
checkpoints without evicting the deeper coarse-grained layer
commits/removals/moves, and the surviving entries keep their original relative
order — undo depth depends on what the user did (every fine-grained edit is
undoable for a brief window, every structural layer edit far further back).

**The layer commit is itself a checkpoint.** When a tool loses focus and its
changed parameters are folded into the stack (see *Commit on lost focus*), that
committed state is recorded as a **coarse-grained** checkpoint — a single Undo
right after switching tools reverts exactly that commit, removing the committed
layer entry, without discarding the undone state.

`saveToHistory()` runs on:
- **tool-switch** (`selectTool`) — the coarse-grained pre-tool baseline that
  begins the whole edit session;
- the **one-shot control commits** — rotate/flip/crop, style, filter, enhance,
  frame, lens filters (`fine_grained`);
- each **committed brush stroke** (`fine_grained`);
- the **layer commit on lost focus** (`coarse_grained`);
- **layer removal** — `removeStackEffect` (`coarse_grained`, snapshot before and
  after the mutation);
- **layer position reorder / move** — `moveEffect` (`coarse_grained`, snapshot
  before and after the move).

Opening the Layers window and tapping **Save** trigger the same
commit-on-lost-focus path and therefore the same coarse-grained checkpoint; the
session was already snapshotted at the tool-selection that began it, so undo
still reverts a whole edit session one checkpoint at a time.

`undo()`/`redo()` restore the whole document (a checkpoint never holds live
preview bitmaps — only scalars + the effect stack — so trimming frees memory
cleanly as the caps evict old entries). The top app bar exposes Undo/Redo
buttons.

## Scope / remaining work

- **On-device persistence** (saving a project with the effect stack to disk) is
  not supported — the stack is in-memory only; save always bakes the current fold
  result.
- The **lazy preview-cache + resumable full fold** performance model (see
  *Performance / preview cache*) is implemented across the editor: a per-layer
  preview sized to the visible pixel footprint (recomputed on any
  dimension-affecting commit — crop / transform), a background
  full-size fold triggered on **`displayScale` > 1** (colloquially "zooming past
  100%"; upscaled preview shown meanwhile,
  swapped in silently), lazy `currentImage`/`prevImage`/`lastImage` caches that
  resume from the recorded tool via the best-fit base-selection rule (last
  non-stale cache before the proposed position, with opportunistic mid-layer
  refresh), shared geometry (beauty + background) grounded in an
  **original geometry image** (a
  reference to `originalImage` until a deforming/occluding merge makes it an
  independent instance) plus a lazily-computed `originalGeometry`, that first uses
  the preview then swaps in the full-quality geometry-track geometry (the
  adaptive 1–4 MP resolution — never a full-size fold; blocking only on commit),
  folds only the geometry-changing gap across when stale, and never reuses the
  three colour caches,
  and the per-layer preview doubling as the Layers-window thumbnail — no separate
  thumbnail generation. Save/merge/share always use the full-size fold, so they
  produce byte-identical output to a from-base re-fold. The resumable fold uses
  `EditorViewModel.renderIncremental`, keyed by per-effect fingerprints with
  prefix-contagious invalidation (see the *Behaviour rules* above).
