# Motion and clear focus — 0.5.0

2026-11-06, Asia/Jerusalem. Installed version **0.5.0 (code 10)** on the authorized HY320, firmware `UI_Veng.projector.20241225.164550`, at its unchanged 1920 × 1080 / density-320 configuration.

Home rows now glide one row over 220 ms using a restrained ease-out curve. Tab content glides horizontally over 220 ms, with the clock/date and navigation in consistent positions. A focused app gets a white name capsule and a 140 ms scale lift. Focused controls use a white surface; Settings glyphs lift within their cards. App artwork remains borderless and undimmed, with one sharp row and half of the next row blurred.

Logical selection changes immediately. Further input cancels previous row motion, so MENU/OK do not wait for animations. Focus refreshes cover initial attachment, native selection, focus transfer, and GridView recycling without another layout. Existing Android animator settings are respected. The code adds no dependency, idle animation, image decoding on animation frames, or live blur. Downward motion briefly retains up to four departing card views sharing their existing artwork; they are removed on completion, interruption, or detachment. Full-screen snapshots and persistent hardware layers are not used.

## Verification on the final artifact

- The [row regression](test-results/20261106-motion/rows-final/result.json) passed all **6 distinct rows** down/up, including the incomplete final row. Every selected tile settled at full height in the top row, with its correct focus capsule. Geometry remains **236 px active row / 382 px viewport / 118 px visible preview**. The checker now asserts the selected app's name capsule as well as row bounds.
- The [focused interaction probe](test-results/20261106-motion/probe-result.json) passed rapid down/down/up followed immediately by MENU: the options belonged to the correct app, **13+**. Repeated row reversals and horizontal moves retained exactly one correct capsule. Focusing the top navigation cleared the app capsule. Apps and Settings focus, VLC launch with OK, return navigation, and tab changes passed.
- Screenshots were inspected for sharp artwork, cached blurred previews, borderless/undimmed faces, focus labels, and visible Settings controls. A bounded [device recording](test-results/20261106-motion/motion.mp4) showed the departing row and incoming row moving together without a blank-frame flash. This firmware's screen recorder captures the existing projection warp and black surround; screenshots capture the unwarped UI. No projection value was changed to obtain either.
- [Installed-artifact verification](test-results/20261106-motion/verification.json) confirms the pulled installed APK matches the retained APK and all source/resource hashes. APK: **57,890 bytes**. Both launcher-selection helpers and projection code remain unchanged.
- Stock HOME remains `com.htc.htclauncherhighenglishd08/com.htc.activity.HomeActivity`; our HOME alias remains disabled. All **13 present projection-related properties** and the disabled-package list match the before-state. Favorites were not edited. No Vibe fatal-process or ANR entry was found in the bounded retained logs.

## Performance limits

The motion is finite and interruptible, but **this is not a stable-60-FPS result**. A short unrecorded stress sample after VLC launch/return retained 82 new flag-zero frames: median IntendedVsync-to-FrameCompleted was **59.4 ms**, p95 **77.3 ms**, and 65 exceeded 33.33 ms. See the [sample and conditions](test-results/20261106-motion/frame-sample.json). This is a bounded frame-timing sample, not input latency, a sustained FPS benchmark, or a controlled comparison against 0.4.2. GPU timing buckets from this firmware include invalid-looking values and are not used as GPU-duration measurements.

A temporary per-row hardware-layer trial did not improve the observed frame timings and was removed. The final installed APK is the exact artifact that passed the row and interaction probes before that trial. The restored installation and hashes were checked again, followed by a fresh VLC-focus screenshot. The final fresh-launch PSS snapshot was **49,284 KiB**; it does not measure peak/steady-state memory or establish memory savings.

Reduced-motion behavior follows Android's existing animator flag in code; device animation settings were not modified to test it. This work did not select another default HOME, apply projector changes, reboot, change system packages, or test reboot persistence.

## Evidence and reproduction

[Final Home / VLC focus](test-results/20261106-motion/home-vlc.png) · [Settings focus](test-results/20261106-motion/focus-settings.png) · [Final-row screenshot](test-results/20261106-motion/rows-final/down-5.png) · [Artifact/source hashes](test-results/20261106-motion/artifact.json).

```sh
python3 launcher/build.py
adb -s 192.168.10.15:5555 install --no-incremental -r launcher/build/vibe-home.apk
python3 launcher/check_home_rows.py --serial 192.168.10.15:5555
```

The ignored evidence directory also retains the interaction probe, commands, screenshots, logs, previous APK, trial results, and file hashes. Run device interaction only within the authorized scope. No evidence or artifacts were published.

Installed APK SHA-256: `71a777d6586adf888fbe655c26b56f1fb2d0a95fbecc0c8f139496a8b315e501`.
