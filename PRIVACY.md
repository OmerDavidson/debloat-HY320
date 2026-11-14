# Vibe Home privacy disclosure

Vibe Home does not collect or send personal data to its developer or an analytics service. It has no analytics or advertising SDK and does not request the Internet permission. The launcher works offline.

To work on your device, Vibe Home reads information locally, including the installed launchable apps and their names, icons and banners, as well as relevant device settings and input or projection status. It uses this information to display apps and controls. Quick Wake uses the optional `WRITE_SECURE_SETTINGS` permission, granted through ADB, to change the projector’s Power-button behavior when you use the switch. The Power-button setting is stored by Android and can persist after uninstalling Vibe Home.

Vibe Home saves favorites and app order, a pending launcher-selection state, and any projection draft you choose to save in its private app storage. Android app backup is disabled for Vibe Home. The projection draft can also be read through its read-only provider by the app itself or an authorized ADB shell; ordinary third-party apps are denied access. Uninstalling Vibe Home removes its private app data under normal Android behavior.

Vibe Home can open other apps and Android or projector settings. Those apps and services handle data under their own practices; this disclosure covers Vibe Home, not them. Diagnostic scripts run separately on a connected computer may save device information locally and are not part of the launcher app.

This disclosure describes the launcher source and manifest in this repository, version 0.14.9. Modified builds or later versions may behave differently.
