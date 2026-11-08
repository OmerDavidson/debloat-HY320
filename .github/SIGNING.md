# CI APK signing

Every push runs the Build APK workflow. It uploads the APK, SHA-256 checksum, build metadata and `signing-info.txt` as a GitHub Actions artifact. It does not publish a GitHub Release. Workflow artifacts have limited retention and may require sign-in to download.

Without configured secrets, each run generates a new temporary development key. Its APK can be installed for testing, but cannot update an APK signed by another run. Do not publish these builds as a stable release.

To produce update-compatible builds, create a dedicated release keystore on a trusted computer, securely back it up, and configure these repository Actions secrets:

| Secret | Value |
| --- | --- |
| `VIBE_KEYSTORE_BASE64` | Base64-encoded keystore file, as a single line |
| `VIBE_KEY_ALIAS` | Key alias |
| `VIBE_KEYSTORE_PASSWORD` | Keystore password |
| `VIBE_KEY_PASSWORD` | Key password |

For example, outside the repository, generate a key with `keytool -genkeypair -v -keystore vibe-release.keystore -alias vibe-release -keyalg RSA -keysize 3072 -validity 10000`. Enter strong passwords when prompted. To print the file as a single Base64 line for the secret, run `python3 -c 'import base64; print(base64.b64encode(open("vibe-release.keystore", "rb").read()).decode())'` on the trusted computer. Paste the output directly into the secret and keep the file and passwords private; never commit them. Keep an offline backup: losing the key prevents in-place updates signed by it.

Set all four secrets under the repository's **Settings → Secrets and variables → Actions** before relying on the workflow for distribution. An incomplete configuration fails the build. Existing installations signed with the local prototype key cannot be updated with the new release key. Increment the manifest's version code and update the About label for each delivered app version as described in the project guide. A successful build and checksum do not establish device compatibility or replace device testing.
