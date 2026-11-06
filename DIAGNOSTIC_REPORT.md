# HY320 diagnostic report

2026-11-05, Asia/Jerusalem. **Read-only diagnostic phase completed over `192.168.10.15:5555`. No device modifications were performed during that phase.** A subsequent user-approved launcher installation is recorded below.

The unit matches the reported model and exact firmware. The main optimization opportunity is reducing unnecessary background work and UI animation cost. Logs demonstrate a startup memory-pressure burst and cumulative UI jank; the later settings-screen snapshot does not show sustained RAM exhaustion or a full data partition. No custom firmware or rooting procedure is verified compatible with this exact unit.

## Evidence and reproducibility

| Run | Host UTC | Contents |
|---|---|---|
| `diagnostics/20261105T163356.319416Z_wireless_baseline/` | 16:33:56–16:34:12 | Full hardware/build/memory/process/package/HOME/display/log collection |
| `diagnostics/20261105T163532.812458Z_hardware_followup/` | from 16:35:32 | Panel configuration, USB state, extended logs, repeat memory/CPU/thermal observations |
| `diagnostics/20261105T163643.153574Z_compatibility/` | from 16:36:43 | Display/panel hashes, existing block-node permissions, package-manager help, casting PSS |

Local time is UTC+2. At the baseline, the device reported approximately four minutes of uptime and had Android TV settings foregrounded over vendor settings and the launcher. This is a settings-screen diagnostic snapshot, not a controlled idle or playback benchmark. Collection used the existing unprivileged shell (`uid=2000`). Kernel logs were readable without escalation. No app was launched, stopped, installed or removed by this investigation; no reboot, root command, setting write or partition-content read was performed.

`collect_diagnostics.py` contains fixed read-only queries, exact serial targeting, per-query timeouts and separate stdout/stderr files. Every capture has a `manifest.json` with command arguments, timestamps, exit status, output hashes and the collector hash. The collector was extended between runs; each run records its own version hash. `summarize_diagnostics.py` verifies baseline evidence hashes and derives `summary.json` and `applications.csv` locally, without ADB.

Reproduce from this project directory:

```sh
adb connect 192.168.10.15:5555
python3 collect_diagnostics.py --serial 192.168.10.15:5555 --label baseline
```

Reproduce the analysis of the saved baseline:

```sh
python3 summarize_diagnostics.py diagnostics/20261105T163356.319416Z_wireless_baseline --logs diagnostics/20261105T163532.812458Z_hardware_followup
```

For a smaller repeat capture, the collector supports `--only memory memory_end meminfo cpu_sample cpuinfo graphics_frames logcat_recent thermal activity_services`. Use the same screen, navigation sequence, app, elapsed time since boot and peripherals for before/after comparisons. Graphics statistics are cumulative; compare counter deltas over the same sequence instead of comparing lifetime percentages directly. Diagnostics themselves add some load.

Raw evidence remains local. Properties, package inventories and logs can expose identifiers, titles and URLs; redact before sharing. Initial blocked USB captures are retained for provenance. macOS detected no USB data device. The projector later reported its USB gadget disconnected; its USB host history included an Apple device. This supports a possible USB-role mismatch but does not establish the exact cable/port cause. Wireless ADB resolved access; no sandbox denial occurred.

## Actual platform and build

| Item | Device evidence |
|---|---|
| SoC | **Allwinner H713 platform**, high confidence: `sun50iw12p1`, DT compatible `allwinner,tv303` / `arm,sun50iw12p1`, H713 boot-image fingerprint and firmware identifier |
| CPU | Four online Cortex-A53 cores (ARM implementer `0x41`, part `0xd03`); runtime `armv7l` |
| Kernel / ABI | `5.4.99-00110-g7616a6b12f8c-dirty`, build #2046, Dec 25 2024; **32-bit kernel and userspace**, `armeabi-v7a,armeabi`, no advertised 64-bit ABI |
| GPU | SurfaceFlinger: ARM Mali-G31, OpenGL ES 3.2 |
| CPU policy | Observed 1.32 GHz; hardware range 672 MHz–1.32 GHz. Missing governor/max-policy readings are not inferred. |
| Android | Release 11, API 30; firmware `UI_Veng.projector.20241225.164550` |
| Vendor identifier | `H713-android11-v1.2rc3`, build flavor `h713_tuna_p3-user`, platform `ares`, board `exdroid` |
| System fingerprint | `ADT-3/adt3/adt3:11/RP1A.201005.006/eng.projector.20241225.164550:user/release-keys` |
| Boot-image fingerprint | `Allwinner/h713_tuna_p3/h713-tuna_p3:11/RP1A.201005.006/hotack12251645:user/release-keys` |
| Fingerprint inconsistency | Several partition fingerprints retain `ADT-3/adt3/adt3:10/QTT1.200116.002.B6/6245789:user/release-keys`. These reused identifiers are not reliable hardware identities or a reason to flash ADT-3 firmware. |
| Security observations | Reported patch `2022-02-05`; SELinux **Permissive**; `ro.debuggable=0`, `ro.adb.secure=1`; ADB TCP port 5555 |
| Boot state | Active suffix `_a`, dynamic partitions, `super`, boot/vendor_boot A/B names. `ro.boot.vbmeta.device_state=locked`, `ro.boot.trustchain=false`; actual unlockability/recovery not tested. |
| Existing root evidence | Shell remains uid 2000; no `su` found on PATH or in four common locations. This is not an exhaustive proof of absence. `ro.oem_unlock_supported=1` is a capability claim, not proof that unlocking is safe or works. |
| PCB revision | **Unresolved.** `ro.revision=0` and CPU `Revision=0000` are not a PCB silkscreen identifier. RAM timings, bootloader/ARISC identity and full board equivalence remain unverified. |

CPU core identification and software naming corroborate the H713 platform; no physical chip marking was inspected.

## RAM, storage and temperature

- Boot property reports 1024 MiB DRAM. Linux exposes **938.4 MiB** usable RAM (`MemTotal=960908 KiB`), consistent with reserved regions.
- `MemAvailable` was **325.8 MiB** at the start, **323.0 MiB** at the end, and **324.7 MiB** in the follow-up. `MemFree` alone was about 48 MiB and is not the available-memory measure.
- zram is already enabled: about **844.5 MiB** logical swap capacity, about 176 MiB occupied. The initial zram statistics report about 6.2 MiB physical allocation for about 170.7 MiB original stored data. Do not interpret logical swap as extra physical RAM or add swap on eMMC as an initial fix.
- Memory PSI `some/full avg10` was 0/0 initially, 1.08/0.75 at the end, then 0/0 in the follow-up. There was transient pressure, not continuous exhaustion in these snapshots. Android reports `ro.config.low_ram=true` already.
- `dumpsys meminfo` includes a negative Lost RAM value and overlapping-looking vendor graphics accounting. Use individual PSS and `/proc/meminfo` cautiously; do not sum process RSS or treat the dump's category totals as exact physical accounting.
- eMMC identifies as **T22708**, manufacturer ID `0x70`, **7,650,410,496 bytes (7.125 GiB)**. This is the measured capacity of the nominal 8 GB storage.
- Data filesystem: approximately **4.74 GiB**, **51% used**, **2.25 GiB available**. The read-only system/vendor partitions being 100% full does not mean user storage is full. Storage cleanup is not the first priority.
- eMMC reports lifetime `0x02 0x02`, pre-EOL `0x01`; these are device counters, not a comprehensive health test.
- CPU/GPU temperatures were **70.7/69.7 °C**, then **69.0/67.3 °C**. HAL thermal status was 0 and cooling-device value 0. No thermal throttling was established in this short capture. The stale cached HAL temperatures were lower; current readings were used.
- `top`'s second baseline sample showed 347% idle out of 400%, about **13% aggregate CPU busy** across four cores. The follow-up second sample showed 359% idle, about 10% busy. These brief snapshots do not establish sustained CPU saturation.

## Apps, processes and launcher

**127 installed packages: 113 system-classified, 14 user-classified; 2 disabled packages** (`com.android.mtp`, `com.android.nfc`). Existing disabled states were not changed. Updated system apps remain in the system count. Full package paths, UIDs, versions, installers and user state are in `applications.csv`; raw package details and process/service inventories are retained.

The 14 user-classified packages are:

```text
com.google.android.apps.mediashell
com.google.android.youtube.tv
com.google.android.youtube.tvmusic
com.netflix.mediaclient
com.disneyplus.mea
com.apple.atve.androidtv.appletv
com.crunchyroll.crunchyroid
org.videolan.vlc
org.jellyfin.androidtv
com.valvesoftware.steamlink
gov.nasa
tv.accedo.ott.flow.i24news
com.keshet.mako.VODAndroidTV
com.applicaster.iReshet
```

Installed does not mean running. The baseline process table included 221 processes/tasks, including kernel tasks and diagnostic commands. Selected PSS values:

| Process | PSS MiB | Interpretation |
|---|---:|---|
| `com.htc.htcsettings` | 95.7 | Vendor settings, open behind the current screen |
| `com.htc.htclauncherhighenglishd08` | 81.3 | Default HOME; version 1.7.10 / code 26030611, updated system app |
| `system` | 74.6 | Android framework process; preserve |
| `com.android.tv.settings` | 65.6 | Foreground settings; not an idle-home baseline |
| `com.android.systemui` | 48.3 | Core UI; preserve |
| `com.google.android.gms` | 41.5 | Dependencies for installed apps; preserve initially |
| `com.android.toofifi` + `com.huawei.connection` | 35.3 | Casting package plus its secondary process |
| AirPlay, DLNA, USB cast, line server combined | 38.5 | Four additional casting processes |

Casting-related processes total about **73.8 MiB PSS** in this snapshot. That is a footprint to investigate, not a promised RAM saving: processes, shared pages and replacement workload can change after a disable.

Resolved HOME is `com.htc.htclauncherhighenglishd08/com.htc.activity.HomeActivity`. The only other HOME candidate is `com.android.tv.settings/.system.FallbackHome`, a fallback rather than a verified replacement launcher. The vendor launcher runs a `KeepAliveService`; simply installing a different launcher may leave it resident.

The Android UI display reports 1920×1080, about 60 Hz, density 320. Recorded cumulative gfx counters:

| Process | Frames | Janky | 99th percentile |
|---|---:|---:|---:|
| Vendor launcher | 549 | 174 (31.69%) | 150 ms |
| Vendor settings | 615 | 139 (22.60%) | 57 ms |
| Android TV settings | 455 | 106 (23.30%) | 200 ms |

These support the reported sluggish menus, but combine prior startup/navigation and are not a controlled before/after benchmark.

## Relevant logs and limitations

The extended retained log covers roughly 18:29:58–18:35:38 local device time. It contains **38 explicit lowmemorykiller kills**, occurring between **18:30:18 and 18:31:51**, before our main collection. These include SystemUI, settings, VLC, Jellyfin and YouTube. They are memory-manager kills, not proof of Java crashes.

`com.huawei.connection` was killed **10 times**, nine of them within roughly 2.5 seconds, with repeated binds/restarts of `com.android.toofifi/com.huawei.connection.service.NearFieldManagerService`. Package metadata and service ownership show this is a process of `com.android.toofifi`, not an installed `com.huawei.connection` package. Several vendor processes are also mentioned in lowmemorykiller whitelist messages. This makes the casting suite a concrete candidate for an approved experiment, while leaving the sole cause of startup pressure unresolved. No further explicit kills occur after 18:31:51 in that retained log.

No `FATAL EXCEPTION`, `ANR in`, or `Fatal signal` entries were found in the extended retained log; a bounded log cannot prove these never occur. A VLC `I/O error` concerns browsing `/storage/emulated/0/Android/data/`, not evidence of eMMC hardware failure. No block-I/O failure was established from the captured kernel log. SELinux audit messages include `permissive=1`; they do not mean enforcement blocked every logged operation.

Expected gaps are preserved rather than bypassed:

- `/proc/cmdline`: permission denied. No privilege escalation attempted.
- `cmd role get-role-holders`: unsupported; HOME was resolved through package-manager queries.
- `/vendor/lib64/hw` and `/vendor/firmware`: absent; this is a 32-bit system.
- `dumpsys media.codec`: service unavailable despite a running codec process; service listing and codec XML were captured instead. No playback was active, so codec usage and playback performance remain untested.
- Some compound commands return success despite an earlier failure. The compatibility query's `pm help` returned 255 after printing help; hashes were captured successfully. Inspect stderr as well as manifest exit codes.

## Safe optimization plan — approval required before execution

Priority follows the user's goal: slow menus/app switching; built-in casting unused. **The following commands are proposals only and have not been executed.**

### 1. Establish a repeatable navigation baseline

Return to HOME using the remote, allow startup activity to settle, then repeat the same settings/menu and app-switch sequence. Record elapsed time and collect a smaller diagnostic sample. This avoids attributing the memory of two open settings screens to normal HOME usage. No reboot or automated app launching is needed.

### 2. Trial shorter UI animations

Current global window/transition scales are both `1.0`. Trial `0.5` for those two keys only. `animator_duration_scale` is unset; leave it untouched in the first trial. Expected benefit is faster-feeling transitions, not increased RAM or faster decoding.

Proposed commands, only after approval:

```sh
adb -s 192.168.10.15:5555 shell settings put global window_animation_scale 0.5
adb -s 192.168.10.15:5555 shell settings put global transition_animation_scale 0.5
```

Exact rollback to the observed state:

```sh
adb -s 192.168.10.15:5555 shell settings put global window_animation_scale 1.0
adb -s 192.168.10.15:5555 shell settings put global transition_animation_scale 1.0
```

Re-read values immediately before any approved write, in case the user changes them meanwhile. Validate with the same navigation sequence. Revert if transitions become unpleasant or no useful benefit is observed.

### 3. Trial one unused casting package

After separately approving this step, test **only `com.android.toofifi` first**. It owns the secondary process that repeatedly restarted. Existing user-0 state is `enabled=0` (default). Disabling it will stop its running components and remove those casting functions. It may also cause vendor callers to retry; verify CPU/logs and normal launcher, settings, HDMI, audio, remote and projection controls, then revert immediately if anything regresses. User confirmation that casting is unused is not approval to disable a package.

Proposed command:

```sh
adb -s 192.168.10.15:5555 shell pm disable-user --user 0 com.android.toofifi
```

Exact rollback of the enabled state, supported by this device's package-manager help:

```sh
adb -s 192.168.10.15:5555 shell pm default-state --user 0 com.android.toofifi
```

This restores the package state; services may need to start through normal UI use afterward. No reboot is included. If restart requires one, request separate approval. Do not issue a disable command against `com.huawei.connection`; it is a process name here.

Only if the first trial is beneficial, review `com.mphotool.airplay`, `com.mphotool.dlna`, `com.mphotool.usbcastserver` and `com.toofifi.lineserver` individually. Do not bulk-disable them. AirPlay and DLNA share UID 10031; shared identities are an additional reason to test dependencies carefully.

### 4. Preserve hardware services; defer launcher replacement

Preserve `com.htc.closedialog`: its observed services include **TempCheckService and ManualFocusService**. Preserve vendor settings, `com.softwinner.vis`, TV input/HALs, SystemUI, Bluetooth, Wi-Fi, remote/input, package management and updates in the initial trial. Names such as `android.rockchip.update.service` do not override the hardware evidence identifying Allwinner.

A launcher replacement is a later option after comparing a remote-friendly candidate compatible with 32-bit Android 11 and verifying access to projector controls. No suitable replacement is currently installed; the vendor keep-alive/dependencies need review before changing HOME or disabling its launcher.

### 5. Evaluate and retain only measured improvements

Keep each trial separate. Compare menu/app-switch behavior, frame-counter deltas, available RAM/PSS, PSI, CPU and new memory-kill/restart messages. Confirm projection, HDMI, audio and remote functions. Stop if a dependency fails. Storage cleanup, task killers, forced background-process limits, LMK/zram retuning, resolution overrides, clock/fan changes and rooting are not justified by this initial evidence.

Approval ledger at diagnostic-phase completion: only network ADB diagnostics were authorized; no device modifications had been performed. Subsequently, the user explicitly approved installing/opening our launcher prototype for testing, while preserving stock HOME and system packages. See the launcher trial below.

## Firmware and rooting compatibility investigation

Primary sources checked on 2026-11-05. Text-only source snapshots and hashes are in `research/sources.json`; no third-party tool, APK, installer or firmware was executed. The device is not a proven match for any replacement image.

### Hardware comparison

The actual `/Reserve0/panel_config.ini` reports project ID 48 (`0x30`), 1920×1080, dual-port output, 2200×1125 totals, 164 MHz clock and PWM channel 5. The Wi-Fi kernel probe identifies **AIC8800D80**, rev `0x7`, subrevision `0x82`.

The public [HY320 profile](https://github.com/well0nez/allwinner-h713-linux/blob/main/installer/h713/profiles/hy320.py) agrees on H713, panel parameters and display firmware. Our `display.bin` hash exactly matches its listed revision:

```text
22a7df113fce3fa182926268de8c7551a107f0c3bc2932f0940bd58b8f424835
```

However, this unit has **14,942,208 sectors**, versus **15,269,888** in that profile; our `super` is **2,097,152 KiB (2 GiB)**, versus **3 GiB** in the profile. The profile's stock system is October 2024, while ours is December 2024. The profile itself says shared display/fingerprint features are weak identifiers and relies on boot-package hashes and version strings. Those strong identifiers and PCB revision remain unknown. **A display hash match is not a board/flash-layout match.**

### Candidate decisions

| Candidate | Evidence and decision |
|---|---|
| [Alliwava HY320 downloads](https://www.alliwava.com/en-ca/pages/hy320) | Vendor offers HT_UI and ZX_UI branches, including November 2024 images. No exact PCB/radio/layout compatibility evidence for our December build was found. Do not select an image from the product name alone. |
| [Magisk official installation guidance](https://topjohnwu.github.io/Magisk/install.html) | Legitimate rooting framework exists; requires matching ramdisk-bearing stock image and bootloader prerequisites. Our locked-state property and lack of verified stock backup/recovery prevent a recommendation to root now. Never use another owner's prepatched image. |
| [well0nez/magcubic-root](https://github.com/well0nez/magcubic-root) | Documents tests on HY310/L018, not this HY320 revision. Its repacker and instructions are research leads, not verified tools for this unit. Do not run its debloat list or third-party patching flow. |
| [YuujiLab HY300 H713 research](https://github.com/YuujiLab/HY300-H713-Research) | Adjacent-platform root/firmware research; different model/revision. Does not establish support for this device. |
| [Experimental H713 Linux, HY320 board notes](https://github.com/well0nez/allwinner-h713-linux/blob/main/boards/hy320/README.md) | Board-specific notes still mark HY320 partial/test-only. They describe a Nov 1 video-plane fix, while the overview still mentions a dark video plane: documentation is inconsistent. Not a validated safe upgrade for this unit. |

The Linux project's overview also labels its implementation experimental and AI-generated without complete human review. Do not interpret a test boot or claimed recovery route on another board as guaranteed recovery here.

### Gate before recommending a flash or root procedure

1. Establish PCB revision, exact bootloader/ARISC/DT identity and DRAM configuration, not just retail name or CPU family.
2. Obtain trusted exact-build stock firmware and a separately approved, verified backup preserving calibration and unique data. This diagnostic capture is **not** a firmware backup. Full eMMC node permissions appear world-readable, but no raw dump or root attempt was made.
3. Validate actual partition offsets/sizes, boot-image layout, slots, AVB and a recovery method for this board. Slot names alone do not establish a usable fallback slot. Do not reboot to fastboot/FEL or unlock during diagnosis.
4. Inspect candidate contents offline, confirm source hashes/signatures and reproducible same-revision evidence. Do not cross-flash HY320 Pro/H726 or use ADT-3 images because of reused fingerprints.
5. Present exact operation, recoverability, rollback limitations and data-loss risk for explicit approval.

Recommendation: retain current firmware and test the reversible UI/casting proposals first. Compatible rooting or replacement firmware remains **unverified**, rather than proven absent.

## Subsequent approved launcher trial

The user approved installation and opening of Vibe Home as a normal app. Version 0.1.3 is installed; the original launcher resolves as HOME. No system package was disabled/uninstalled, no projection values were adjusted, and no root, reboot or partition operation occurred. Initial HOME registration introduced a chooser; the test manifest now omits HOME registration, restoring stock resolution without a default-setting command. Runtime tests, screenshots, metrics and a stock-launcher exception observed during installation are documented in [the launcher test report](launcher/TEST_REPORT.md).

## Reference-inspired native editor update

The user requested inspiration from CleanYourTV/Tellyscope and native projector controls. Version 0.2.0 adds a focus-driven home shelf and an independently implemented native projection preview/draft editor. Read-only HIDL mode access works from the ordinary app. No hardware setting was applied. See [the current test report](launcher/NATIVE_SETTINGS_TEST_REPORT.md) and [the native-control boundary](launcher/PROJECTOR_CONTROLS.md). Root README credits both gitignored reference clones.
