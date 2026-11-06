# Complete home rows — 0.4.1

2026-11-06, Asia/Jerusalem. Installed version 0.4.1 (code 8) on the authorized HY320.

The previous 198 dp home grid was shorter than two full rows, so remote navigation left part of a neighboring row cropped. Home now reserves exactly two 118 dp rows plus their 14 dp gap. Native fading-edge selection offsets are disabled for this grid. The inactive row is dimmed with a lightweight native foreground drawable; no blur, extra image raster or animation was added. Tile size, four-column layout, app-colored background, favorites, Settings and default-launcher behavior are preserved.

## Verification

The focused black-box test navigated every home row down, then back up, and checked every visible card against the grid bounds. At this device's density 320, each full tile remains **236 px** high in a **500 px** viewport.

- **6 distinct rows**, 13 captured row states, no partial-height/out-of-bounds tiles.
- First row, second row, scrolling beyond the initial rows, the final incomplete row, upward navigation, and restoring focus after Apps/Settings all checked.
- Visual inspection confirmed full neighboring tiles with the inactive row dimmed.
- App options, Apps and Settings remain reachable; both launcher-selection buttons are present. Neither default-selection action was invoked.
- Default HOME unchanged: `com.htc.htclauncherhighenglishd08/com.htc.activity.HomeActivity`. **13 present projection-related properties** and the disabled-package list remain unchanged. Favorites were not edited.
- Signed APK/source hashes match the installed artifact; no Vibe fatal-process/ANR entry found in the retained log capture. APK: **49,698 bytes**.

This checks remote D-pad navigation on the supplied 1080p/density-320 device. It is not a touch/fling or sustained-performance benchmark. No projector hardware setting, root, reboot, partition operation or system-package change was performed.

## Reproduce

```sh
python3 launcher/build.py
adb -s 192.168.10.15:5555 install --no-incremental -r launcher/build/vibe-home.apk
python3 launcher/check_home_rows.py --serial 192.168.10.15:5555
```

The checker navigates the UI and captures read-only state. It does not install anything itself, change defaults/favorites, invoke a hardware setter, reset counters or clear logs. Run only while authorized projector interaction is appropriate.

[Final second-row screenshot](test-results/20261106-row-visibility/navigation/final-second-row.png) · [Down to the third row](test-results/20261106-row-visibility/navigation/down-2.png) · [Result](test-results/20261106-row-visibility/navigation/result.json) · [Artifact summary](test-results/20261106-row-visibility/navigation/summary.json).

Installed APK SHA-256: `c68883d74a5ff2ddcd82cb0bba34da3ce2dd9814f789fb55cef2e7a4e3ed16de`. Commands and evidence hashes are retained beside the captures. The supplied HEIC was converted locally for inspection without changing the original; that photo and the previous APK remain in the ignored evidence directory.
