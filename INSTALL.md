# Install Vibe Home

You need a USB drive, a computer with internet access, and your projector's remote. No computer commands or ADB are needed for installation.

Vibe has been tested on one Allwinner H713 HY320 running Android 11, firmware `UI_Veng.projector.20241225.164550`. Other HY320 versions may work differently. The USB installation steps below have not yet been verified on that projector.

## 1. Get the APK

1. Open the project's [Releases page](https://github.com/oshriagronov/debloat-HY320/releases).
2. Open a release and download **vibe-home-&lt;version&gt;.apk** from its **Assets** section (for example, `vibe-home-0.14.4.apk`). Do not download the source-code ZIP.
3. Copy the APK to your USB drive, then safely eject the drive from your computer.

An APK only appears on the Releases page after a maintainer publishes one. If none is available, the [build instructions](README.md#install-the-launcher) are available to developers. Builds for individual pushes are also available as [Actions artifacts](https://github.com/oshriagronov/debloat-HY320/actions) when the workflow has run; downloading these may require a GitHub account. Open a successful **Build APK** run, download its artifact ZIP, and extract the versioned APK from `launcher/build/`. These are development builds, not necessarily tested releases. Read `signing-info.txt` in the ZIP before installing: a temporary-key build cannot update another installation.

## 2. Install on the projector

1. Plug the USB drive into the projector.
2. Use the projector's file manager or USB media browser to find and open the **vibe-home-&lt;version&gt;.apk** file.
3. If Android asks you to allow this file manager to install unknown apps, follow the on-screen settings and allow it. Return to the APK and open it again if needed.
4. Select **Install**, then **Open**.

Menu names and USB file support can vary by firmware. If the USB browser cannot open APK files, use a file manager that can, or use the developer ADB instructions. Only install an APK you trust. You can turn off the file manager's install permission afterward.

## 3. Choose Vibe as Home

Installing and opening Vibe does not change your default launcher.

1. In Vibe, open **Settings → About → Use Vibe Home**.
2. Choose **Vibe Home** in Android's prompt and confirm **Set as default**.

To return to the original launcher, open **Settings → About → Use original launcher**. If Android asks you to choose, select **HTCLauncherEN** and confirm. The original launcher remains installed.

## Updates and checksums

An update must be signed with the same key as the installed version and have a compatible version code. If Android refuses an update, check which build you installed and its signing information. Removing Vibe to change signing keys also removes its local app data, including favorites; switch back to the original launcher first. Do not remove the original launcher.

Each CI artifact includes `SHA256SUMS.txt` alongside the APK. To check it on a computer, run `sha256sum -c SHA256SUMS.txt` on Linux, or `shasum -a 256 -c SHA256SUMS.txt` on macOS, from the folder containing both files. A matching checksum confirms the file matches the checksum; it does not independently authenticate a compromised download source.
