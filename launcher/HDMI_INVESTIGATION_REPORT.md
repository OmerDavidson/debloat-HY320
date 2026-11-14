# HDMI investigation — 2026-11-09

## Findings

Vibe 0.14.4 opens `com.softwinner.awlivetv/.MainActivity` with an `input_source` extra. That activity is the vendor HDMI viewer, not the stock HOME launcher. The examined viewer itself uses Android's `TvView.tune()` and a passthrough URI. Its HDMI input service is supplied by the firmware, so a different viewer cannot bypass a broken HDMI input service or HAL.

Vibe 0.14.5 read the physical-source list from `/product/etc/config.ini` and assumed this unit's `com.softwinner.vis/.HdmiInputService/HWn` IDs. Missing or different configuration, a different input-ID convention, or an absent vendor viewer could make the entry unavailable or its status wrong on other firmware. These are code-level compatibility risks, not a confirmed explanation for any particular user's report. Automatically enumerating every advertised HDMI port is also unreliable on this unit: the HAL advertises three while the vendor configuration identifies only HDMI2.

## Direct Android test

On the identified HY320 (`UI_Veng.projector.20241225.164550`), the configuration mapped the physical HDMI input to HDMI2. A separate temporary diagnostic app used `TvView` and `TvContract.buildChannelUriForPassthroughInput()` with the actual input ID `com.softwinner.vis/.HdmiInputService/HW2`. It requested no Android permissions, and the user confirmed that the connected Mac's picture was visible. Android reported an active input session and `onVideoAvailable`, after a short transition. A screenshot could not be captured while HDMI was active.

The sound observation is inconclusive: audio was initially playing through the Mac's speakers, and the user later clarified there was a mistake during the output test. Video availability callbacks alone do not establish audio, protected-content, format, remote-control, or cross-firmware compatibility. The test app's source, build, and callback logs are retained in the ignored [test evidence](test-results/20261109-hdmi-direct/).

The installed Vibe APK during the initial diagnostic was version 0.14.4 (code 25), SHA-256 `7c30ae04ffaf67d7584b066194dd36610b32b285c80fbede4c9f63c368d6f952`. The HOME resolution, disabled-package list, and checked keystone properties matched the starting snapshot after the initial test. No launcher-selection or projection settings were changed.

Following the direct experiment, the user requested the Android path in Vibe. Version 0.14.5 uses `TvView` for matched configured passthrough inputs and keeps the original viewer as a fallback. The change removes the mandatory dependency on the vendor viewer but still depends on the firmware input service. It does not by itself establish the cause of other users' reports.

## Vibe 0.14.5 verification

On the same projector, the user confirmed the connected Mac's picture was visible in Vibe's new viewer and later confirmed that both picture and sound worked. The [Inputs check](test-results/20261109-hdmi-direct/inputs-check-retry/result.json) opened `com.vibehome.launcher/.HdmiActivity`, recorded an Android TV input session for HDMI2, returned to Vibe with Back, exercised the USB browser and dropdown navigation, and verified the default HOME, disabled-package list, and 13 projection properties were unchanged. Protected content and other firmware revisions were not tested. A transient unavailable screen showed the original-viewer button; its selection was not separately exercised in this build.

The first automated run unexpectedly reached the HDMI activity during its USB step and failed that assertion; the full retry passed. The cause of the first interruption was not established. Bounded logs in the passing run showed no Vibe fatal exception or ANR. The installed 0.14.5 APK hash `badfcdcb7763308afec647d2b9257f996db8d5bf3c606de8773c7160818fd919` matched the build and its recorded source hashes. The temporary probe app was removed.

## Generic discovery in 0.14.6

The Android input list supplies HDMI passthrough candidates and their actual IDs. A confidently resolved vendor source list retains the physical-port filtering on this HY320. If the configuration is missing or a mapping is ambiguous, the menu instead offers the Android-reported HDMI inputs. Duplicate names are distinguished, and explicitly hidden Android inputs are omitted. The original viewer remains a fallback for configured sources when no Android HDMI input is reported. Vibe no longer draws a no-signal or loading screen over direct HDMI; the input service may show its own display, or the result may be black. This is a compatibility improvement, not proof of support across all firmware: Android must expose a usable HDMI input, and some firmware advertises unused ones. No reported user's failure has been reproduced or attributed to a specific cause.

The standalone `HdmiSourcesTest` covers the known physical mapping with extra advertised inputs, no configuration, label matching, ambiguous inputs, a single differently named input, and the configured vendor fallback.

On the identified projector, the [0.14.6 Inputs check](test-results/20261109-generic-hdmi/inputs-final/result.json) passed dropdown navigation, opened the Android HDMI2 session and the USB browser, and returned with Back. The user confirmed picture and sound work and that the extra Vibe screen is gone. The check verified unchanged HOME, disabled packages and 13 projection properties; the additional keystone-property snapshot also matched. Bounded logs contained no Vibe fatal exception or ANR. The installed APK and recorded build have SHA-256 `7b143f68b0f055b4a9af3397cc4db744a9632d0bd2ef047316ff7f3d201a2aa0`, and all recorded source hashes matched. The prior 0.14.5 APK is retained in the ignored test directory. Other firmware and physical USB hotplug were not tested.
