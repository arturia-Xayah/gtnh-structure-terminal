#!/usr/bin/env python3
"""Install project-local Azul JDKs needed by Gradle and the 1.7.10 toolchain."""
import json
import os
from pathlib import Path
import platform
import sys
import re
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def fetch(url, destination):
    with urllib.request.urlopen(url, timeout=60) as response, destination.open("wb") as out:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            out.write(chunk)


def install(version):
    for name in ("STRUCTURE_TERMINAL_JAVA" + str(version), "JAVA_HOME"):
        configured = os.environ.get(name)
        if configured:
            home = Path(configured)
            release = home / "release"
            if release.exists() and (home / "bin/javac").exists():
                match = re.search(r'JAVA_VERSION="([^"\n]+)', release.read_text())
                major = match.group(1).split(".")[1 if match and match.group(1).startswith("1.") else 0] if match else ""
                if major == str(version):
                    return home
    folder = ROOT / ".tools" / ("jdk" + str(version))
    existing = list(folder.glob("**/bin/java"))
    if existing:
        return existing[0].parent.parent
    os_name = {"Darwin": "macos", "Linux": "linux"}.get(platform.system())
    if not os_name:
        raise SystemExit("On Windows, install JDK 25, JDK 17 and JDK 8, then use gradlew.bat.")
    arch = "aarch64" if platform.machine().lower() in ("arm64", "aarch64") else "x86_64"
    url = ("https://api.azul.com/metadata/v1/zulu/packages/?java_version=%s&os=%s&arch=%s"
           "&archive_type=tar.gz&java_package_type=jdk&latest=true&release_status=ga&availability_types=CA") % (version, os_name, arch)
    with urllib.request.urlopen(url, timeout=30) as response:
        packages = json.load(response)
    package = next(p for p in packages if "-ca-jdk" in p["name"])
    folder.mkdir(parents=True, exist_ok=True)
    archive = folder / "jdk.tar.gz.part"
    print("Downloading " + package["name"], file=sys.stderr)
    fetch(package["download_url"], archive)
    with tarfile.open(archive) as tar:
        root = folder.resolve()
        for member in tar.getmembers():
            target = (root / member.name).resolve()
            if root not in target.parents and target != root:
                raise ValueError("Unsafe archive path")
            if member.isdev():
                raise ValueError("Unsupported archive entry")
            if member.issym() or member.islnk():
                base = target.parent if member.issym() else root
                link = (base / member.linkname).resolve()
                if root not in link.parents and link != root:
                    raise ValueError("Unsafe archive link")
        tar.extractall(folder)
    archive.unlink()
    java = next(folder.glob("**/bin/javac"))
    home = java.parent.parent
    print("Installed JDK %s at %s" % (version, home), file=sys.stderr)
    return home


if __name__ == "__main__":
    print(install(int(sys.argv[1]) if len(sys.argv) > 1 else 25))
