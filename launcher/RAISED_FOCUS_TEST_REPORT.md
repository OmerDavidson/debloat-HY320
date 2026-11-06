# Raised tile focus — 0.5.1

2026-11-06, Asia/Jerusalem. Installed **0.5.1 (code 11)** on the authorized HY320, with its unchanged 1920 × 1080 display and density 320.

Removed the white app-name capsule from app tiles. Focus now uses a stronger 140 ms scale/lift: artwork scales from 0.90 at rest to 1.10 when selected, making it approximately **22% larger than its neighbors**, with an additional **8 dp upward translation**. Internal headroom keeps the enlarged artwork inside its cell. The app title above Home, fallback artwork labels, borderless faces, undimmed artwork, cached blurred preview, and existing row/tab glides remain.

## Verified

- [All-row check](test-results/20261106-raised-focus/rows/result.json): **5 current Home rows**, 11 captured states, down/up through the final row. Active cells remain 236 px high in the 382 px viewport, with a 118 px preview. The checker now asserts badge absence, unclipped focused artwork, substantially larger artwork than neighboring tiles, and a visibly higher top edge.
- [Additional focus checks](test-results/20261106-raised-focus/extra-result.json): left/right edge tiles, rapid row reversals followed by MENU, clearing the lift when focus enters navigation, raised focus in Apps, and launching/returning from VLC. Screenshots show no badge, border, rim, or dimming.
- [Artifact verification](test-results/20261106-raised-focus/verification.json): pulled installed APK matches the retained build and source hashes; manifest/About are 0.5.1/code 11. The workspace/build/installed artifact were checked for concurrent changes before installation and final verification.
- Stock HOME and disabled packages are unchanged; our HOME alias remains disabled. All **13 present projection-related properties** match the before-state. No favorites edits, hardware writes, default selection, or reboot were performed. Bounded logs contain no Vibe fatal-process or ANR entry.

This is a focus/layout regression check, not a new performance benchmark. The [0.5.0 motion report](MOTION_TEST_REPORT.md) retains the earlier frame-drop measurements and their conditions; it does not measure this build. Image budgets, worker loading, blur math, hardware helpers, and launcher-selection helpers were not changed.

[Final Home screenshot](test-results/20261106-raised-focus/final-home.png) · [Right edge](test-results/20261106-raised-focus/right-edge.png) · [Apps focus](test-results/20261106-raised-focus/drawer-focus.png) · [Artifact metadata](test-results/20261106-raised-focus/artifact.json).

```sh
python3 launcher/build.py
adb -s 192.168.10.15:5555 install --no-incremental -r launcher/build/vibe-home.apk
python3 launcher/check_home_rows.py --serial 192.168.10.15:5555
```

APK: **57,890 bytes**. Installed SHA-256: `a57394c2c43ff42a33b100e5c507ad4f6928529e95d6b64da29506d5dfc4c958`. Commands, XML, screenshots, logs, previous APK and evidence hashes remain in the ignored evidence directory.
