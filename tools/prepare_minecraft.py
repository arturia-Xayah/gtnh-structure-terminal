#!/usr/bin/env python3
"""Optional verified-cache fallback for unavailable legacy Mojang/Forge domains.

Only build dependencies are downloaded; none are bundled in the distributed mod.
"""
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(os.environ.get("GRADLE_USER_HOME", str(ROOT / ".tools/gradle-cache"))) / "caches"


def download(urls, digest=None, algorithm="sha256"):
    failures = []
    for url in urls:
        try:
            with urllib.request.urlopen(url, timeout=45) as response:
                data = response.read()
            if digest and hashlib.new(algorithm, data).hexdigest() != digest:
                raise ValueError("Checksum mismatch for " + url)
            return data
        except (OSError, ValueError) as error:
            failures.append(str(error))
    raise RuntimeError("All download endpoints failed: " + "; ".join(failures))


def put(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as out:
        out.write(data)
        temporary = Path(out.name)
    temporary.replace(path)


def unzip(data, target):
    target.mkdir(parents=True, exist_ok=True)
    base = target.resolve()
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in archive.namelist():
            path = (base / name).resolve()
            if path != base and base not in path.parents:
                raise ValueError("Unsafe ZIP path")
        archive.extractall(target)


def main():
    vanilla = CACHE / "retro_futura_gradle/mc-vanilla"
    metadata = json.loads(download([
        "https://bmclapi2.bangbang93.com/version/1.7.10/json",
    ]))
    expected = {"client": "e80d9b3bf5085002218d4be59e668bac718abbc6",
                "server": "952438ac4e01b4d115c5fc38f891710c4941df29"}
    for kind, sha1 in expected.items():
        if metadata["downloads"][kind]["sha1"] != sha1:
            raise ValueError("Unexpected Minecraft 1.7.10 metadata")
        destination = vanilla / "1.7.10" / (kind + ".jar")
        if not destination.exists() or hashlib.sha1(destination.read_bytes()).hexdigest() != sha1:
            print("Preparing Minecraft 1.7.10 " + kind, flush=True)
            put(destination, download([metadata["downloads"][kind]["url"],
                "https://bmclapi2.bangbang93.com/version/1.7.10/" + kind], sha1, "sha1"))
    put(vanilla / "manifest_1.7.10.json", json.dumps(metadata).encode())
    put(vanilla / "all_versions_manifest.json", json.dumps({"versions": [{
        "id": "1.7.10", "url": "https://bmclapi2.bangbang93.com/version/1.7.10/json"
    }]}).encode())

    mappings = CACHE / "minecraft/de/oceanlabs/mcp/mcp_stable/12"
    if not (mappings / "joined.srg").exists() or not (mappings / "fields.csv").exists():
        srg = download(["https://raw.githubusercontent.com/ModCoderPack/mcpbot/master/mcp-1.7.10-srg.zip"],
                       "d49df5c445dcf00ada532ab3b0e4283bf4b05ac13f210647658dfd3cec8b34c2")
        stable = download(["https://maven.cleanroommc.com/de/oceanlabs/mcp/mcp_stable/12-1.7.10/mcp_stable-12-1.7.10.zip"],
                          "83d303a80071582cd07d41b35aa77c178d609a27c69c8472b794636eb50905b0")
        unzip(srg, mappings)
        unzip(stable, mappings)

    version = "1.7.10-10.13.4.1614-1.7.10"
    forge = CACHE / "minecraft/net/minecraftforge/forge" / version / "unpacked"
    if not (forge / "dev.json").exists():
        data = download(["https://maven.cleanroommc.com/net/minecraftforge/forge/" + version + "/forge-" + version + "-userdev.jar"],
                        "6ed364c0defdb481a41ef2d99c02d382f16fc42a6ea0ea1c2b849848d8466e91")
        unzip(data, forge)
        for name, destination in [("sources.zip", "src/main/java"), ("resources.zip", "src/main/resources")]:
            nested = forge / name
            if nested.exists():
                unzip(nested.read_bytes(), forge / destination)

    fernflower = CACHE / "retro_futura_gradle/mcp/fernflower.jar"
    if not fernflower.exists():
        data = download(["https://raw.githubusercontent.com/anatawa12/ForgeGradle-resources/master/fernflower-fix-FG1.2.zip"],
                        "d19b6972bf35d51a37d3ae71c3699bb10687f1210729b796f74e167b23508a26")
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            entry = next(n for n in archive.namelist() if n.endswith("/fernflower.jar"))
            put(fernflower, archive.read(entry))
    print("Legacy build dependencies prepared; game jars stay in the local Gradle cache.")


if __name__ == "__main__":
    main()
