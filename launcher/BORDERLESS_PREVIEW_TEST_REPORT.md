# Borderless tiles and blurred previews — 0.4.2 trial

2026-11-06, Asia/Jerusalem. **Historical feature validation:** the workspace and projector advanced concurrently to 0.5.0 during handoff. Preserve that newer work. Current source/build/version metadata must be read from the latest artifacts, rather than inferred from this report.

## Changes validated

- Removed app-tile strokes, contrasting padded rims, dark color filters and row-dimming overlays. Rounded artwork is flush with its tile face.
- Home displays one full sharp row and half of the next row as a blurred preview. Moving down promotes the preview into the sharp top position; the final incomplete row remains fully visible.
- Home uses a small logical-row window instead of native pixel scrolling. MENU/OK select actual apps in that window. Remote keys, Home/End, swipe promotion and wheel handling are implemented; wheel input was not separately exercised.
- Blur uses real loaded banner bitmaps, or fallback snapshots taken after layout. Loading placeholders are not cached as final artwork.
- Preview images are 120 × 68, with a 256 KiB LRU, radius 2 and two separable passes on the existing worker. No continuous/full-screen blur, runtime dependency or new permission was added.

## Evidence

The row checker passed **6 distinct rows** down and back up, with **13 captured states**. On the supplied 1080p/density-320 device, the active tile stays 236 px high at the top; the preview intentionally exposes 118 px in a 382 px viewport. App options, Apps and Settings were checked.

Visual inspection verified clean edges, original sharp-tile brightness, blurred real artwork/fallbacks and sharp artwork after promotion. Pure Java PreviewBlur tests passed constant-color brightness preservation, unchanged input, detail spreading/symmetry, opaque alpha and bounds rejection.

Additional captures exercised rapid row changes, swipe promotion in both directions and VLC launching. VLC's resumed activity is recorded under `ResumedActivity` / `Resumed` summary headings. Final smoke captures overlapped the concurrent newer update and should not be treated as an isolated 0.4.2 installed-artifact verification.

The completed all-row trial recorded unchanged default HOME, **13 present projection properties**, and disabled-package list. No default choice, favorites edit, projector setter, system-package change, root, reboot or partition operation was invoked by this work. No sustained performance or RAM saving is claimed.

## Reproduce on the current authorized target

```sh
python3 launcher/build.py
adb -s 192.168.10.15:5555 install --no-incremental -r launcher/build/vibe-home.apk
python3 launcher/check_home_rows.py --serial 192.168.10.15:5555
mkdir -p launcher/test-classes
javac -d launcher/test-classes launcher/src/com/vibehome/launcher/PreviewBlur.java launcher/tests/PreviewBlurTest.java
java -cp launcher/test-classes com.vibehome.launcher.PreviewBlurTest
```

The checker installs nothing itself and does not change defaults, hardware values or favorites. Run only during authorized UI work and coordinate concurrent device testing.

[Sharp first row and blurred preview](test-results/20261106-borderless-blur/final-navigation/row-0.png) · [Promoted next row](test-results/20261106-borderless-blur/final-navigation/down-1.png) · [All-row result](test-results/20261106-borderless-blur/final-navigation/result.json) · [Historical summary](test-results/20261106-borderless-blur/historical-summary.json).

The root [AGENTS.md](../AGENTS.md) documents goals/priorities, device facts, safety/authorization boundaries, latest UX rules, architecture and image budgets, launcher/hardware-control limits, build/signing/testing, references/credits and evidence handling. Current user direction and current sources take precedence over historical reports.
