# WhimsicalArt User Guide

WhimsicalArt is a free, ad-free Android photo editor. This guide walks through
the main features. Everything works offline and requires no account.

## Getting started

1. Allow photo access when prompted so the gallery can list your photos.
2. Pick a photo from the gallery to open it in the editor.

## The editor

The editor toolbar at the bottom lists every tool. Selecting a tool opens its
controls panel. Most tools are applied to the live preview in real time.

### Save and share

Tap the check mark in the top bar to open the save dialog. From there you can
save the photo or share it with another app. Configure:

- **Format** — JPEG (with a quality slider) or PNG (lossless)
- **Resolution** — Original, High, Medium, or Low

### Adjustments

Brightness, contrast, saturation, exposure, shadows, highlights, temperature,
tint, and vignette each have a slider. Sharpening adds detail. **Skin denoise**
and **auto-enhance** are one-tap tools on the same toolbar. Every adjustment is
a reorderable layer; see **Layers** below.

### Cutting, rotating, flipping

- **Crop** — choose an aspect ratio (Free, 1:1, 4:3, 16:9, 3:2, 9:16), then drag
  the selection rectangle's edges/corners (or pinch to scale it).
- **Transform** — rotate left/right in 90° steps, mirror horizontally or
  vertically, and use the **free rotate** slider for an arbitrary angle.

### Filters

The **Filters** tool splits into **Color Filters** (single-select colour-matrix
looks), **Style Filters** (Film/Vibrant/Matte tone-map looks), and **Lens
Filters** (multi-select physical lens effects: clear, UV, polarizers, ND,
infrared, soft, star, bokeh, anamorphic, astro). Select a filter to preview it;
lens filters stack independently.

### Text

The **Text** tool lets you add text overlays. Choose a font, size, color,
alignment, background shape, stroke, and shadow.

### Stickers

The **Stickers** tool opens a picker grouped by category (Emoji, Nature, Food,
Animals, Objects, Decoration). Tap a sticker to place it, then drag to move,
pinch to resize or rotate, and adjust opacity or mirror it.

### Decorative tools

- **Mosaic** — pixel, blur, or custom-pattern brushes with an eraser.
- **Blur brush** — blur parts of the image by brushing over them.
- **Pen** — freehand drawing with solid, glow, neon, and rainbow brushes,
  plus layer management and undo/redo.

### Beauty tools

The **Beauty** tools (from the editor top entry point) use face detection to
support skin smoothing, blemish and wrinkle removal, teeth whitening, face
reshaping, and makeup (lipstick, blush, eye shadow, eyeliner, foundation,
hair color).

### Collage

The **Collage** maker combines 2–9 photos into grid or free-form layouts with
controls for borders, background, and templates.

### Object removal and background blur

Object removal provides a brush you paint over the object to remove with
content-aware fill. Background blur (bokeh) simulates portrait depth with
edge-aware blur and manual brushing.

### Layers & preview

Every applied edit is a **layer** in an ordered, reversible stack. Open the
**Layers** window to see them, drag rows to reorder, tap a row to re-edit that
tool, and use **Merge Layers** to flatten (or the ✕ to delete a layer). You can
**pinch to zoom and drag to pan** the preview on any tool; the **eye** button
controls whether later (tail) effects are folded into the preview — on by
default for adjustment/transform tools, off by default for draw/place tools.
On draw/place tools (crop, frames, stickers, text, brushes) the eye also
enables pan/zoom and hold-to-compare. See
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
