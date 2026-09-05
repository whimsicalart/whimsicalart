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
  mutated**. Used for hold-to-compare (press and hold reveals the original) and
  as the ultimate base of every stack replay. Never discarded, including after a
  merge.
- **`effectStack: List<StackEffect>`** — the **single, ordered, parameterised**
  effect list. There is **one** list, and **every** effect — adjustments,
  filters, overlays, frames, **and every beauty sub-tool layer** — is a
  `StackEffect` entry in it. A `BeautyStackEffect` is just another
  `StackEffect`; there is **no separate beauty store**. Each entry stores the
  full parameters of the tool at the moment it was **committed** (its last
  values on lost focus). Order matters: effect _N_ input is the committed output
  of effects _0..N-1_, so a beauty effect can sit anywhere and be interleaved
  freely (normal → beauty → normal → beauty → …). The user reorders the one
  list; `originalImage` is the base of the whole fold.
- **Committed milestones.** Each tool, when left ("lost focus") with **changed,
  non-default** parameters, is **committed**: its parameters are stored on its
  stack entry **and** its result is baked into a new **committed input bitmap**
  that becomes the input for the next effect. The working composite advances one
  committed milestone per tool. Committing is reversible — re-entering the tool
  recomputes the composite *before* it, restores the params, and re-commits at
  the **same stack position**, triggering a cascade fold of the tail.
- **`lastImage`** — the **final presentation image**: the composite of folding
  the whole committed stack **including the current effect** over `originalImage`.
  It is the last item of the fold (**`N`**), the final result, and is also the
  input for any next tool. It is what becomes a single **Merged** layer. It
  exists at **two resolutions**: during editing, the tail reapply always produces
  `lastImage` at **preview resolution** (cheap, one small fold); the **full-size**
  version is a **lazily computed cache** produced **only when the zoom exceeds
  100%** (or a full-res result — save / merge / share — is needed) — see
  *Performance / preview cache*. The canvas shows `lastImage` (eye ON — full
  composite) or the **limited preview** (eye OFF — current effect only, no tail);
  resolution follows the zoom and staleness rules below (at zoom ≤100% the
  preview-sized version; at >100% the preview-sized version immediately while the
  full-size is generated in the background, swapped in when ready — see
  *Zooming past 100%*).
- **`prevImage`** — the **base for the last effect** (**`N-1`**): the composite of
  every committed effect *before* the **last** one — the input the last effect is
  applied to in order to produce `lastImage`. It is needed because the last effect
  must be applied **on top of** `prevImage`, never on top of `lastImage` (which
  would re-apply it a second time) — and, on revert of the last tool, because
  `lastImage` must fall back to `prevImage`. Like `lastImage`, it exists at two
  resolutions: preview (always available during editing) and full-size (lazily
  computed on demand).
- **`currentImage`** — the **base for an effect in the middle of the stack**: a
  composite sitting **between the origin and `prevImage`** (`>0` and `<N-1`) — the
  composite of every committed effect *before* the tool currently being edited
  (the base that tool is applied to). On first entry of a brand-new tool,
  `currentImage` = `lastImage`; on re-entry of an existing tool, `currentImage` =
  the fold of effects `0..index-1` (see *Choosing the base image* below). Like `lastImage`, it exists at two
  resolutions: preview (always available during editing) and full-size (lazily
  computed on demand, only ever populated when a mid-stack effect actually needs
  a base).

**`currentImage` and `prevImage` coincide when the current tool is the last
effect** — then "the composite before the edited tool" *is* "the composite before
the last effect" (both `N-1`). In that case the same cached image plays both roles.

The image **roles** exist — `originalImage`, `currentImage`, `prevImage`,
`lastImage` — of which the **three cached images** (`currentImage`,
`prevImage`, `lastImage`) each have a **preview-sized** version (always produced
during editing, cheap) and a **full-size** version (lazily computed only when
zoom >100% or save is needed) — see *Performance / preview cache*. Each generated
cache carries a **staleness flag** plus a record of **up to which tool it was
applied**, so its fold can resume instead of restarting from the pristine base
(see *Resumable fold* and *Choosing the base image*).

**`originalImage` is always present at full size** — never mutated nor
discarded, including after a merge — and it is, by definition, **the very first
base image of the fold**. It therefore carries **no staleness flag and no trace
of "up to which tool it was applied"**: it needs no cursor, because it is
always the pristine starting point of any from-base fold.

Re-render is a **left-to-right fold**: `current = effect.render(current,
 context)` for each effect, starting from the pristine base. The fold recycles
superseded intermediates but never the caller-owned base.

> **`currentImage`/`prevImage`/`lastImage` are caches, not the mechanism.** They
> store fold results so a single slider drag or brush stroke re-runs **only the
> current effect plus everything after it** (the tail) instead of re-folding from
> the pristine base on every preview frame. Using them never changes the produced
> image — the fold itself is identical either way; the cache only avoids repeating
> the *prefix* part of the fold. The new model keeps the same semantics but makes
> these full-size caches **lazy** and pairs them with a per-layer **preview-sized
> cache** so the common path (inspecting / reordering layers, panning, showing a
> thumbnail) never touches a full-size bitmap.

## The fold renderer

The fold engine is `CascadeRenderer` (`feature/editor/.../domain/CascadeRenderer.kt`),
reused for **both** the per-layer preview images and the final full-size image, so a
layer preview is byte-consistent with the saved result (different layer previews and
the final image are the same fold at different resolutions).

- Each `StackEffect` implements a single self-contained method
  `render(current: Bitmap, context: Context): Bitmap`: it knows its own
  parameters and applies them to the running image, returning the next image.
  A transforming effect returns a **new** bitmap (leaving `current` owned by
  the fold); an overlay / draw-in-place effect mutates `current` and returns it;
  a no-op returns `current`.
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
- **Occluding overlays** — stickers, text, frames — which can place something
  **over the subject** that the geometry needs to outline around.
- **Hard-stroke brush tools** — the set that hard-covers the subject:
  **pen, mosaic, and object removal**. **Face makeup** brushes (and the **blur
  brush**) do **not** hard-cover and do **not** invalidate geometry — face makeup
  maps onto the existing facial geometry rather than redrawing it. (Some tools'
  behaviour here is expected to change in the future; this is the current set.)

Everything else (colour adjustments, filters, smooth/teeth/spot/skin-tone beauty,
etc.) leaves geometry untouched.

## Performance / preview cache

Interactive editing feels instant and memory stays flat because the common path
never touches a full-size bitmap. The architecture is a **per-layer preview-sized
cache** plus a **lazily computed, resumable full-size fold**. Only
**`originalImage` is always present at full size**; every other image
(`currentImage`, `prevImage`, `lastImage`, the final composite) is produced on
demand.

### Per-layer preview cache

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

### Choosing preview resolution

The **preview scale multiplier can change after a crop** (a crop alters the
source footprint), so the preview dimensions are **not a fixed constant**.
Instead, each layer's desired preview dimensions are computed from the
**visible area** — the pixel footprint of the region where the preview will
actually be drawn:

```
previewSize = visibleDrawnPixels(viewWidth, viewHeight, zoom)
            + margin                    // some headroom so it looks sharp while panning
previewScale = previewSize / fullSize   // per-layer multiplier; updated on crop
```

This per-layer preview scale builds/downscales the preview cache, and decides the
target resolution whenever anything requests a "sharp-enough upcoming zoom"
render.

### Zooming past 100% (background full-size fold)

Full-size work runs **only when the user actually zooms past 100%** (or a zoom
was already set) **or needs a full-res result (save)**. At that moment the
full-size `lastImage` is computed in the **background**:

```
fullSize = fold( currentImage, [currentEffect] + effectsAfterCurrent )
```

The full-size fold is **debounced** before it is launched, so rapid zooming does
not spawn a new fold per frame: it starts **≈500 ms after the pinch fingers are
released** (when the release is detectable), or **≈2 s if the fingers remain on
screen but hold still** — whichever applies; both debounce windows aim to fire
only once the user has settled on a scale.

**What the canvas shows (the display decision tree).** The canvas shows either
`lastImage` (eye ON — full composite) or the **limited preview** (eye OFF —
current effect only, no tail — see *Previewing a tool*). In both cases the
resolution follows the same rules:

- **Zoom ≤100%, full-size image absent or stale**: show the **preview-sized
  version** — this is the common path; the small image is sharp enough at this
  scale.
- **Zoom ≤100%, full-size image present and non-stale**: show the **full-size
  version** — no reason to fall back to the small preview while the full-size
  result is still valid (e.g. the user zoomed past 100%, the full-size image was
  generated, and they zoomed back out).
- **Zoom >100%**: show the **preview-sized version scaled up** with
  proportional scale and panned to the current viewport — it is scaled only to
  *simulate being at the full-size resolution*, serving as a stand-in; the canvas
  never blocks and there is no dead frame. Once the background fold completes, the
  full-size image is **swapped into the preview in place**, silent and following
  the current scale and pan.

**Control changes during a background fold.** When a **control value changes**
while the background full-size processing is in flight, the in-flight fold is
**cancelled and restarted** with the new values; while it is not ready again, the
preview keeps showing the small preview-sized image (the full-size is stale).
When a control changes but **no** full-size work is in flight, the tail reapply
immediately produces the new preview-resolution `lastImage` (shown promptly) and
the full-size image is regenerated in the **background** (again debounced) and
swapped in when ready. For a **beauty tool**, the full-size result also becomes
stale when the background geometry became ready in the meantime — the preview
must be regenerated against the fresh geometry.

### All `lastImage` logic is lazy

The three cached images — `currentImage` / `prevImage` / `lastImage` — each
exist at **two resolutions**:

- **Preview resolution**: always produced during editing via tail reapply (cheap,
  one small fold). This is the version used for the canvas in the common path
  (zoom ≤100%) and as a stand-in while the full-size version is being generated.
- **Full-size resolution**: computed **only when the user zooms past 100%** (or a
  zoom was already set) **or when a full-resolution result is needed (save)**.
  Everything else runs against the **preview-sized cache**.

When a **control value changes**, the tail reapply immediately produces the new
preview-resolution `lastImage` (and all preview-sized caches after the current
effect's position become stale and are re-folded as part of the tail). The
**full-size** caches after the current effect are also marked stale; if a
full-size image is in flight it is **cancelled and restarted** (see *Zooming past
100%*).

- **Save / merge / share** require the true full-size result and **await its
  completion** — this is the one blocking case — but they do **not** always fold
  from the base: they **follow the same resumable-base logic as any zoom>100%
  fold**. If `lastImage` is already fresh (non-stale), save can use it directly
  with no fold at all. If not, it folds **only the minimum**: if `prevImage`
  exists and is non-stale, resume from `prevImage` and apply the tail; if
  `currentImage` exists and is non-stale, resume from `currentImage` and apply
  the remaining tail; only if **neither** is available and non-stale does it fold
  the full stack from `originalImage`. This is the same resumable logic driven by
  the staleness flags and resume cursors — see *Resumable fold*.
- **Every other operation** (panning, layer inspection, reorder preview, a
  thumbnail) serves from the preview cache and — where a full-size image is
  momentarily needed but absent — **scales the preview** until the full image is
  generated, then uses it.

> **Non-blocking golden rule.** The only operations that ever await a full-size
> fold are **save / merge / share**. Zooming past 100% and control changes serve
> the upscaled preview immediately and regenerate the full-size image in the
> background — the UI never blocks on a full fold.

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

### Original geometry image and original geometry

Geometry is grounded in a dedicated base that survives merges — distinct from the
three lazy colour caches:

- **`originalGeometryImage`** — the bitmap the facial geometry is traced from.
  Its **resolution starts identical to `originalImage`** (full-size), and it
  initially **is the same object as `originalImage`** (a **reference**, sharing
  the memory). `originalImage` itself is **immutable and never changes**. After a
  merge, if any **geometry-changing effect** (a deformation — crop / transform /
  reshape / rotate-flip — or an occluding overlay (sticker / text / frame) / a
  hard-stroke brush (pen / mosaic / object removal)) was
  folded, the geometry base can no longer equal the pristine photo, so the merge
  **regenerates `originalGeometryImage`** as an **independent bitmap instance**
  — independent from `originalImage`, which stays frozen. The regenerated image
  is produced by **folding all the deformation effects over `originalImage`**,
  which **cascades the resolution-affecting effects too**: its resolution may
  therefore differ from the original (a crop shrinks it; a rotation that enlarges
  grows it), but it is **always grounded in the initial full-size image**. If
  **no** geometry-changing effect was merged, `originalGeometryImage` is **kept
  as it was** (still a reference / still matching `originalImage`) — there is
  nothing to regenerate.
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

**How the beauty effect obtains that base.** The beauty tool is handed an
**interface/callback** the editor implements: a method the beauty tool can call
to ask the editor to **fold the stack up to the beauty tool's (proposed) layer
position applying only the deforming tools**, and return the resulting bitmap —
or, when **no deforming tool exists in that range**, to return
**`originalGeometryImage`** directly (no fold). The beauty effect calls this
method to obtain the image to resolve its geometry against (handling the stale /
absent cases above), so the editor stays the single owner of the deformation
fold while the beauty tool only consumes the returned base. The returned base is
then folded/resolved at preview size first and full size in the background, per
*Lazy beauty geometry*.

> **Geometry is a separate process and does not reuse the three cached images**
> (`currentImage` / `prevImage` / `lastImage`). The geometry pipeline works from
> `originalGeometryImage` (+ the lazily-computed `originalGeometry`) and, when a
> gap contains deforming/occluding effects, folds only those across to reach the
> layer's position — it neither seeds itself from nor populates the full-size
> `current`/`prev`/`last` caches. The lazy-cache base-selection (see *Choosing
> the base image*) governs only the **colour/fold** images, while geometry
> resolution stays on its own track.

### Lazy beauty geometry (on stale geometry)

When a beauty tool's geometry is **stale** (a geometry-changing effect sits
between `originalGeometryImage` and the layer) or **absent** (needed for the
first time), the real geometry must be resolved at full size. A beauty tool's
geometry is **not recomputed on every control change** — it is recomputed only
when it goes stale/absent, so a full-size resolution happens rarely (once per
tool entry into a gap, or after a geometry-changing effect is
edited/added/removed). Still, a full-size ML resolution (FaceLandmarker +
segmentation) is expensive, so the editor never blocks the UI on it at tool
selection:

1. **First resolves geometry over the already-ready preview image** (fold the gap
   at preview resolution) and uses that for the time being — the user sees a
   correct, if lower-resolution, deformation immediately and can start
   interacting with it instead of waiting on a stall.
2. **In the background**, folds the gap **at full size applying only the
   geometry-changing effects** (no full-res colour work for the geometry pass),
   then **resolves the geometry against that**.
3. Swaps the exact full-size geometry in once ready.
4. **On commit, blocks until the final (full-size) geometry is ready** before
   actually committing — the committed layer must carry the correct geometry. The
   user can keep interacting with the temporary preview-geometry in the meantime;
   the block only gates the commit itself.

> **Why keep the two-stage (preview-then-full-size) flow rather than always
> blocking on the full-size geometry.** Because geometry is *not* recomputed on
> control changes, the number of full-size resolutions is already small; the
> question is only whether to *wait* on the one that does happen. Full-size ML
> resolution is slow enough that a hard block at **tool selection** — the moment
> the user is deciding to engage — would be a noticeable stall. The provisional
> preview-sized geometry costs one cheap, fast pass (small-image ML + a
> preview-resolution gap fold) and buys instant, non-blocking entry; the
> expensive full-size pass then runs once in the background and is the only thing
> ever awaited, and only at commit. This mirrors the zoom-preview principle
> (never block except on save/commit) and the preview pass is inexpensive
> compared with the raw full-size result it avoids waiting on.

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
image from the set **(`originalImage`, `currentImage`, `prevImage`)** that sits
**before the current effect's proposed position** in the stack. *Proposed*
because the effect may not yet be committed — it commits **only if it changed AND
the change differs from the default values**. Concretely:

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
  are folded** on top to reach the effect's base — i.e. from the reused `current`
  up to `prev` (`N-1`), no more.
- If all the candidates are **stale**, the fold starts from **`originalImage`**.

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
  **`originalImage`** and neither `currentImage` nor `prevImage` is touched —
  there is nothing before it to cache.

Because all three caches are lazy and only `originalImage` is guaranteed present
at full size, the above selection runs only when a zoom-past-100% or save
actually needs a full-size base; in the common path the preview-sized cache
serves the request.

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
each change. **Every control change marks all preview-sized caches after the
current effect's position stale** (full-size caches after it are likewise stale;
see *Staleness is prefix-contagious* in *Per-layer preview cache*), and the
tail reapply produces the new **preview-resolution `lastImage`** — always, on
every change, regardless of zoom level:

- Editing a **non-last** tool: `lastImage = fold(currentImage, [currentEffect] +
  effectsAfterCurrent)`.
- Editing the **last** tool: `currentImage = prevImage` and the tail is just the
  single effect, so `lastImage = currentEffect.render(prevImage)`.
- **Adding a new tool**: `currentImage = lastImage` and the tail is the single new
  effect, so the new `lastImage = newEffect.render(lastImage)`; the previous
  `lastImage` becomes the new `prevImage` (and the edited tool's input).

**What the canvas shows depends on the eye button** (see
`docs/effects_reference.md`, *Two preview/compare categories*):

- **Eye ON**: the canvas shows `lastImage` — the full composite (prefix +
  current effect + tail). Resolution follows the zoom/staleness rules below.
- **Eye OFF** (Category A only — Category B eye is off by default but controls
  pan/zoom too): the canvas shows a **limited preview** — the current effect
  applied to `currentImage` with **no tail folded**. This lets the user see the
  isolated contribution of the current effect and everything before it. The
  limited preview is computed cheaply as `currentEffect.render(currentImage)`.
  `lastImage` is still computed as the full composite (it is needed for save
  and for the next tool's base); only its *display* is suppressed.

The **canvas always shows either `lastImage` or the limited preview**, choosing
the resolution per the zoom and staleness rules:

- At zoom **≤100%**: the preview-sized version is shown. If a full-size
  `lastImage` exists and is **non-stale**, it is shown instead (no reason to
  fall back while the full-size result is still valid).
- At zoom **>100%**: the preview-sized version is shown immediately while
  the full-size version is generated in the background (debounced); the full-size
  image is **swapped in silently** when ready — see *Zooming past 100%*.

So `lastImage` is always the **full composite** — derived by applying the tail
on top of `currentImage`, never by mutating an older `lastImage` in place —
which is exactly why the previous `lastImage` (the one before this edit) must be
kept as `prevImage` until the new result supersedes it.

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
   `0..index-1` before it (from `originalImage`).
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
for the fact that this invalidates the shared beauty-geometry context. Reapplying
that tail is
equivalent to **removing and re-adding the affected layers in the same
position, but with fresh geometry**: the effect calls `markStale()`, so the
next geometry-needing beauty layer in the tail **regenerates** against the
changed image rather than reusing a stale geometry — exactly as a fresh add
would. So a mid-stack edit over (or followed by) a geometry-changing effect never
reuses geometry that no longer matches the pixel space; it recomputes the
affected layers with new geometry. (Mechanism is the `markStale()` / regenerate
flow in *Shared beauty geometry context* below.)

### Editing an existing effect (already in the stack)

Re-entering a tool restores the slider(s) to the stored parameters of its stack
entry, and reopens it **at its current stack position** (see *Commit on lost
focus*). Moving a slider picks `currentImage` as above, applies **only the
current effect plus the tail**, and refreshes `lastImage`; committed effects
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
footprint, this diff's drawn pixels can become **mispositioned** relative to the
image content:

- The **easy (lazy) approach — used today**: the diff bitmap is simply **aligned
  to fit the new footprint** — middle-aligned / rescaled to match the new
  resolution. This is approximate and can, in a shrink-then-grow scenario (draw
  after a shrink, then grow the crop back), leave parts of a drawing missing.
- The **best approach (documented for later re-evaluation)**: **cascade the
  crop/transform onto the following brush/diff effects**, transforming their
  diff so the drawn pixels track the image content exactly. This is more work and
  is deferred; the easy approach is the current behaviour, with the limitation
  noted so it can be revisited.

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

Flattens the whole stack into a single **Merged** layer; **keeps
`originalImage`** for hold-to-compare; cleans the stack (removing every effect,
including each beauty layer, from the single `effectStack`), resets tool
controls to neutral, and calls `flatten()` on the shared beauty-geometry
context. Revisiting a previously-applied tool after a merge starts from a clean
slate and applies fresh over the merged result.

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
   add-order as every other effect, not a fixed always-on-top border.

Because beauty is just a `StackEffect` in the one list, the user can freely
interleave it (e.g. beauty → crop → a brush stroke → another beauty layer → a
frame) and drag it anywhere. `buildStackEffects` reads **only** `effectStack`
for both membership and order — it does not prepend a separate beauty bundle.

## Layers window

A **Layers button** in the editor UI opens a **Layers window** listing every
effect applied so far:

- **Thumbnail on the left** of each row (≤100×100 px, lazily generated).
- **Drag handle on the far left**: long-press + drag a row **up/down to
  reorder** the stack (undoable via `moveEffect`).
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
`BeautyPenEffect`, and the per-dimension reshape family `BeautySlimEffect`,
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
layers, and the fold always starts from `originalImage`, running left-to-right
over the user's stack.

### Shared beauty geometry context

Beauty rendering needs resolved ML geometry — `BeautyGeometry` (`faceResult` +
optional `skinPath`/`hairPath`), an **immutable value**.

The editor holds a single **shared geometry context**
(`BeautyGeometryContext` in `feature:beauty/.../domain/BeautyGeometryContext.kt`);
`DefaultBeautyGeometryContext` implements it and is wired to the
`BeautyGeometryGenerator` / ML pipeline. The editor does **NOT** read the
context's data; its only interactions are two explicit operations:

- **`markStale()`** — called when an effect that **changes geometry** is applied:
  a **deformation** (rotate CW/CCW, H/V flip, crop, transform) **or** a
  geometry-affecting beauty op (**slim / eye enlarge / nose / jaw**) **or** an
  **occluding overlay** (sticker / text / frame) **or** a **hard-stroke brush**
  (pen, mosaic, or object removal) — anything that repositions or occludes
  what the geometry must outline. This is the editor's single, uniform stale role
  for ALL geometry-changing effects.
- **`flatten()`** — called when merging layers.

The context holds exactly two pieces of state: a **single last geometry** and a
**stale flag**. There is deliberately **no list** of past geometries — old
`BeautyGeometry` instances live on only as long as the layer that carries them;
once that layer is regenerated the value drops out of use and JVM GC frees it.

**Every beauty effect shares the same context**, and the *beauty effect* (not
the editor, not the fold) is **fully responsible** for creating geometry and
manipulating the context's data, **lazily** via `geometryFor(image)`:

1. When a geometry-needing beauty effect is applied it **reuses the last
   geometry** (from the single last-geometry slot) — valid when no
   geometry-changing effect sits between `originalGeometryImage` and this layer.
2. If there **is no last geometry yet**, **or** the **stale flag is true** (a
   geometry-changing effect is in the gap), it **folds the geometry-changing
   effects of the gap** over `originalGeometryImage` to reach the running image
   at this layer's position, **resolves the geometry against that**, **replaces
   the single last-geometry slot**, and clears the stale flag. (Its source of
   normalisation is `originalGeometryImage` + the lazily-computed
   `originalGeometry` — see *Original geometry image and original geometry*.)
3. Whether reusing or newly generating, the effect **stores the geometry it used
   as its own parameter** on the stacked layer.

**The fold never worries about which geometry to use** — each layer already
holds, as a parameter, the exact geometry instance it used. Consequences:

- **Share-until-stale falls out naturally**: the first post-stale beauty effect
  regenerates and replaces the last-geometry slot; subsequent beauty effects
  (stale clear, slot populated) reuse the same last geometry, so ML runs once per
  segment.
- **Pre/post-geometry-change correctness falls out naturally**: a
  geometry-changing effect marks the context stale; the next geometry-needing
  beauty layer regenerates against the changed image, while pre-change layers
  (which hold their own geometry reference) are unaffected.
- **Old geometry is freed by layer regeneration, not by the context**: when a
  layer carrying a stale geometry is re-folded it resolves a fresh geometry and
  stores it, dropping its reference to the old one; the old `BeautyGeometry`
  then has no live reference and JVM GC releases it. The context never needs to
  accumulate a history to reclaim memory.
- **The editor stays geometry-agnostic**: it never reads the geometry slot, never
  decides REUSE vs RESOLVE; its only obligations are `markStale()` on
  geometry-changing effects and `flatten()` on merge.

### Merge and the geometry base

Merging flattens the stack into a single **Merged** layer. `originalImage` stays
frozen, so the geometry base must reflect what the merge actually baked:

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

## Relationship to hold-to-compare

Press-and-hold compare keeps showing `originalImage` regardless of stack state
(including after a merge), so `originalImage` is never discarded. The full
per-effect interaction rules (which tools support compare, how the eye /
pan-zoom toggle behaves, and that compare reveals the **plain untransformed /
uncropped** original) are documented in `docs/effects_reference.md`.

## Undo / redo

Undo/redo is **stack-aware and whole-document**. `StackDocument` is a full
snapshot of the **single `effectStack`** (which includes every beauty layer) +
every scalar control (including each beauty sub-tool's own parameter) +
crop/frame/filter/enhance/style/background state. `saveToHistory()` runs on
**commit on lost focus** — tool-switch (`selectTool`), opening the Layers
window, and **Save** — as well as on one-shot commits (rotate/flip/crop, style,
filter, enhance, frame, and re-order); `undo()`/`redo()` restore the whole
document. The top app bar exposes Undo/Redo buttons.

## Scope / remaining work

- **On-device persistence** (saving a project with the effect stack to disk) is
  out of scope for the first iteration — the stack is in-memory only; save
  always bakes the current fold result.
- The **lazy preview-cache + resumable full fold** performance model (see
  *Performance / preview cache*) is implemented across the editor: a per-layer
  preview sized to the visible pixel footprint (recomputed on crop), a background
  full-size fold triggered on **zoom past 100%** (upscaled preview shown meanwhile,
  swapped in silently), lazy `currentImage`/`prevImage`/`lastImage` caches that
  resume from the recorded tool via the best-fit base-selection rule (last
  non-stale cache before the proposed position, with opportunistic mid-layer
  refresh), lazy beauty geometry grounded in an **original geometry image** (a
  reference to `originalImage` until a deforming/occluding merge makes it an
  independent instance) plus a lazily-computed `originalGeometry`, that first uses
  the preview then swaps in the full-size geometry (blocking only on commit),
  folds only the geometry-changing gap across when stale, and never reuses the
  three colour caches,
  and the per-layer preview doubling as the Layers-window thumbnail — no separate
  thumbnail generation. Save/merge/share always use the full-size fold, so they
  produce byte-identical output to a from-base re-fold. The resumable fold uses
  `EditorViewModel.renderIncremental`, keyed by per-effect fingerprints with
  prefix-contagious invalidation (see the *Behaviour rules* above).
