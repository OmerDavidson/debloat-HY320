#!/usr/bin/env python3
"""Build/sign locally with an installed JDK 17 and Android SDK. Never uses ADB."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
SDK = Path(os.environ.get("ANDROID_SDK_ROOT", str(Path.home() / "Library/Android/sdk")))
TOOLS = SDK / "build-tools" / os.environ.get("VIBE_BUILD_TOOLS", "34.0.0")
ANDROID = SDK / "platforms" / os.environ.get("VIBE_PLATFORM", "android-34") / "android.jar"
BUILD = ROOT / "build"


def run(*argv):
    subprocess.run([str(a) for a in argv], cwd=ROOT, check=True)


def main():
    if not ANDROID.is_file() or not (TOOLS / "aapt2").is_file():
        raise SystemExit("Install Android SDK platform 34 / build-tools 34.0.0, or set VIBE_PLATFORM / VIBE_BUILD_TOOLS.")
    # Remove generated output only, never device state or source files.
    if BUILD.exists():
        shutil.rmtree(BUILD)
    for name in ["generated", "classes", "dex"]:
        (BUILD / name).mkdir(parents=True, exist_ok=True)
    run(TOOLS / "aapt2", "compile", "--dir", ROOT / "res", "-o", BUILD / "resources.zip")
    run(TOOLS / "aapt2", "link", "-I", ANDROID, "--manifest", ROOT / "AndroidManifest.xml",
        "--java", BUILD / "generated", "-o", BUILD / "unsigned.apk", BUILD / "resources.zip")
    sources = sorted((ROOT / "src").rglob("*.java")) + sorted((BUILD / "generated").rglob("*.java"))
    run("javac", "-source", "8", "-target", "8", "-encoding", "UTF-8", "-classpath", ANDROID,
        "-d", BUILD / "classes", *sources)
    run(TOOLS / "d8", "--lib", ANDROID, "--min-api", "26", "--output", BUILD / "dex",
        *sorted((BUILD / "classes").rglob("*.class")))
    with zipfile.ZipFile(BUILD / "unsigned.apk", "a", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(ROOT / "CLEANYOURTV-LICENSE.txt", "META-INF/CLEANYOURTV-LICENSE.txt")
        for dex in sorted((BUILD / "dex").glob("*.dex")):
            archive.write(dex, dex.name)
    run(TOOLS / "zipalign", "-f", "4", BUILD / "unsigned.apk", BUILD / "aligned.apk")
    configured_keystore = os.environ.get("VIBE_KEYSTORE")
    keystore = Path(configured_keystore) if configured_keystore else ROOT / "local-debug.keystore"
    if configured_keystore and not keystore.is_file():
        raise SystemExit("VIBE_KEYSTORE does not point to a file.")
    if not keystore.exists():
        run("keytool", "-genkeypair", "-keystore", keystore, "-storepass", "android", "-keypass", "android",
            "-alias", "vibe-local", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
            "-dname", "CN=Vibe Home Local Prototype")
        keystore.chmod(0o600)
    apk = BUILD / "vibe-home.apk"
    if configured_keystore:
        if not all(os.environ.get(name) for name in ("VIBE_KEY_ALIAS", "VIBE_KEYSTORE_PASSWORD", "VIBE_KEY_PASSWORD")):
            raise SystemExit("Set VIBE_KEY_ALIAS, VIBE_KEYSTORE_PASSWORD and VIBE_KEY_PASSWORD for the configured keystore.")
        signing = ["--ks-key-alias", os.environ["VIBE_KEY_ALIAS"],
                   "--ks-pass", "env:VIBE_KEYSTORE_PASSWORD", "--key-pass", "env:VIBE_KEY_PASSWORD"]
    else:
        signing = ["--ks-key-alias", "vibe-local", "--ks-pass", "pass:android"]
    run(TOOLS / "apksigner", "sign", "--ks", keystore, *signing, "--out", apk, BUILD / "aligned.apk")
    run(TOOLS / "apksigner", "verify", "--verbose", apk)
    record = {"apk": apk.name, "bytes": apk.stat().st_size,
              "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
              "build_tools": TOOLS.name, "platform": ANDROID.parent.name,
              "installation_performed": False,
              "source_sha256": {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
                                for p in sorted((ROOT / "src").rglob("*.java"))
                                + sorted((ROOT / "res").rglob("*.xml"))
                                + [ROOT / "AndroidManifest.xml", ROOT / "build.py", ROOT / "CLEANYOURTV-LICENSE.txt"]}}
    (BUILD / "artifact.json").write_text(json.dumps(record, indent=2) + "\n")
    print(json.dumps(record, indent=2))


if __name__ == "__main__":
    main()
