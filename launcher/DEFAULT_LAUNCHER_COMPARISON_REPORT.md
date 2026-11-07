# Matched default-launcher comparison — Vibe 0.8.0 vs HTCLauncherEN

2026-11-06, actual HY320 projector. **Vibe has a much smaller foreground process and substantially lower idle Home CPU, but this test does not demonstrate a whole-system RAM gain after both launchers have been used.** The original launcher keeps a foreground KeepAliveService and stays resident while Vibe is default. Vibe was restored as default at the end.

Warm Home medians: active-launcher PSS **89.6 → 40.7 MiB**, a **49.0 MiB / 54.6% smaller process**. This is a process-footprint comparison, not RAM freed for other apps. Whole-device CPU busy **47.1% → 8.8%** (all-core normalized), about **81.2% less sampled idle CPU activity**. No playback, thermal, power, app-start or long-term stability benefit is established by this short test.

## Method and authorization

The user explicitly approved four temporary Home selections and restoring Vibe. Order was **A1 Vibe → B1 legacy → A2 Vibe → B2 legacy → A3 Vibe**. Each change used the actual Android `android.settings.HOME_SETTINGS` chooser and its visible label, then verified the resolved HOME. No shell preferred-activity override was used. These are actual default-launcher conditions, not simply opening an app while the other remains default.

Identity and package hashes were checked before every block: HY320 / sun50iw12p1 / firmware `UI_Veng.projector.20241225.164550`, Android 11, 1920 × 1080, density 320. Kernel MemTotal was 938.4 MiB. No build/install, reboot, package/service stop, alias change, data/log/frame-counter clear, forced GC, root, permission grant, calibration/display adjustment or settings-value change was performed. The same running Vibe and legacy processes persisted through the warm crossover blocks.

Each block pressed Home and settled 15 seconds, collected three approximately eight-second CPU windows followed by PSS snapshots, performed 16 focus-only Right/Left presses, opened the same vendor Wi-Fi activity and settled eight seconds, collected three windows, then pressed Back/Home and settled 12 seconds before three final windows. Back was verified to return to the selected launcher. Wi-Fi controls were not changed; the vendor page can perform its normal discovery. UI screenshots and foreground/default assertions accompanied the phases.

CPU comes from `/proc/stat` and process `/proc/PID/stat` deltas at the device's reported 100 Hz. System CPU is normalized across all cores; process CPU is on a one-core basis (100% = one fully occupied core). A reported 0.0% means no measured tick delta in these short windows, not a guarantee of no future work. Small ADB sampling offsets and diagnostic overhead remain.

PSS comes from `dumpsys meminfo` and apportions shared resident pages; it excludes swapped-out PSS. System headroom is kernel `MemAvailable`, not just MemFree or a sum of app PSS. Both launchers plus vendor settings and Android TV Settings are tracked, including the nondefault launcher. The tracked PSS sum is a partial-process total, not whole-device memory use. Kernel swap use and whole-device memory/OOM dumps are also retained.

A1 began with the original launcher absent. It is reported separately and excluded from the warm comparison: treating its initial cache/process state as interchangeable with the later conditions would exaggerate a RAM gain. Warm tables pool six windows per condition and phase: A2/A3 versus B1/B2. These are repeated windows in only two blocks per condition, not six independent experiments or a randomized trial. Block medians and a B2 comparison bracketed by A2/A3 expose order drift.

Evidence: [raw directory](test-results/20261106-default-comparison/), [approval](test-results/20261106-default-comparison/approval.json), [plan](test-results/20261106-default-comparison/PLAN.md), [45 sample rows](test-results/20261106-default-comparison/samples.csv), [calculated summaries](test-results/20261106-default-comparison/summary.json).

## Warm RAM results

Values are medians in MiB; parentheses show the minimum–maximum of six windows.

| Phase | Active Vibe PSS | Active legacy PSS | Vibe process reduction |
|---|---|---|---|
| Home idle | 40.7 (38.3–48.7) | 89.6 (86.4–92.9) | 49.0 MiB / 54.6% |
| Vendor Wi-Fi open | 40.9 (38.5–45.9) | 97.8 (95.6–99.8) | 56.9 MiB / 58.2% |
| Home after Wi-Fi | 40.4 (38.6–44.5) | 100.5 (97.3–103.4) | 60.0 MiB / 59.7% |

When Wi-Fi is open the selected launcher is covered; that row describes its retained process, not an active foreground launcher.

| Phase | System available, Vibe default | System available, legacy default | Vibe minus legacy |
|---|---|---|---|
| Home idle | 360.7 (347.9–373.8) | 369.3 (359.7–379.6) | -8.6 MiB |
| Vendor Wi-Fi open | 345.2 (339.6–348.0) | 353.6 (344.7–356.8) | -8.4 MiB |
| Home after Wi-Fi | 358.8 (357.8–362.9) | 372.2 (369.2–376.4) | -13.4 MiB |

**No consistent increase in available system RAM was demonstrated.** Smaller Vibe PSS cannot be counted as freed memory while the old launcher remains loaded. Differences also include cache, swap, shared-page accounting and untracked processes; the available-memory delta is not attributed solely to the launcher choice.

| Phase | Four tracked processes, Vibe default | Four tracked processes, legacy default | Swap used, Vibe default | Swap used, legacy default |
|---|---|---|---|---|
| Home idle | 182.0 (175.7–191.7) | 172.8 (160.8–181.4) | 220.2 (216.2–225.0) | 224.1 (218.5–226.5) |
| Vendor Wi-Fi open | 182.6 (178.3–190.0) | 184.9 (177.0–191.2) | 220.0 (216.0–224.0) | 220.0 (216.2–226.2) |
| Home after Wi-Fi | 180.3 (176.0–184.0) | 185.3 (176.5–193.8) | 220.0 (216.0–224.0) | 219.4 (216.0–222.8) |

In both warm Vibe blocks, the stock launcher remained in Android’s **Perceptible** OOM group with its foreground `com.htc.service.KeepAliveService` (`isForeground=true`), rather than disappearing as the default changed. Its low CPU while covered does not remove its resident memory. No service was stopped to manufacture a memory saving. See A2/A3 `services-stock.txt` and `home-idle-whole-memory.txt`.

## Warm CPU results

Medians; process percentages use one core, system percentages use all cores.

| Phase | Vibe process CPU, Vibe default | Legacy process CPU, legacy default | System CPU, Vibe default | System CPU, legacy default |
|---|---|---|---|---|
| Home idle | 0.0% | 81.4% | 8.8% | 47.1% |
| Vendor Wi-Fi open | 0.0% | 0.1% | 8.8% | 9.2% |
| Home after Wi-Fi | 0.0% | 78.9% | 8.7% | 47.1% |

The high legacy Home CPU repeated in both blocks, returned after Back from Wi-Fi, and dropped while another screen covered Home or Vibe was selected. That supports a real idle-Home CPU advantage for Vibe in this session. It does not identify the responsible legacy code or prove an improvement while video playback covers either launcher. Vendor settings still used roughly 3% of one core in both conditions; Vibe does not eliminate that vendor cost.

## Block medians and order drift

| Block | Phase | Vibe PSS MiB | Legacy PSS MiB | Available MiB | Tracked PSS MiB | System CPU % |
|---|---|---|---|---|---|
| A1-vibe | Home idle | 39.3 | 0.0 | 404.7 | 107.8 | 8.7 |
| A1-vibe | Vendor Wi-Fi open | 44.0 | 0.0 | 375.4 | 124.0 | 8.9 |
| A1-vibe | Home after Wi-Fi | 43.3 | 0.0 | 384.9 | 121.0 | 8.7 |
| B1-legacy | Home idle | 28.1 | 86.8 | 378.1 | 161.3 | 47.7 |
| B1-legacy | Vendor Wi-Fi open | 28.0 | 96.2 | 356.4 | 177.7 | 9.2 |
| B1-legacy | Home after Wi-Fi | 28.0 | 97.5 | 375.3 | 176.7 | 47.0 |
| A2-vibe | Home idle | 38.3 | 86.8 | 372.7 | 175.9 | 8.9 |
| A2-vibe | Vendor Wi-Fi open | 39.3 | 86.7 | 347.3 | 179.1 | 8.7 |
| A2-vibe | Home after Wi-Fi | 39.3 | 86.7 | 358.5 | 176.8 | 8.7 |
| B2-legacy | Home idle | 37.8 | 90.0 | 359.7 | 178.6 | 47.0 |
| B2-legacy | Vendor Wi-Fi open | 38.2 | 99.0 | 349.1 | 191.0 | 9.6 |
| B2-legacy | Home after Wi-Fi | 38.2 | 100.5 | 369.5 | 190.0 | 47.1 |
| A3-vibe | Home idle | 40.7 | 90.0 | 353.7 | 182.0 | 8.8 |
| A3-vibe | Vendor Wi-Fi open | 41.4 | 88.6 | 342.7 | 183.6 | 8.9 |
| A3-vibe | Home after Wi-Fi | 40.5 | 88.5 | 359.1 | 180.3 | 8.7 |

A1 had more available memory before the old launcher was ever opened in this comparison, but it also had different background/settings process and swap state. It is not a matched cold-boot baseline, and no reboot persistence or stock auto-start behavior was tested. Do not promise its headroom as a sustained gain.

For a time-bracketed check, average the A2/A3 phase medians and subtract B2. Positive available-memory difference would favor Vibe:

| Phase | Available MiB difference | Tracked PSS MiB difference | System CPU difference, percentage points |
|---|---|---|---|
| Home idle | 3.5 | 0.3 | -38.2 |
| Vendor Wi-Fi open | -4.1 | -9.7 | -0.8 |
| Home after Wi-Fi | -10.6 | -11.5 | -38.4 |

## Navigation and settings experience

The same direct Wi-Fi screen opened successfully in all five blocks; Back returned to the correct selected launcher in each. The full hybrid redirect/focus checks are in the [hybrid trial report](HYBRID_SETTINGS_TEST_REPORT.md). The matched run establishes continued access and return navigation, not the speed of changing protected settings.

Incremental `gfxinfo` frame durations are retained below for inspection. They measure FrameCompleted minus IntendedVsync, not button-to-screen latency. Legacy’s history saturated the 120-row ring and covered only about 1.8 seconds, whereas Vibe’s event-driven frames covered about 21.7 seconds of navigation. A3 also reached 120 retained rows. Different layouts, rendering cadence and truncated histories prevent treating these numbers as matched input-latency or smoothness scores. No navigation-speed winner is claimed.

| Block | New frame rows retained | Median frame ms | p95 frame ms |
|---|---|---|---|
| A1-vibe | 117 | 50.7 | 74.1 |
| B1-legacy | 120 | 30.0 | 42.3 |
| A2-vibe | 118 | 47.9 | 74.0 |
| B2-legacy | 120 | 32.9 | 47.4 |
| A3-vibe | 120 | 50.7 | 74.5 |

## Artifacts, recovery and final checks

| Package | Version | Installed APK SHA-256 |
|---|---|---|
| Vibe | 0.8.0 / code 15 | `67e3ad67e5808a7e2a89a6cd680dca8cf6fd3ba23600b112500b41231c631614` |
| HTCLauncherEN | 1.7.10 / code 26030611 | `4c02831c99ddee49d5dbba8c0f48d838e51618e51fe9cc13cf639e7a2092608c` |
| Vendor settings | Existing installed system app | `bc85e95d0a640d19cc1d6017ed9e2c19684ece434dc7002b22408d61f81c9f62` |

[Final read-only verification](test-results/20261106-default-comparison/verification.json) confirms Vibe `.HomeEntry` is restored as default and foreground, its alias remains enabled, all three APK hashes and permission-grant states are unchanged, disabled packages and display parameters are unchanged, and tracked projection/calibration, selected audio/network/time/input settings remain unchanged. No fatal-exception/ANR marker was found in the bounded logs; this is not an exhaustive crash history. Current app sources and local APK still match the tested build artifact. No application rebuild or installation was needed for this diagnostic task.

The four selections verified the OS Home-settings chooser, not the entire built-in Vibe HomeSwitcher button lifecycle. Reboot persistence remains untested. Legacy and manufacturer settings remain installed/enabled and accessible. The stock foreground service is intentionally left intact. Raw APKs, screenshots and device logs remain ignored local evidence; no commit, push or publication was performed.

Keep Vibe as the selected Home for its confirmed lower idle CPU and smaller launcher footprint. Whole-system RAM recovery would require understanding the manufacturer keep-alive lifecycle and validating a supported way for its launcher process to exit while retaining protected settings, recovery and calibration. This comparison does not authorize stopping that service or disabling a system package.
