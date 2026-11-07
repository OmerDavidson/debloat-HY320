# Vibe HY320

A lightweight, native Android launcher for our Allwinner H713 / Android 11 HY320, plus reproducible ADB diagnostics. Built for a 1 GB device: bounded icons, native views, no WebView, no downloaded backgrounds, and no continuous background monitoring.

- [Launcher build, installation and integration notes](launcher/README.md)
- [Current raised tile focus](launcher/RAISED_FOCUS_TEST_REPORT.md)
- [Row/tab motion](launcher/MOTION_TEST_REPORT.md)
- [Borderless tiles and blurred row preview](launcher/BORDERLESS_PREVIEW_TEST_REPORT.md)
- [Project goals and agent workflow](AGENTS.md)
- [Earlier home-row visibility fix](launcher/ROW_VISIBILITY_TEST_REPORT.md)
- [Cinematic home and launcher-button checks](launcher/CINEMATIC_TEST_REPORT.md)
- [Earlier home redesign measurements](launcher/REDESIGN_TEST_REPORT.md)
- [Original device diagnostic report](DIAGNOSTIC_REPORT.md)
- [First installed prototype test](launcher/TEST_REPORT.md)
- [Native projector-control investigation](launcher/PROJECTOR_CONTROLS.md)

Build locally with the installed Android SDK and JDK:

```sh
python3 launcher/build.py
```

Stock HOME remains selected after installation. Settings contains buttons for choosing Vibe Home or returning to the original launcher through Android’s default-home UI. The optional HOME entry stays disabled until requested. Projector calibration, hardware services and system-package states are preserved; rooting, flashing, system-package changes and projection-value writes remain outside the approved work.

## Credits and inspiration

- **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** by Gathin23 / contributors, MIT license. Inspiration: a focus-driven app top shelf, a clear favorites dock, device-settings entry points and restrained motion for modest hardware. Inspected commit `fc148d5a9707b30e8f49f489625947421740dc55`.
- **[Tellyscope](https://github.com/man15h/tellyscope)** by man15h / contributors, GPL-3.0-only according to its source headers and notice. Inspiration: tonal surfaces, clear menus and device status, reviewing a proposed change before applying it, and preserving the prior state for undo. Inspected commit `ec9058de10fdc9c06ec4deeac22331ab0a960684`.

Both projects are cloned under `references/` and excluded by the root `.gitignore`. Vibe's menu uses three CleanYourTV vector icons for Wi-Fi, Settings and HDMI. Their MIT notice is included in the APK and in [CLEANYOURTV-LICENSE.txt](launcher/CLEANYOURTV-LICENSE.txt). Installed app icons and TV banners belong to their respective owners and are loaded from apps on the device.

Vendor APK/framework inspection stays local under ignored `research/vendor-settings/`. It is evidence for this specific firmware, not a source of bundled vendor code or assets. No system-signing key or privileged UID is used.
