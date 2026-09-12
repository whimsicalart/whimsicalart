# Gallery Reference

The **Gallery** is the app's landing screen: a browsable grid of every image on
the device, with sorting, grouping, tag filters, multi-select and a set of
**persisted viewing preferences**. The screen exposes photo-grid state,
selection, grouping and multi-select (`GalleryViewModel`, `feature:gallery`).

## Screen structure

A top app bar (title "Gallery", or a live selection count in multi-select mode)
is followed by:

- a **tag filter row** (a horizontal, scrollable row of toggleable scene-tag
  filter chips — see the scene-tag pipeline),
- a **Sort / Group** button in the top app bar (hidden in multi-select mode)
  that opens the **viewing-options panel**, and
- the scrollable content area: the **photo grid** in the flat view, or the
  **group grid** when a group-by dimension is selected. **Pull-to-refresh**
  re-reads the media store on the grid.

### Pull-to-refresh interaction

Pulling the grid down past the refresh threshold starts a non-disruptive
media-store re-read (see *Photo source*): the grid stays visible and the fresh
rows are diff-merged in, never replaced by a full-screen loader.

The refresh gesture gives the standard pull-to-refresh feedback in every phase:

- **While pulling**, a refresh **indicator** follows the gesture from the top
  of the grid, animating as the pull distance grows — the user always sees it
  as soon as they start pulling, not only once a refresh has begun.
- **After the threshold**, the indicator switches to a **spinning progress
  animation** for the duration of the re-read.
- **When the refresh completes**, the indicator plays a **fade-out** and leaves
  the screen. It never lingers after the re-read has finished, but it also
  never drops without the gesture feedback.

## Photo source

The gallery lists images from the MediaStore (`EXTERNAL_CONTENT_URI`), reading
per photo its id, content uri, display **name**, full **path** (and the derived
parent **folder**), **size**, **last modified**, **date taken**, **mime type**,
width and height. The leading grid ordering always follows the active **sort by**
preference (default: last modified, descending). The list refreshes on app
resume, media-store changes (ContentObserver, debounced) and pull-to-refresh
(see *Pull-to-refresh interaction*).

## Viewing preferences (sort & group)

Two independent, **persisted** settings shape the grid. Both are chosen from the
**Sort / Group** panel and take effect instantly; both are written to
preferences on every change, so whichever combination was last selected is
restored on the next app launch.

### Sort by

- A *field*: one of **Name**, **Path**, **Size**, **Last modified**,
  **Date taken** (default **Last modified**).
- A *direction*, always applicable: **Ascending** or **Descending** (default
  **Descending**).

Sorting is stable: photos sharing the same key fall back to the default
last-modified-descending order.

### Group by

- A *dimension*: **None** (default), **Folder**, **File type**, **Extension**,
  **Last modified (daily)**, **Last modified (monthly)**, **Date taken (daily)**,
  **Date taken (monthly)**.
- A *direction*, **Ascending** / **Descending**, which is shown but **does not
  apply** while the dimension is **None**.

## Flat view (group by = None)

A plain grid of square thumbnails ordered by the active **sort by** rule, each
photo shown with **no label**. Tapping a photo opens it in the editor; long-press
enters multi-select (see below).

## Grouped view (group by ≠ None)

The grid shows one **group tile** per group. Each tile uses the group's **first
photo** — the first under the active **sort by** ordering — as its thumbnail and
carries a **label** that names the group:

| Dimension | Label |
|---|---|
| Folder | the parent folder's name |
| File type | the MIME type |
| Extension | the file extension (suffix after the last dot) |
| Last modified (daily) / Date taken (daily) | the date, `yyyy-MM-dd` |
| Last modified (monthly) / Date taken (monthly) | the year and month, `yyyy-MM` |

Groups are ordered by the **group direction** (folders/file types/extensions by
plain text comparison; date buckets chronologically). Tapping a group opens a
**drill-in view** listing every photo in that group, still ordered by **sort by**.
Its top bar shows the group's label with a **back icon** that returns to the main
gallery, and the **system back gesture/button** does the same: pressing back while
a group is open closes the drill-in view (back to the gallery grid), **not** the
app. Multi-select and the tag filter behave the same inside a group.

## Permission & first launch

The gallery needs read access to the device's images (`READ_MEDIA_IMAGES` on
Android 13+; `READ_EXTERNAL_STORAGE` before). While the permission is missing it
shows a friendly prompt with a **Grant Permission** button instead of photo
content. Granting reloads the grid; the missing permission is requested as soon
as the gallery becomes the visible screen and no share is pending.

## Opening shared images (from other apps)

The app registers for `ACTION_SEND` (single `image/*`). A shared image opens
**directly in the editor**, read through the sender's transient grant — so the
missing media permission never blocks it and the first launch of the app through
a share works immediately. Accepting a share never routes the user to the
onboarding flow and never strands them on the gallery's permission prompt: the
gallery's permission request only surfaces when the user visits the
gallery.
