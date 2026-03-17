from __future__ import annotations

import json
import re
import subprocess
import tempfile
import os
import sys
import traceback
import tomllib
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from modcompiler.common import (
    ModCompilerError,
    copy_file,
    copy_tree,
    load_json,
    make_slug,
    parse_version_tuple,
    render_summary_markdown,
    safe_rmtree,
    write_json,
)


MANIFEST_PATH = Path("META-INF/MANIFEST.MF")
FABRIC_METADATA_BASENAME = "fabric.mod.json"
FORGE_TOML_BASENAMES = ("neoforge.mods.toml", "mods.toml")
FORGE_MCMOD_BASENAME = "mcmod.info"


def command_decompile_jar(args: Any) -> int:
    artifact_dir = Path(args.artifact_dir)
    safe_rmtree(artifact_dir)
    artifact_dir.mkdir(parents=True, exist_ok=True)
    log_path = artifact_dir / "decompile.log"
    metadata = default_decompile_metadata(str(args.jar_path))
    exit_code = 1
    try:
        jar_path = resolve_input_jar_path(args.jar_path)
        metadata["jar_name"] = jar_path.name
        metadata["resolved_jar_path"] = relativize_to_cwd(jar_path)
        if jar_path.suffix.lower() != ".jar":
            raise ModCompilerError(f"Expected a .jar file but got: {jar_path.name}")

        manifest = load_json(Path(args.manifest))
        inspected = inspect_mod_jar(jar_path, manifest)
        metadata.update(inspected)
        metadata["requested_jar_path"] = str(args.jar_path)
        metadata["resolved_jar_path"] = relativize_to_cwd(jar_path)
        slug = make_slug(metadata["primary_mod_id"], metadata["loader"], "decompiled")
        metadata["slug"] = slug

        info_path = artifact_dir / "mod_info.txt"
        info_path.write_text(render_mod_info(metadata), encoding="utf-8")

        if not jar_has_class_files(jar_path):
            metadata["warnings"].append("Jar contains no .class files; skipping decompiler.")
            metadata["status"] = "skipped"
            exit_code = 0
        else:
            decompiler_jar = Path(args.decompiler_jar)
            if not decompiler_jar.is_absolute():
                decompiler_jar = Path.cwd() / decompiler_jar
            if not decompiler_jar.exists():
                raise ModCompilerError(f"Decompiler jar does not exist: {decompiler_jar}")

            with tempfile.TemporaryDirectory(prefix=f"modcompiler-decompile-{slug}-") as temp_dir:
                temp_root = Path(temp_dir)
                expanded_jar = temp_root / "expanded-jar"
                java_output = temp_root / "java-output"
                package_root = temp_root / "package"
                package_src = package_root / "src" / "main" / "java"
                package_root.mkdir(parents=True, exist_ok=True)
                expanded_jar.mkdir(parents=True, exist_ok=True)
                java_output.mkdir(parents=True, exist_ok=True)

                with zipfile.ZipFile(jar_path) as archive:
                    archive.extractall(expanded_jar)

                command = [
                    "java",
                    "-jar",
                    str(decompiler_jar),
                    str(expanded_jar),
                    str(java_output),
                ]
                with log_path.open("w", encoding="utf-8") as log_file:
                    log_file.write("$ " + " ".join(command) + "\n\n")
                    run = subprocess.run(
                        command,
                        cwd=Path.cwd(),
                        stdout=log_file,
                        stderr=subprocess.STDOUT,
                        text=True,
                    )
                if run.returncode != 0:
                    metadata["warnings"].append("Decompiler exited with a non-zero status.")
                else:
                    for source_file in sorted(java_output.rglob("*")):
                        if not source_file.is_file():
                            continue
                        relative = source_file.relative_to(java_output)
                        destination = package_src / relative
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        copy_file(source_file, destination)
                    if not list(package_src.rglob("*.java")) and not list(package_src.rglob("*.kt")):
                        metadata["warnings"].append("Decompiler finished without producing Java or Kotlin source files.")
                    else:
                        inferred_entrypoint = detect_forge_entrypoint(package_root / "src", metadata["primary_mod_id"])
                        if inferred_entrypoint and not metadata["metadata"]["entrypoint_class"]:
                            metadata["metadata"]["entrypoint_class"] = inferred_entrypoint
                            metadata["metadata"]["group"] = entrypoint_group(inferred_entrypoint)
                            info_path.write_text(render_mod_info(metadata), encoding="utf-8")
                        zip_path = artifact_dir / f"{slug}.zip"
                        create_decompile_zip(package_root, zip_path)
                        metadata["status"] = "success"
                        metadata["zip_name"] = zip_path.name
                        exit_code = 0
    except ModCompilerError as error:
        metadata["warnings"].append(str(error))
        append_failure_log(log_path, str(error))
    except Exception as error:  # pragma: no cover - defensive fallback
        metadata["warnings"].append(str(error))
        append_failure_log(
            log_path,
            "Unhandled decompile exception:\n" + "".join(traceback.format_exception(error)),
        )

    if metadata["status"] not in {"success", "skipped"}:
        metadata["status"] = "failed"
    metadata["log_relpath"] = "decompile.log"
    if (artifact_dir / "mod_info.txt").exists():
        (artifact_dir / "mod_info.txt").write_text(render_mod_info(metadata), encoding="utf-8")
    summary_markdown = render_decompile_summary_markdown(metadata)
    (artifact_dir / "SUMMARY.md").write_text(summary_markdown, encoding="utf-8")
    write_json(artifact_dir / "result.json", metadata)
    return exit_code


def default_decompile_metadata(requested_jar_path: str) -> dict[str, Any]:
    requested = Path(requested_jar_path)
    primary_mod_id = requested.stem.lower() or "unknown"
    return {
        "jar_name": requested.name or requested_jar_path,
        "requested_jar_path": requested_jar_path,
        "resolved_jar_path": "",
        "loader": "unknown",
        "loader_detail": "",
        "metadata_source": "",
        "supported_minecraft": "",
        "loader_version": "",
        "resolved_range_folders": [],
        "warnings": [],
        "detected_mod_ids": [],
        "primary_mod_id": primary_mod_id,
        "slug": make_slug(primary_mod_id, "unknown", "decompiled"),
        "status": "failed",
        "zip_name": "",
        "metadata": {
            "mod_id": primary_mod_id,
            "name": requested.stem or requested_jar_path,
            "mod_version": "",
            "description": "",
            "authors": [],
            "license": "",
            "homepage": "",
            "sources": "",
            "issues": "",
            "entrypoint_class": "",
            "group": "",
        },
    }


def resolve_input_jar_path(raw_path: str) -> Path:
    path = Path(raw_path)
    candidates: list[Path] = []
    if path.is_absolute():
        candidates.append(path)
    else:
        repo_root = Path.cwd()
        candidates.append(repo_root / path)
        if len(path.parts) == 1:
            candidates.append(repo_root / "To Be Decompiled" / path)
    for candidate in candidates:
        if candidate.exists():
            return candidate
    searched = ", ".join(str(candidate) for candidate in candidates) or raw_path
    raise ModCompilerError(f"Jar path does not exist. Checked: {searched}")


def jar_has_class_files(jar_path: Path) -> bool:
    with zipfile.ZipFile(jar_path) as archive:
        return any(
            info.filename.endswith(".class")
            for info in archive.infolist()
            if not info.is_dir()
        )


def relativize_to_cwd(path: Path) -> str:
    try:
        return str(path.relative_to(Path.cwd()))
    except ValueError:
        return str(path)


def append_failure_log(log_path: Path, message: str) -> None:
    with log_path.open("a", encoding="utf-8") as log_file:
        log_file.write(message.rstrip() + "\n")


def inspect_mod_jar(jar_path: Path, manifest: dict[str, Any]) -> dict[str, Any]:
    primary_mod_id = jar_path.stem.lower()
    metadata: dict[str, Any] = {
        "jar_name": jar_path.name,
        "requested_jar_path": str(jar_path),
        "resolved_jar_path": str(jar_path),
        "loader": "unknown",
        "loader_detail": "",
        "metadata_source": "",
        "supported_minecraft": "",
        "loader_version": "",
        "resolved_range_folders": [],
        "warnings": [],
        "detected_mod_ids": [],
        "metadata": {
            "mod_id": primary_mod_id,
            "name": jar_path.stem,
            "mod_version": "",
            "description": "",
            "authors": [],
            "license": "",
            "homepage": "",
            "sources": "",
            "issues": "",
            "entrypoint_class": "",
            "group": "",
        },
    }

    with zipfile.ZipFile(jar_path) as archive:
        entries = [info.filename for info in archive.infolist() if not info.is_dir()]
        manifest_attributes = parse_manifest_attributes(read_zip_text(archive, str(MANIFEST_PATH)))
        fabric_entry = find_entry_by_basename(entries, FABRIC_METADATA_BASENAME)
        forge_toml_entry = None
        for basename in FORGE_TOML_BASENAMES:
            forge_toml_entry = find_entry_by_basename(entries, basename)
            if forge_toml_entry:
                break
        forge_mcmod_entry = find_entry_by_basename(entries, FORGE_MCMOD_BASENAME)

        if fabric_entry:
            payload = json.loads(read_zip_text(archive, fabric_entry))
            metadata.update(extract_fabric_metadata(payload))
            metadata["metadata_source"] = fabric_entry
        elif forge_toml_entry:
            payload = tomllib.loads(read_zip_text(archive, forge_toml_entry))
            metadata.update(extract_forge_toml_metadata(payload))
            metadata["metadata_source"] = forge_toml_entry
        elif forge_mcmod_entry:
            payload = json.loads(read_zip_text(archive, forge_mcmod_entry))
            metadata.update(extract_mcmod_metadata(payload))
            metadata["metadata_source"] = forge_mcmod_entry
        else:
            metadata["warnings"].append("No supported mod metadata file was found in the jar.")

        apply_manifest_fallbacks(metadata, manifest_attributes)

    metadata["primary_mod_id"] = metadata["metadata"]["mod_id"] or primary_mod_id
    metadata["resolved_range_folders"] = infer_range_folders(
        manifest,
        metadata["loader"],
        metadata["supported_minecraft"],
    )
    if not metadata["resolved_range_folders"]:
        metadata["warnings"].append("Could not confidently resolve this jar to a configured repo version folder.")
    return metadata


def parse_manifest_attributes(text: str) -> dict[str, str]:
    if not text:
        return {}
    attributes: dict[str, str] = {}
    current_key: str | None = None
    for raw_line in text.splitlines():
        if raw_line.startswith(" ") and current_key:
            attributes[current_key] += raw_line[1:]
            continue
        if ":" not in raw_line:
            current_key = None
            continue
        key, value = raw_line.split(":", 1)
        current_key = key.strip()
        attributes[current_key] = value.strip()
    return attributes


def read_zip_text(archive: zipfile.ZipFile, entry_name: str) -> str:
    try:
        with archive.open(entry_name) as handle:
            return handle.read().decode("utf-8")
    except KeyError:
        return ""


def find_entry_by_basename(entries: list[str], basename: str) -> str | None:
    for entry in entries:
        if Path(entry).name == basename:
            return entry
    return None


def extract_fabric_metadata(payload: dict[str, Any]) -> dict[str, Any]:
    entrypoint = first_fabric_entrypoint(payload.get("entrypoints", {}).get("main"))
    authors = normalize_authors(payload.get("authors"))
    contact = payload.get("contact", {}) if isinstance(payload.get("contact"), dict) else {}
    mod_id = str(payload.get("id", "")).strip()
    return {
        "loader": "fabric",
        "loader_detail": "fabricloader",
        "supported_minecraft": str(payload.get("depends", {}).get("minecraft", "")).strip(),
        "loader_version": str(payload.get("depends", {}).get("fabricloader", "")).strip(),
        "detected_mod_ids": [mod_id] if mod_id else [],
        "metadata": {
            "mod_id": mod_id,
            "name": str(payload.get("name", "")).strip(),
            "mod_version": str(payload.get("version", "")).strip(),
            "description": str(payload.get("description", "")).strip(),
            "authors": authors,
            "license": normalize_license(payload.get("license")),
            "homepage": str(contact.get("homepage", "")).strip(),
            "sources": str(contact.get("sources", "")).strip(),
            "issues": str(contact.get("issues", "")).strip(),
            "entrypoint_class": entrypoint,
            "group": entrypoint_group(entrypoint),
        },
    }


def extract_forge_toml_metadata(payload: dict[str, Any]) -> dict[str, Any]:
    mods = payload.get("mods") or []
    if not isinstance(mods, list) or not mods:
        raise ModCompilerError("mods.toml does not define any [[mods]] entries")
    primary = mods[0]
    mod_id = str(primary.get("modId", "")).strip()
    dependencies = payload.get("dependencies", {})
    minecraft_range = ""
    forge_range = ""
    loader = "forge"
    if isinstance(dependencies, dict):
        for dep in dependencies.get(mod_id, []) or []:
            dep_id = str(dep.get("modId", "")).strip()
            version_range = str(dep.get("versionRange", "")).strip()
            if dep_id == "minecraft":
                minecraft_range = version_range
            if dep_id == "neoforge":
                loader = "neoforge"
                forge_range = version_range
            elif dep_id == "forge":
                forge_range = version_range
    return {
        "loader": loader,
        "loader_detail": str(payload.get("modLoader", "")).strip(),
        "supported_minecraft": minecraft_range,
        "loader_version": forge_range or str(payload.get("loaderVersion", "")).strip(),
        "detected_mod_ids": [str(entry.get("modId", "")).strip() for entry in mods if entry.get("modId")],
        "metadata": {
            "mod_id": mod_id,
            "name": str(primary.get("displayName", "")).strip(),
            "mod_version": str(primary.get("version", "")).strip(),
            "description": str(primary.get("description", "")).strip(),
            "authors": normalize_authors(primary.get("authors")),
            "license": str(payload.get("license", "")).strip(),
            "homepage": str(primary.get("displayURL", "")).strip(),
            "sources": "",
            "issues": str(payload.get("issueTrackerURL", "")).strip(),
            "entrypoint_class": "",
            "group": "",
        },
    }


def extract_mcmod_metadata(payload: Any) -> dict[str, Any]:
    entries = payload if isinstance(payload, list) else [payload]
    if not entries:
        raise ModCompilerError("mcmod.info does not define any mod entries")
    primary = entries[0]
    mod_id = str(primary.get("modid", "")).strip()
    return {
        "loader": "forge",
        "loader_detail": "fml",
        "supported_minecraft": str(primary.get("mcversion", "")).strip(),
        "loader_version": "",
        "detected_mod_ids": [str(entry.get("modid", "")).strip() for entry in entries if entry.get("modid")],
        "metadata": {
            "mod_id": mod_id,
            "name": str(primary.get("name", "")).strip(),
            "mod_version": str(primary.get("version", "")).strip(),
            "description": str(primary.get("description", "")).strip(),
            "authors": normalize_authors(primary.get("authorList")),
            "license": "",
            "homepage": str(primary.get("url", "")).strip(),
            "sources": "",
            "issues": str(primary.get("updateUrl", "")).strip(),
            "entrypoint_class": "",
            "group": "",
        },
    }


def normalize_authors(raw: Any) -> list[str]:
    if raw is None:
        return []
    if isinstance(raw, str):
        return [item.strip() for item in raw.split(",") if item.strip()]
    if isinstance(raw, list):
        authors: list[str] = []
        for item in raw:
            if isinstance(item, str) and item.strip():
                authors.append(item.strip())
            elif isinstance(item, dict):
                name = str(item.get("name", "")).strip()
                if name:
                    authors.append(name)
        return authors
    return []


def normalize_license(raw: Any) -> str:
    if isinstance(raw, str):
        return raw.strip()
    if isinstance(raw, list):
        return ", ".join(str(item).strip() for item in raw if str(item).strip())
    return ""


def first_fabric_entrypoint(raw: Any) -> str:
    if not isinstance(raw, list):
        return ""
    for item in raw:
        if isinstance(item, str) and item.strip():
            return item.strip()
        if isinstance(item, dict):
            value = str(item.get("value", "")).strip()
            if value:
                return value
    return ""


def entrypoint_group(entrypoint_class: str) -> str:
    if "." not in entrypoint_class:
        return ""
    return entrypoint_class.rsplit(".", 1)[0]


def apply_manifest_fallbacks(metadata: dict[str, Any], attributes: dict[str, str]) -> None:
    details = metadata["metadata"]
    if not details["name"]:
        details["name"] = attributes.get("Implementation-Title", "") or attributes.get("Specification-Title", "")
    if not details["mod_version"]:
        details["mod_version"] = attributes.get("Implementation-Version", "") or attributes.get("Specification-Version", "")
    if not details["authors"]:
        vendor = attributes.get("Implementation-Vendor", "") or attributes.get("Specification-Vendor", "")
        details["authors"] = normalize_authors(vendor)
    if not details["entrypoint_class"]:
        main_class = attributes.get("Main-Class", "")
        if main_class:
            details["entrypoint_class"] = main_class
            details["group"] = entrypoint_group(main_class)


def infer_range_folders(manifest: dict[str, Any], loader: str, supported_minecraft: str) -> list[str]:
    if not supported_minecraft or loader not in {"fabric", "forge", "neoforge"}:
        return []
    raw = supported_minecraft.strip()
    exact = re.fullmatch(r"\d+\.\d+(?:\.\d+)?", raw)
    if exact:
        return matching_exact_ranges(manifest, loader, raw)

    wildcard = re.fullmatch(r"(\d+\.\d+)(?:\.(?:x|\*))?", raw)
    if wildcard:
        return matching_prefix_ranges(manifest, loader, wildcard.group(1))

    if raw.startswith("~"):
        version = raw[1:].strip()
        if re.fullmatch(r"\d+\.\d+(?:\.\d+)?", version):
            return matching_prefix_ranges(manifest, loader, ".".join(version.split(".")[:2]))

    range_match = re.fullmatch(r"([\[(])\s*([^,\s]*)\s*,\s*([^)\]]*)\s*([)\]])", raw)
    if range_match:
        lower_inclusive = range_match.group(1) == "["
        upper_inclusive = range_match.group(4) == "]"
        lower = range_match.group(2) or None
        upper = range_match.group(3) or None
        if lower and not re.fullmatch(r"\d+\.\d+(?:\.\d+)?", lower):
            lower = None
        if upper and not re.fullmatch(r"\d+\.\d+(?:\.\d+)?", upper):
            upper = None
        return matching_interval_ranges(manifest, loader, lower, upper, lower_inclusive, upper_inclusive)

    discovered_versions = re.findall(r"\d+\.\d+(?:\.\d+)?", raw)
    folders: list[str] = []
    for version in discovered_versions:
        for folder in matching_exact_ranges(manifest, loader, version):
            if folder not in folders:
                folders.append(folder)
    return folders


def matching_exact_ranges(manifest: dict[str, Any], loader: str, version: str) -> list[str]:
    folders: list[str] = []
    target = parse_version_tuple(version)
    for range_entry in manifest["ranges"]:
        if loader not in range_entry["loaders"]:
            continue
        lower = parse_version_tuple(range_entry["min_version"])
        upper = parse_version_tuple(range_entry["max_version"])
        if lower <= target <= upper:
            folders.append(range_entry["folder"])
    return folders


def matching_prefix_ranges(manifest: dict[str, Any], loader: str, prefix: str) -> list[str]:
    folders: list[str] = []
    for range_entry in manifest["ranges"]:
        if loader not in range_entry["loaders"]:
            continue
        if range_entry["min_version"].startswith(prefix) or range_entry["max_version"].startswith(prefix):
            folders.append(range_entry["folder"])
    return folders


def matching_interval_ranges(
    manifest: dict[str, Any],
    loader: str,
    lower: str | None,
    upper: str | None,
    lower_inclusive: bool,
    upper_inclusive: bool,
) -> list[str]:
    folders: list[str] = []
    lower_tuple = parse_version_tuple(lower) if lower else None
    upper_tuple = parse_version_tuple(upper) if upper else None
    for range_entry in manifest["ranges"]:
        if loader not in range_entry["loaders"]:
            continue
        range_min = parse_version_tuple(range_entry["min_version"])
        range_max = parse_version_tuple(range_entry["max_version"])
        if lower_tuple is not None:
            if lower_inclusive:
                if range_max < lower_tuple:
                    continue
            elif range_max <= lower_tuple:
                continue
        if upper_tuple is not None:
            if upper_inclusive:
                if range_min > upper_tuple:
                    continue
            elif range_min >= upper_tuple:
                continue
        folders.append(range_entry["folder"])
    return folders


def detect_forge_entrypoint(source_root: Path, primary_mod_id: str) -> str:
    for stem in ("src/main/java", "src/main/kotlin", "src/client/java", "src/client/kotlin"):
        base = source_root / stem
        if not base.exists():
            continue
        for path in sorted(base.rglob("*")):
            if not path.is_file() or path.suffix not in {".java", ".kt"}:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            if "@Mod(" not in text:
                continue
            if primary_mod_id and primary_mod_id not in text:
                continue
            relative = path.relative_to(base).with_suffix("")
            return ".".join(relative.parts)
    return ""


def render_mod_info(metadata: dict[str, Any]) -> str:
    details = metadata["metadata"]
    lines = [
        f"jar_name={metadata['jar_name']}",
        f"requested_jar_path={metadata.get('requested_jar_path', '')}",
        f"resolved_jar_path={metadata.get('resolved_jar_path', '')}",
        f"loader={metadata['loader']}",
        f"loader_detail={metadata['loader_detail']}",
        f"metadata_source={metadata['metadata_source']}",
        f"supported_minecraft={metadata['supported_minecraft']}",
        f"loader_version={metadata['loader_version']}",
        f"resolved_range_folders={','.join(metadata['resolved_range_folders'])}",
        f"mod_ids={','.join(metadata['detected_mod_ids'])}",
        f"primary_mod_id={details['mod_id']}",
        f"name={details['name']}",
        f"mod_version={details['mod_version']}",
        f"entrypoint_class={details['entrypoint_class']}",
        f"group={details['group']}",
        f"description={details['description'].replace(chr(10), '\\n')}",
        f"authors={','.join(details['authors'])}",
        f"license={details['license']}",
        f"homepage={details['homepage']}",
        f"sources={details['sources']}",
        f"issues={details['issues']}",
        f"warnings={' | '.join(metadata['warnings'])}",
    ]
    return "\n".join(lines) + "\n"


def create_decompile_zip(package_root: Path, zip_path: Path) -> None:
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(package_root.rglob("*")):
            if not path.is_file():
                continue
            archive.write(path, path.relative_to(package_root))


def render_decompile_summary_markdown(metadata: dict[str, Any]) -> str:
    details = metadata["metadata"]
    folders = ", ".join(metadata["resolved_range_folders"]) or "-"
    warnings = " | ".join(metadata["warnings"]) or "-"
    zip_name = metadata.get("zip_name") or "-"
    return "\n".join(
        [
            "# Jar Decompile Summary",
            "",
            "| Status | Requested Path | Resolved Path | Jar | Loader | Mod ID | Name | Version | Supported Minecraft | Repo Folders | Zip |",
            "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
            f"| {metadata.get('status', 'failed')} | {metadata.get('requested_jar_path', '-') or '-'} | "
            f"{metadata.get('resolved_jar_path', '-') or '-'} | "
            f"{metadata['jar_name']} | {metadata['loader']} | {details['mod_id']} | {details['name']} | "
            f"{details['mod_version'] or '-'} | {metadata['supported_minecraft'] or '-'} | {folders} | {zip_name} |",
            "",
            f"Warnings: {warnings}",
            "",
        ]
    )


@dataclass
class DecompileResult:
    extracted_src: Path
    metadata: dict[str, Any]
    status: str


def decompile_jar_internal(jar_path: Path, manifest: dict[str, Any], output_dir: Path | None = None) -> DecompileResult:
    metadata = inspect_mod_jar(jar_path, manifest)
    slug = make_slug(metadata["primary_mod_id"], metadata["loader"], "decompiled")

    if output_dir is None:
        output_dir = Path.cwd() / f"decompiled-{slug}"

    safe_rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    temp_root = Path(tempfile.mkdtemp(prefix=f"modcompiler-decompile-{slug}-"))
    try:
        expanded_jar = temp_root / "expanded-jar"
        java_output = temp_root / "java-output"
        package_root = temp_root / "package"
        package_src = package_root / "src" / "main" / "java"
        package_root.mkdir(parents=True, exist_ok=True)
        expanded_jar.mkdir(parents=True, exist_ok=True)
        java_output.mkdir(parents=True, exist_ok=True)

        with zipfile.ZipFile(jar_path) as archive:
            archive.extractall(expanded_jar)

        decompiler_jar_path = temp_root / "vineflower.jar"
        if not decompiler_jar_path.exists():
            import urllib.request
            url = "https://github.com/Vineflower/vineflower/releases/download/1.9.3/vineflower-1.9.3.jar"
            urllib.request.urlretrieve(url, decompiler_jar_path)

        command = ["java", "-jar", str(decompiler_jar_path), str(expanded_jar), str(java_output)]
        stream = os.environ.get("MODCOMPILER_DECOMPILE_STREAM", "").strip().lower() in {"1", "true", "yes"}
        if stream:
            process = subprocess.Popen(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
            )
            assert process.stdout is not None
            for line in process.stdout:
                print(f"[vineflower] {line.rstrip()}", file=sys.stderr)
            return_code = process.wait()
            if return_code != 0:
                raise ModCompilerError(f"Vineflower failed with exit code {return_code}")
        else:
            result = subprocess.run(command, capture_output=True, text=True)
            if result.stdout:
                for line in result.stdout.splitlines():
                    print(f"[vineflower] {line}", file=sys.stderr)
            if result.stderr:
                for line in result.stderr.splitlines():
                    print(f"[vineflower] {line}", file=sys.stderr)
            if result.returncode != 0:
                raise ModCompilerError(f"Vineflower failed with exit code {result.returncode}")

        for source_file in sorted(java_output.rglob("*")):
            if not source_file.is_file():
                continue
            relative = source_file.relative_to(java_output)
            destination = package_src / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            copy_file(source_file, destination)

        mod_info_txt = package_src.parent / "mod_info.txt"
        mod_info_txt.write_text(render_mod_info(metadata), encoding="utf-8")

        copy_tree(package_src.parent, output_dir)

        dump_sources = os.environ.get("MODCOMPILER_DECOMPILE_DUMP", "").strip().lower() in {"1", "true", "yes"}
        if dump_sources:
            print("DEBUG: Dumping decompiled sources (this can be very large)...", file=sys.stderr)
            if not package_src.exists():
                print("DEBUG: No decompiled sources found to dump.", file=sys.stderr)
            else:
                for source_file in sorted(package_src.rglob("*")):
                    if not source_file.is_file():
                        continue
                    if source_file.suffix.lower() not in {".java", ".kt"}:
                        continue
                    rel_path = source_file.relative_to(package_src)
                    print(f"----- BEGIN {rel_path} -----", file=sys.stderr)
                    try:
                        with source_file.open("r", encoding="utf-8", errors="replace") as handle:
                            for line in handle:
                                print(line.rstrip("\n"), file=sys.stderr)
                    except Exception as exc:
                        print(f"[dump error] {rel_path}: {exc}", file=sys.stderr)
                    print(f"----- END {rel_path} -----", file=sys.stderr)

        return DecompileResult(
            extracted_src=output_dir,
            metadata=metadata,
            status="success" if list(package_src.rglob("*.java")) or list(package_src.rglob("*.kt")) else "failed",
        )
    finally:
        safe_rmtree(temp_root)
