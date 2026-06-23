"""Shared library for launcher test scripts used in GitHub Actions workflows.

Contains reusable functions: resolve_dependencies, find_java_home.
"""

import json
import os
import re
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

    Returns a list of unresolved dependency IDs (empty if all resolved).
    A dependency is "unresolved" if:
      - It is not in KNOWN_DEPENDENCIES (no Modrinth project ID)
      - No matching version exists on Modrinth for the target game_version+loader
      - The download failed
    """
    unresolved = []
    if loader != "fabric":
        return unresolved
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
        return unresolved
    print(f"  [dep] Dependencies detected: {deps_needed}")
    for dep_id, constraint in deps_needed.items():
        known_id = KNOWN_DEPENDENCIES.get(dep_id)
        if not known_id:
            print(f"  [dep] UNRESOLVED: Unknown dependency '{dep_id}' — cannot auto-resolve")
            unresolved.append(dep_id)
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
                print(f"  [dep] UNRESOLVED: No {dep_id} version found for {game_version}")
                unresolved.append(dep_id)
                continue
            best = versions[0]
            files = best.get("files", [])
            if not files:
                print(f"  [dep] UNRESOLVED: No files in {dep_id} version for {game_version}")
                unresolved.append(dep_id)
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
                print(f"  [dep] UNRESOLVED: No download URL for {dep_id}")
                unresolved.append(dep_id)
        except Exception as e:
            print(f"  [dep] UNRESOLVED: Error resolving {dep_id}: {e}")
            unresolved.append(dep_id)
    if unresolved:
        print(f"  [dep] UNRESOLVED dependencies: {unresolved}")
    else:
        print(f"  [dep] All dependencies resolved successfully")
    return unresolved


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


def _neoforge_version_prefix(mc_version):
    """Map a Minecraft version to the NeoForge version prefix.

    NeoForge versions drop the "1." prefix from Minecraft versions.
    Examples:
      - 1.20.6 -> 20.6
      - 1.21   -> 21.0
      - 1.21.1 -> 21.1
      - 26.1   -> 26.1
    """
    parts = mc_version.split(".")
    if parts[0] == "1" and len(parts) >= 2:
        return f"{parts[1]}.{parts[2] if len(parts) > 2 else '0'}"
    return ".".join(parts[:2])


def resolve_neoforge_version(mc_version, install_stdout=""):
    """Resolve the NeoForge loader version for a Minecraft version.

    First tries to parse the version from HeadlessMC install stdout
    (e.g. "Installing NeoForge 1.21.1-21.1.234" -> "21.1.234").
    Falls back to the latest matching version from NeoForge Maven metadata.
    """
    m = re.search(r"Installing NeoForge [^-\s]+-([\w.\-]+)", install_stdout)
    if m:
        return m.group(1).strip()

    prefix = _neoforge_version_prefix(mc_version)
    url = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "ModCompiler/1.0"})
        with urllib.request.urlopen(req, timeout=30) as r:
            xml = r.read().decode("utf-8")
        versions = re.findall(r"<version>([^<]+)</version>", xml)
        matching = sorted([v for v in versions if v.startswith(prefix + ".")])
        return matching[-1] if matching else None
    except Exception as e:
        print(f"  Could not fetch NeoForge versions: {e}")
        return None


def install_neoforge_direct(mc_version, neoforge_version, timeout=600):
    """Download and run the NeoForge installer directly.

    Returns (returncode, stdout, stderr).
    """
    installer_jar = f"neoforge-{neoforge_version}-installer.jar"
    installer_url = (
        f"https://maven.neoforged.net/releases/net/neoforged/neoforge/"
        f"{neoforge_version}/{installer_jar}"
    )
    if not Path(installer_jar).exists():
        print(f"  Downloading NeoForge installer {installer_jar}...")
        urllib.request.urlretrieve(installer_url, installer_jar)
        print(f"  Downloaded {installer_jar} ({Path(installer_jar).stat().st_size / 1024:.0f} KB)")
    print(f"  Running NeoForge installer for {neoforge_version}...")
    java_bin = "java"
    java_home = os.environ.get("JAVA_HOME", "")
    if java_home and Path(java_home, "bin", "java").exists():
        java_bin = str(Path(java_home, "bin", "java"))
    result = subprocess.run(
        [java_bin, "-jar", installer_jar, "--install-client"],
        capture_output=True, text=True, timeout=timeout
    )
    return result.returncode, result.stdout or "", result.stderr or ""