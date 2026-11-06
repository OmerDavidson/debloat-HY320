# Native settings and reference-inspired launcher update

2026-11-05, Asia/Jerusalem. Installed **0.2.0 (code 5)** as an ordinary app under the user's existing approval to install and test this launcher. The original launcher remains HOME. The live projection has not been changed.

## Delivered

- Focus-driven app name and a subtle app-color tint, inspired by CleanYourTV's top shelf and dock. No animated wallpaper, blur, new UI framework, fonts or downloaded artwork.
- Long-press app options: open, pin/unpin, app information.
- Native Projector activity with current mode/status, picture outline, size preview and four-corner editor.
- Review and save draft flow, inspired by Tellyscope's explicit change-review/history approach.
- Read-only draft retrieval and a separately gated one-shot hardware probe/test tool. No real Apply button in the app yet.
- Both reference clones are excluded by root `.gitignore`; root and launcher READMEs credit them. Commit/license evidence is in `research/reference-review/sources.json`. No repository code/assets were imported or device-changing scripts executed.

## Checks passed

1. Native APK/DEX build and signatures; final artifact approximately **37 KB**. All recorded Java/resource/manifest hashes match the build.
2. No requested Android permissions, HOME registration, services or receivers. One read-only provider checks app/shell caller identity and rejects mutating operations.
3. Geometry tests: 100% identity, immutable captured baseline, preservation of an asymmetric trapezoid during scaling, range checks and rejection of enlargement beyond the panel. Host integer/bounds and exact approval-token checks also passed.
4. Focused app title follows remote navigation. App-options menu contains the intended actions; no app was disabled or uninstalled by this test.
5. Projection opens **our own activity**, not the vendor menu. Size, keystone and mode tabs display correctly. Preview clipping found during visual inspection was corrected.
6. D-pad adjusts the size preview to **99%**. Review/save produces a private draft containing corners `[170,169,170,170,170,170,170,169]`. The actual picture is untouched. Draft retrieval through authorized ADB and host plan generation were verified.
7. The ordinary app's read-only HIDL mode getter returns **Front · table**, matching the shell probe's mode 0. No mode setter was called.
8. Back returns from native settings to Vibe Home. HOME opens the stock launcher; package-manager resolution confirms the original component.
9. **23 projection-related properties** remain unchanged compared with the pre-update snapshot. Existing disabled system packages remain `com.android.mtp` and `com.android.nfc`.
10. No Vibe Home fatal exception was found in the retained update/test logs. Logs/screenshots are bounded observations, not a prolonged stability test.

Keyboard tests account for Android's touch-mode behavior: a pointer tap on a slider does not necessarily transfer keyboard focus. D-pad navigation was used to focus it before changing the preview. The app-options menu was verified with a held touch gesture; the software-generated long-press key instead launched an app, so physical remote long-hold behavior still needs user confirmation. No projector setting was changed by those inputs.

## Evidence and remaining boundary

Device UI hierarchies, commands, screenshots, installed artifact metadata, logs and property captures are under:

`test-results/20261105T171125Z-native-settings/`

Key screenshots: `final-focused-home.png`, `final-projector-screen.png`, `final-keystone-screen.png`.

`research/reference-review/native-control-probe.json` records the read-only service result. `preview-plan.json` is the tiny **167 → 168** bottom-left inset plan, lasting 10 seconds, with automatic restore of the captured transform. `saved-draft-plan.json` demonstrates planning from the app's saved draft; that larger draft is **not** the proposed first hardware test. Neither plan has been applied.

No preview command with an approval token, raw SurfaceFlinger setter, mode setter, persistent-property setter, root command, reboot, flash, default-HOME write or system-package modification was executed. Installing corrective app builds, navigating screens and saving a draft were the only writes performed by this update, apart from the normal Android installation/runtime bookkeeping.

Actual hardware writes require the user's next explicit approval. The [control investigation](PROJECTOR_CONTROLS.md) distinguishes the read-only getter, transient protocol test, ordinary-app write permissions, physical verification, and future persistent Apply/Undo. No measured performance improvement is claimed from APK size or these UI checks.

Final runtime PSS was **37,272 KiB (36.4 MiB)** with the native Projector activity foregrounded. This is one snapshot, not a matched comparison or guaranteed sustained memory footprint.
