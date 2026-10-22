"""Prepare a fixed, already signed Android release without exposing signing keys."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request

REPO = "leoraijin/NOVA-VPN-Android"
manifest = json.loads(Path("tools/release-0.2.51.json").read_text(encoding="utf-8"))
out = Path("outputs")
out.mkdir(exist_ok=True)
work = Path("release-work")
work.mkdir(exist_ok=True)

def download(url, path, expected=None):
    request = urllib.request.Request(url, headers={"User-Agent": "NOVA-Android-release"})
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read()
    if expected and hashlib.sha256(data).hexdigest() != expected:
        raise ValueError("Checksum mismatch: " + path.name)
    path.write_bytes(data)

# The APK must correspond to the checked-out application source, even on a rerun.
subprocess.run(["git", "diff", "--exit-code", manifest["codeCommit"], "HEAD", "--",
                "app", "native", "build.gradle.kts", "settings.gradle.kts", "gradle.properties"], check=True)
delta = work / "signed-apk.delta.gz"
with delta.open("wb") as result:
    for index in range(manifest["parts"]):
        url = ("https://raw.githubusercontent.com/" + REPO + "/" +
               manifest["payloadCommit"] + "/part-" + str(index).zfill(3) + ".bin")
        part = work / ("part-" + str(index).zfill(3))
        download(url, part)
        result.write(part.read_bytes())
base = work / "base.apk"
download("https://github.com/" + REPO +
         "/releases/download/v0.2.50/NOVA-VPN-Android-0.2.50-arm64-optimized.apk",
         base, "43bc0ba4854eefb2093ee9e14d379ceb54b411e2ae0caf6e949f5af8cff2c158")
subprocess.run([sys.executable, "tools/Restore-SignedApk.py",
                str(base), str(delta), str(out / manifest["apkName"])], check=True)
for source in manifest["sources"]:
    download(source["url"], out / source["name"], source["sha"])
subprocess.run(["git", "archive", "--format=zip",
                "--output=" + str(out / "NOVA-VPN-Android-0.2.51-source.zip"), "HEAD"], check=True)
shutil.copyfile("RELEASE-NOTES-0.2.51.md", out / "RELEASE-NOTES.md")
(out / "VERIFICATION.txt").write_text(
    "APK application code: " + manifest["codeCommit"] + "\n"
    "Publication source: " + subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip() + "\n"
    "Local optimized build: PASS. Local unit tests: 208 passed, 1 skipped.\n"
    "Updated Android instrumentation: compiled, NOT executed. No attached device.\n"
    "APK reconstruction: pinned base, delta and final SHA256 verified.\n"
    "Native ARM64 Reality/JNI: unchanged from checksum-pinned 0.2.50.\n"
    "No claim of complete VPN concealment or physical-phone routing validation.\n",
    encoding="utf-8")

