"""Shared library for launcher test scripts used in GitHub Actions workflows.

Contains reusable functions: resolve_dependencies, find_java_home.
"""

import json
import os
import subprocess
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path


# Known Modrinth project IDs for auto-resolvable dependencies
KNOWN_DEPENDENCIES = {
    "fabric-api": "P7dR8mSH",
    "fabric-api-base": "P7dR8mSH",
}
MODRINTH_API = "https://api.modrinth.com/v2"


def resolve_dependencies(mods_dir, game_version, loader):
    """Resolve and download missing dependencies from Modrinth.

    Scans all jars in mods_dir for fabric.mod.json, detects dependencies,
    and downloads matching versions from Modrinth for game_version + loader.
    """
    if loader != "fabric":
        return
    deps_needed = {}
    for jar_file in sorted(Path(mods_dir).glob("*.jar")):
        if "mc-runtime-test" in jar_file.name:
            continue
        try:
            with zipfile.ZipFile(str(jar_file), "r") as zf:
                if "fabric.mod.json" not in zf.namelist():
                    continue
                fmj = json.loads(zf.read("fabric.mod.json").decode("utf-8"))
                for dep_id, constraint in fmj.get("depends", {}).items():
                    if dep_id in ("minecraft", "fabricloader", "java"):
                        continue
                    if dep_id not in deps_needed:
                        deps_needed[dep_id] = constraint
        except Exception as e:
            print(f"  [dep] WARNING: Could not read {jar_file.name}: {e}")
    if not deps_needed:
        return
    print(f"  [dep] Dependencies detected: {deps_needed}")
    for dep_id, constraint in deps_needed.items():
        known_id = KNOWN_DEPENDENCIES.get(dep_id)
        if not known_id:
            print(f"  [dep] WARNING: Unknown dependency '{dep_id}' — cannot auto-resolve")
            continue
        if list(Path(mods_dir).glob(f"*{dep_id}*.jar")):
            print(f"  [dep] {dep_id} already present in mods/")
            continue
        print(f"  [dep] Resolving {dep_id} (Modrinth {known_id}) for {game_version}...")
        try:
            url = f"{MODRINTH_API}/project/{known_id}/version"
            params = urllib.parse.urlencode({
                "game_versions": json.dumps([game_version]),
                "loaders": json.dumps([loader]),
                "featured": "true",
            })
            req = urllib.request.Request(f"{url}?{params}", headers={"User-Agent": "ModCompiler/1.0"})
            with urllib.request.urlopen(req, timeout=30) as r:
                versions = json.loads(r.read().decode())
            if not versions:
                print(f"  [dep] WARNING: No {dep_id} version found for {game_version}")
                continue
            best = versions[0]
            files = best.get("files", [])
            if not files:
                continue
            dl_url, file_name = "", ""
            for f in files:
                if f.get("primary", False):
                    dl_url, file_name = f.get("url", ""), f.get("filename", "")
                    break
            if not dl_url:
                dl_url, file_name = files[0].get("url", ""), files[0].get("filename", "")
            if dl_url and file_name:
                dest = Path(mods_dir) / file_name
                print(f"  [dep] Downloading {file_name} ({best.get('version_number', '?')})...")
                urllib.request.urlretrieve(dl_url, str(dest))
                print(f"  [dep] Downloaded {file_name} ({dest.stat().st_size / 1024:.0f} KB)")
            else:
                print(f"  [dep] WARNING: No download URL for {dep_id}")
        except Exception as e:
            print(f"  [dep] ERROR resolving {dep_id}: {e}")


def find_java_home(version):
    """Find the correct Java home for the given version.

    Searches /opt/hostedtoolcache/ first, then JAVA_HOME env var,
    then falls back to parsing java -XshowSettings output.
    """
    tc = Path("/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk")
    if tc.exists():
        for d in sorted(tc.iterdir(), reverse=True):
            if d.is_dir() and d.name.split(".")[0] == str(version):
                candidate = d / "x64"
                if (candidate / "bin" / "java").exists():
                    return str(candidate)
    env_home = os.environ.get("JAVA_HOME", "")
    if env_home and Path(env_home, "bin", "java").exists():
        return env_home
    try:
        result = subprocess.run(
            ["java", "-XshowSettings:properties", "-version"],
            capture_output=True, text=True, timeout=10,
        )
        for line in (result.stderr + result.stdout).splitlines():
            if "java.home" in line:
                return line.split("=", 1)[1].strip()
    except Exception:
        pass
    return ""


def patch_fabric_mc_version(mod_jar, old_version, new_version):
    """Patch fabric.mod.json inside a jar to accept new_version instead of old_version."""
    try:
        with zipfile.ZipFile(str(mod_jar), "r") as zf:
            fmj = json.loads(zf.read("fabric.mod.json").decode("utf-8"))
        deps = fmj.get("depends", {})
        mc_dep = deps.get("minecraft", "")
        if mc_dep and mc_dep == old_version:
            deps["minecraft"] = new_version
            fmj["depends"] = deps
            print(f"  Patched fabric.mod.json: minecraft {old_version} -> {new_version}")
            tmp_jar = Path(str(mod_jar) + ".tmp")
            with zipfile.ZipFile(str(mod_jar), "r") as zin:
                with zipfile.ZipFile(str(tmp_jar), "w", zipfile.ZIP_DEFLATED) as zout:
                    for item in zin.infolist():
                        if item.filename == "fabric.mod.json":
                            zout.writestr(item, json.dumps(fmj, indent=2))
                        else:
                            zout.writestr(item, zin.read(item.filename))
            tmp_jar.replace(mod_jar)
            return True
        else:
            print(f"  NOTE: fabric.mod.json minecraft dep is '{mc_dep}', not '{old_version}' — skipping patch")
    except Exception as e:
        print(f"  WARNING: Could not patch fabric.mod.json: {e}")
    return False