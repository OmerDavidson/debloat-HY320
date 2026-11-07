# Hybrid settings trial — 0.8.0

2026-11-06, Asia/Jerusalem. The latest user direction is to try native controls plus vendor settings redirects and assess performance/user experience. This supersedes the earlier requirement that every settings editor be native.

The later [matched default-launcher comparison](DEFAULT_LAUNCHER_COMPARISON_REPORT.md) compares actual Vibe and HTCLauncherEN default-HOME conditions. This report's performance table compares two Vibe versions and should not be used as a stock-launcher baseline.

## Implementation

Vibe keeps the six-category sidebar and native volume control. Protected or unvalidated operations open their installed settings pages directly, without opening the original Home launcher or changing default HOME. Picture/keystone, brightness/color, Wi-Fi, Bluetooth, hotspot, language/input, date/time, maintenance and advanced Android settings are reachable from Vibe. Private firmware editors (screen saver and developer options) are accessed through their exported parent menu. Update links open the options menu, not an updater operation. Factory reset has a Vibe entry guard with Cancel focused before the vendor confirmation can be opened.

Returning preserves the category, focused action and scroll position. If touch scrolling cleared the action focus, that stale selection is discarded so reopening Settings does not focus a hidden row. State is also saved through activity recreation. The native projection editor remains a clearly labeled draft/preview; hardware helpers and HOME-selection logic are unchanged.

The default Settings landing page no longer reads firmware properties, panel mode, picture adjustments, Wi-Fi scan results or Bluetooth state. System information is loaded only when System is selected; access diagnostics only when explicitly requested. There is a volume receiver only while Sound is visible. There is no additional service, polling, boot receiver, root, shell bridge or vendor process kill. Prior permission declarations/grant state are retained for update compatibility; no permission is granted or revoked by this task.

## Device and artifact

Identity was refreshed before installation: HY320 / sun50iw12p1 / `UI_Veng.projector.20241225.164550`, 1920 × 1080, density 320. Vibe `.HomeEntry` was already default. The previous installed 0.7.1/code 14 APK was retained and matched its local build hash.

Evidence: [test-results/20261106-hybrid/](test-results/20261106-hybrid/). Final candidate is **0.8.0/code 15**, **74,274 bytes**, SHA-256 `67e3ad67e5808a7e2a89a6cd680dca8cf6fd3ba23600b112500b41231c631614`. Installation/source verification is recorded separately after final checks. Corrective trial artifacts are retained separately and identified by their actual hashes.

## Performance method

Compared 0.7.1 with 0.8.0 on the same unit. Each run force-stopped **only Vibe**, opened the already-selected Vibe HOME alias, performed one Home/Settings warm-up, then sampled five warm Home→Settings transitions. No app data, logs or frame counters were cleared; no forced GC, vendor service/package stop, display change or reboot was used.

Per page, three approximately eight-second idle windows captured Vibe user+system CPU tick deltas from `/proc/PID/stat` at the device's reported 100 Hz. PSS snapshots followed those windows. Incremental `dumpsys gfxinfo ... framestats` rows (Flags=0) were selected by timestamps absent from the pre-transition snapshot. Reported frame time is FrameCompleted minus IntendedVsync, not end-to-end button-to-settled-screen latency. Input timestamps were absent for the selected rows, so input latency is not reported.

The baseline, first hybrid trial and final corrective build have separate outputs. Final comparisons use the final APK hash. These are sequential short samples, not randomized sustained benchmarks. ART/JIT, image caches, background activity, clock updates, memory pressure and shared-page accounting can differ. The final hybrid run also occurred after vendor-page exploration, so vendor cache state was warmer. Sample frame counts differ because the animations produced different numbers of rendered frames. These conditions limit causal and statistical claims.

| Metric | Native-only 0.7.1 | Hybrid 0.8.0 final |
|---|---|---|
| Settings transition frames | 55 | 87 |
| Median transition frame | 43.8 ms | 18.1 ms |
| 95th percentile transition frame | 96.6 ms | 44.2 ms |
| Home median Vibe PSS | 40.7 MiB | 42.9 MiB |
| Settings median Vibe PSS | 43.8 MiB | 46.1 MiB |
| Median idle Vibe CPU (one-core basis) | 0.0% in both pages | 0.0% in both pages |

[Baseline results](test-results/20261106-hybrid/baseline/result.json), [final hybrid results](test-results/20261106-hybrid/hybrid-final/result.json). The first hybrid trial also had lower sampled frame times (median 25.1 ms / p95 58.0 ms) but is not the final artifact.

The sampled Settings transitions rendered faster with hybrid. Vibe PSS was slightly higher in the final sample, so **no RAM saving is demonstrated**. Idle CPU was below the short sample's useful discrimination in both builds. No sustained performance improvement or 60-fps guarantee is claimed.

## User-experience checks and limits

The [final hybrid UI check](test-results/20261106-hybrid/ui-final/result.json) passed all **12 vendor redirect rows plus Android advanced settings**. Back returned to the correct Vibe control in each case. The native volume UI, preview return, reset-guard cancellation, remote focus movement and touch-scroll/reopen focus bounds also passed. Tracked HOME, disabled packages, projection/hotspot/locale properties and selected clock/network/audio/input settings were unchanged. The earlier trial's automation swipe overshot a partially visible developer row; a slower gesture and a Vibe stale-focus correction were verified in the final run.

Android's own Displayed logs for the 12 vendor entries reported **148–239 ms**, median **175 ms**. These were already-running vendor-process launches; they are first-draw timings, not cold-start/network-scan completion times.

While Wi-Fi was open, Vibe PSS was **52.4 MiB** and the vendor settings process **78.5 MiB** in one snapshot. With Android advanced settings open after the route tour, Vibe/vendor/Android-TV-settings PSS snapshots were **47.9 / 84.3 / 35.9 MiB**. These are partial-process memory observations, not whole-device RAM measurements. The vendor process was already present in the baseline (~46 MiB PSS), so its entire footprint cannot be counted as a newly introduced cost. Vendor cache state was substantially warmer during later measurements.


Checks open settings pages and return without adjusting their controls. They inspect actual projector UI, resumed activities, focus and screenshots. Vendor Wi-Fi/Bluetooth pages may perform their own discovery. DateTimeActivity reasserts its existing 12/24-hour value on entry; tests require that value to be present and verify it remains unchanged. A native guard cancellation tests the reset entry; no vendor reset confirmation is accepted, update launched, permission granted, calibration changed or default HOME selected.

The Screen saver link intentionally lands on Projection settings rather than a private editor; the user then selects Screen Saver. Developer/USB similarly lands on Other Setting. Vendor pages retain their own visual style and internal navigation. These extra steps and the temporary memory cost of external settings are tradeoffs, not native UI parity.

Actual changes to Wi-Fi, Bluetooth, sound, locale, time, projector geometry, reset and update are not tested here. Automatic full entry into the screen-saver editor is avoided because that vendor editor initializes an Android timeout on entry. No system helper/OEM/root implementation was benchmarked, so this trial cannot establish a globally optimal architecture.

## Post-return vendor cost

After returning Home from the route tour, three approximately eight-second windows sampled Vibe, vendor settings and Android TV Settings. Vibe/Android TV CPU medians were 0.0% of one core; the vendor process was about 3.0% of one core. A six-second per-thread follow-up located the sampled work on its main thread. No active vendor service was listed in the activity-service dump, but this does not identify the cause of its work.

A separate older vendor task still contained About/Other/Projection/Home menus from earlier inspection. These menus were closed using ordinary Back navigation (no vendor process or service was stopped), then the sample was repeated. Vendor CPU remained around 3.0%; therefore the older task does not explain away the observation.

| Process | PSS after returning Home | PSS after closing old menus | CPU median after cleanup |
|---|---|---|---|
| Vibe | 47.6 MiB | 47.0 MiB | 0.0% of one core |
| Vendor settings | 70.4 MiB | 75.8 MiB | 3.0% of one core |
| Android TV Settings | 32.7 MiB | 32.4 MiB | 0.0% of one core |

[Post-return samples](test-results/20261106-hybrid/post-return/result.json), [post-cleanup samples](test-results/20261106-hybrid/post-cleanup/result.json). Cached memory can remain after Back. Vendor CPU was not sampled in the native-only baseline, so this data cannot establish that hybrid introduced the CPU activity. It also cannot support a claim that total device idle CPU is zero. No vendor code was patched and no service/process was killed to improve the results.

## Assessment

Hybrid removes the native-only permission dead ends and exposes the installed complete controls while retaining Vibe as HOME. Default Settings entry does less work, and its sampled rendering improved. It adds no helper process. However, neither total-memory savings nor globally best performance is established: Vibe PSS was slightly higher, vendor/Android settings retain memory after use, and vendor background CPU needs separate investigation. An unbuilt privileged native backend could have a different tradeoff and was not benchmarked.

Keep this as a practical hybrid trial for functional coverage and quick Vibe navigation. Treat vendor style differences, parent-menu steps and retained vendor resources as explicit UX/performance costs. Further work should measure vendor idle activity against a properly captured baseline before claiming a total-system improvement.

## Final verification and delivery

[Final verification](test-results/20261106-hybrid/final-verification.json) confirms the installed APK/source hash match, 0.8.0/code 15, unchanged HOME/display/disabled packages/permission grants, unchanged tracked projection and selected settings, and no Vibe fatal-exception/ANR marker in the bounded process log.

The [Home-row regression](test-results/20261106-hybrid/home-rows/result.json) passed all five distinct rows down/up, including the final row, app options and All apps. Geometry remains 382 px viewport / 236 px active row / 118 px next-row preview. That general check used the ordinary launcher entry. Vibe was subsequently restarted (app-only, no data/log clear) and reopened through the already-default HOME alias, leaving one canonical Home entry with the new Settings page visible. Future general checks choose that alias automatically when Vibe is already default.

Screenshots: [final hybrid Settings](test-results/20261106-hybrid/final-settings.png), [native Sound](test-results/20261106-hybrid/hybrid-final/sound.png), [vendor Wi-Fi](test-results/20261106-hybrid/ui-final/vendor-WIFIActivity.png), [vendor projection menu](test-results/20261106-hybrid/ui-final/vendor-ProjectionActivity.png). Raw screenshots/logs/APKs remain ignored. No commit, push, publication, hardware Apply, firmware operation or default-HOME grant was performed.
