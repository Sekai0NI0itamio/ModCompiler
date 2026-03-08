from __future__ import annotations

import json
import os
import re
import shutil
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any


VERSION_KEYS = {"minecraft_version", "loader"}
MOD_REQUIRED_KEYS = {
    "mod_id",
    "name",
    "mod_version",
    "group",
    "entrypoint_class",
    "description",
    "authors",
    "license",
}
MOD_OPTIONAL_KEYS = {"homepage", "sources", "issues", "runtime_side"}
TEXT_FILE_SUFFIXES = {
    ".cfg",
    ".gradle",
    ".info",
    ".java",
    ".json",
    ".kt",
    ".mcmeta",
    ".properties",
    ".toml",
    ".txt",
    ".xml",
    ".yml",
    ".yaml",
}
IGNORED_ZIP_PREFIXES = ("__MACOSX/",)
IGNORED_ZIP_NAMES = {".DS_Store"}


class ModCompilerError(Exception):
    """Raised for user-facing build configuration errors."""


@dataclass(frozen=True)
class ModMetadata:
    mod_id: str
    name: str
    mod_version: str
    group: str
    entrypoint_class: str
    runtime_side: str
    description: str
    authors: list[str]
    license: str
    homepage: str | None
    sources: str | None
    issues: str | None


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def parse_key_value_file(path: Path, required_keys: set[str], optional_keys: set[str]) -> dict[str, str]:
    allowed_keys = required_keys | optional_keys
    parsed: dict[str, str] = {}
    for index, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = raw_line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if "=" not in raw_line:
            raise ModCompilerError(f"{path}: line {index} must use key=value format")
        key, value = raw_line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key:
            raise ModCompilerError(f"{path}: line {index} has an empty key")
        if key in parsed:
            raise ModCompilerError(f"{path}: duplicate key '{key}'")
        if key not in allowed_keys:
            raise ModCompilerError(f"{path}: unknown key '{key}'")
        parsed[key] = value
    missing = sorted(required_keys - parsed.keys())
    if missing:
        raise ModCompilerError(f"{path}: missing required keys: {', '.join(missing)}")
    return parsed


def load_mod_metadata(mod_txt: Path, version_txt: Path) -> tuple[ModMetadata, dict[str, str]]:
    mod_raw = parse_key_value_file(mod_txt, MOD_REQUIRED_KEYS, MOD_OPTIONAL_KEYS)
    version_raw = parse_key_value_file(version_txt, VERSION_KEYS, set())
    authors = [author.strip() for author in mod_raw["authors"].split(",") if author.strip()]
    if not authors:
        raise ModCompilerError(f"{mod_txt}: authors must contain at least one non-empty value")
    runtime_side = (mod_raw.get("runtime_side") or "both").strip().lower()
    if runtime_side not in {"both", "client", "server"}:
        raise ModCompilerError(
            f"{mod_txt}: runtime_side must be one of both, client, or server; got '{runtime_side}'"
        )
    metadata = ModMetadata(
        mod_id=mod_raw["mod_id"],
        name=mod_raw["name"],
        mod_version=mod_raw["mod_version"],
        group=mod_raw["group"],
        entrypoint_class=mod_raw["entrypoint_class"],
        runtime_side=runtime_side,
        description=mod_raw["description"],
        authors=authors,
        license=mod_raw["license"],
        homepage=mod_raw.get("homepage") or None,
        sources=mod_raw.get("sources") or None,
        issues=mod_raw.get("issues") or None,
    )
    return metadata, version_raw


def parse_version_tuple(version: str) -> tuple[int, int, int]:
    parts = version.split(".")
    if len(parts) not in {2, 3}:
        raise ModCompilerError(f"Unsupported version format '{version}'")
    if not all(part.isdigit() for part in parts):
        raise ModCompilerError(f"Unsupported version format '{version}'")
    numbers = [int(part) for part in parts]
    while len(numbers) < 3:
        numbers.append(0)
    return numbers[0], numbers[1], numbers[2]


def format_exact_version(major: int, minor: int, patch: int) -> str:
    if patch == 0:
        return f"{major}.{minor}"
    return f"{major}.{minor}.{patch}"


def expand_minecraft_version_spec(version_spec: str) -> list[str]:
    cleaned = version_spec.strip()
    if "-" not in cleaned:
        parse_version_tuple(cleaned)
        return [cleaned]

    lower_raw, upper_raw = [part.strip() for part in cleaned.split("-", 1)]
    lower = parse_version_tuple(lower_raw)
    upper = parse_version_tuple(upper_raw)
    if lower[:2] != upper[:2]:
        raise ModCompilerError(
            "Minecraft version ranges must stay within one major.minor family; "
            f"got '{version_spec}'"
        )
    if lower > upper:
        raise ModCompilerError(f"Minecraft version range is reversed: '{version_spec}'")
    major, minor = lower[0], lower[1]
    return [format_exact_version(major, minor, patch) for patch in range(lower[2], upper[2] + 1)]


def version_inclusive_between(version: str, lower: str, upper: str) -> bool:
    parsed = parse_version_tuple(version)
    return parse_version_tuple(lower) <= parsed <= parse_version_tuple(upper)


def resolve_range(manifest: dict[str, Any], minecraft_version: str) -> dict[str, Any]:
    matches = [
        range_entry
        for range_entry in manifest["ranges"]
        if version_inclusive_between(minecraft_version, range_entry["min_version"], range_entry["max_version"])
    ]
    if not matches:
        raise ModCompilerError(f"No version folder covers Minecraft {minecraft_version}")
    if len(matches) > 1:
        folders = ", ".join(entry["folder"] for entry in matches)
        raise ModCompilerError(f"Minecraft {minecraft_version} matches multiple folders: {folders}")
    return matches[0]


def get_range_by_folder(manifest: dict[str, Any], folder: str) -> dict[str, Any]:
    for range_entry in manifest["ranges"]:
        if range_entry["folder"] == folder:
            return range_entry
    raise ModCompilerError(f"Unknown range folder '{folder}'")


def resolve_java_version(loader_config: dict[str, Any], minecraft_version: str) -> int:
    for rule in loader_config["java_rules"]:
        if version_inclusive_between(minecraft_version, rule["min"], rule["max"]):
            return int(rule["version"])
    raise ModCompilerError(
        f"No Java version rule covers Minecraft {minecraft_version} for {loader_config['template_dir']}"
    )


def next_major_range(version: str) -> str:
    major, minor, _patch = parse_version_tuple(version)
    return f"[{version},{major}.{minor + 1})"


def make_slug(mod_id: str, loader: str, minecraft_version: str) -> str:
    base = f"{mod_id}-{loader}-{minecraft_version}".lower().replace(".", "-")
    return re.sub(r"[^a-z0-9_-]+", "-", base).strip("-")


def safe_rmtree(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)


def copy_tree(source: Path, destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    shutil.copytree(source, destination)


def copy_file(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def safe_extract_zip(zip_path: Path, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as archive:
        for member in archive.infolist():
            if not member.filename or member.filename.endswith("/"):
                continue
            if member.filename.startswith(IGNORED_ZIP_PREFIXES):
                continue
            if Path(member.filename).name in IGNORED_ZIP_NAMES:
                continue
            member_path = destination / member.filename
            resolved = member_path.resolve()
            if destination.resolve() not in resolved.parents and resolved != destination.resolve():
                raise ModCompilerError(f"{zip_path}: unsafe path '{member.filename}' in zip")
            member_path.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(member) as source_stream, member_path.open("wb") as target_stream:
                shutil.copyfileobj(source_stream, target_stream)


def discover_top_level_mod_dirs(extracted_root: Path) -> list[Path]:
    mod_dirs = []
    for child in sorted(extracted_root.iterdir()):
        if child.name in {"__MACOSX"}:
            continue
        if child.is_dir():
            mod_dirs.append(child)
            continue
        if child.name in IGNORED_ZIP_NAMES:
            continue
        raise ModCompilerError(
            f"Zip layout must contain one folder per mod at the top level; found file '{child.name}'"
        )
    if not mod_dirs:
        raise ModCompilerError("Zip archive does not contain any mod folders")
    return mod_dirs


def validate_mod_dir(mod_dir: Path) -> None:
    required_paths = [mod_dir / "src", mod_dir / "mod.txt", mod_dir / "version.txt"]
    missing = [path.name for path in required_paths if not path.exists()]
    if missing:
        raise ModCompilerError(f"{mod_dir.name}: missing required entries: {', '.join(missing)}")
    if not any((mod_dir / "src").rglob("*")):
        raise ModCompilerError(f"{mod_dir.name}: src/ must contain at least one file")


def build_prepare_plan(zip_path: Path, prepared_root: Path, manifest: dict[str, Any]) -> dict[str, Any]:
    extracted_root = prepared_root / "_extracted"
    safe_rmtree(extracted_root)
    safe_extract_zip(zip_path, extracted_root)
    plan_mods: list[dict[str, Any]] = []
    seen_slugs: set[str] = set()
    for mod_dir in discover_top_level_mod_dirs(extracted_root):
        validate_mod_dir(mod_dir)
        metadata, version_info = load_mod_metadata(mod_dir / "mod.txt", mod_dir / "version.txt")
        version_spec = version_info["minecraft_version"].strip()
        exact_versions = expand_minecraft_version_spec(version_spec)
        loader = version_info["loader"].strip().lower()
        if loader not in {"fabric", "forge"}:
            raise ModCompilerError(f"{mod_dir.name}: unsupported loader '{loader}'")
        for exact_version in exact_versions:
            slug = make_slug(metadata.mod_id, loader, exact_version)
            if slug in seen_slugs:
                raise ModCompilerError(f"Duplicate mod build slug '{slug}' in archive")
            seen_slugs.add(slug)
            destination = prepared_root / "mods" / slug
            safe_rmtree(destination)
            copy_tree(mod_dir, destination)

            warnings: list[str] = []
            precheck_error: str | None = None
            range_folder = "-"
            java_version: int | None = None
            template_dir = ""
            jar_glob = ""
            build_command: list[str] = []
            anchor_version = ""
            exact_dependency_mode = ""

            try:
                resolved_range = resolve_range(manifest, exact_version)
                range_folder = resolved_range["folder"]
                if loader not in resolved_range["loaders"]:
                    raise ModCompilerError(
                        f"{mod_dir.name}: loader '{loader}' is not supported in folder {resolved_range['folder']}"
                    )
                loader_config = resolved_range["loaders"][loader]
                java_version = resolve_java_version(loader_config, exact_version)
                template_dir = loader_config["template_dir"]
                jar_glob = loader_config["jar_glob"]
                build_command = loader_config["build_command"]
                anchor_version = loader_config["anchor_version"]
                exact_dependency_mode = loader_config["exact_dependency_mode"]
                if loader_config["exact_dependency_mode"] == "anchor_only" and exact_version != loader_config["anchor_version"]:
                    warnings.append(
                        "Template dependency versions are still anchored to "
                        f"{loader_config['anchor_version']} for this loader; update version-manifest.json before production use."
                    )
            except ModCompilerError as error:
                if len(exact_versions) == 1:
                    raise
                precheck_error = str(error)
                warnings.append(precheck_error)

            plan_mods.append(
                {
                    "slug": slug,
                    "folder_name_in_zip": mod_dir.name,
                    "requested_version_spec": version_spec,
                    "minecraft_version": exact_version,
                    "loader": loader,
                    "range_folder": range_folder,
                    "java_version": java_version,
                    "metadata": {
                        "mod_id": metadata.mod_id,
                        "name": metadata.name,
                        "mod_version": metadata.mod_version,
                        "group": metadata.group,
                        "entrypoint_class": metadata.entrypoint_class,
                        "runtime_side": metadata.runtime_side,
                        "description": metadata.description,
                        "authors": metadata.authors,
                        "license": metadata.license,
                        "homepage": metadata.homepage,
                        "sources": metadata.sources,
                        "issues": metadata.issues,
                    },
                    "mod_dir": f"mods/{slug}",
                    "template_dir": template_dir,
                    "jar_glob": jar_glob,
                    "build_command": build_command,
                    "anchor_version": anchor_version,
                    "exact_dependency_mode": exact_dependency_mode,
                    "precheck_error": precheck_error,
                    "warnings": warnings,
                }
            )
    safe_rmtree(extracted_root)
    return {
        "schema_version": 1,
        "zip_path": str(zip_path),
        "mods": plan_mods,
    }


def find_plan_entry(plan: dict[str, Any], slug: str) -> dict[str, Any]:
    for mod in plan["mods"]:
        if mod["slug"] == slug:
            return mod
    raise ModCompilerError(f"Could not find prepared mod '{slug}'")


def load_plan(path: Path) -> dict[str, Any]:
    return load_json(path)


def java_home_for_version(java_version: int, environ: dict[str, str]) -> str:
    candidates = [
        f"JAVA_HOME_{java_version}_X64",
        f"JAVA_HOME_{java_version}",
    ]
    for candidate in candidates:
        value = environ.get(candidate)
        if value:
            return value
    current = environ.get("JAVA_HOME")
    if current:
        return current
    raise ModCompilerError(
        f"No JAVA_HOME was found for Java {java_version}. Install it in the workflow before building."
    )


def select_primary_jars(paths: list[Path]) -> list[Path]:
    jars = [
        path
        for path in sorted(paths)
        if path.suffix == ".jar"
        and "-sources" not in path.name
        and "-javadoc" not in path.name
        and "-dev" not in path.name
    ]
    return jars


def find_built_jars(workspace: Path, jar_glob: str) -> list[Path]:
    return select_primary_jars(list(workspace.glob(jar_glob)))


def render_summary_markdown(results: list[dict[str, Any]]) -> str:
    header = [
        "# Build Summary",
        "",
        "| Mod ID | Name | Minecraft | Folder | Loader | Status | Jar | Log |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    rows = []
    for result in results:
        jar_name = ", ".join(result.get("jar_names", [])) or "-"
        rows.append(
            "| {mod_id} | {name} | {minecraft_version} | {range_folder} | {loader} | {status} | {jar_name} | {log_relpath} |".format(
                mod_id=result["metadata"]["mod_id"],
                name=result["metadata"]["name"],
                minecraft_version=result["minecraft_version"],
                range_folder=result["range_folder"],
                loader=result["loader"],
                status=result["status"],
                jar_name=jar_name,
                log_relpath=result.get("log_relpath", "-"),
            )
        )
    return "\n".join(header + rows) + "\n"


def stage_tree_copy(source: Path, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    if source.is_dir():
        copy_tree(source, destination)
        return
    copy_file(source, destination)


def escape_json_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def format_java_constant(java_version: int) -> str:
    if java_version == 8:
        return "JavaVersion.VERSION_1_8"
    return f"JavaVersion.VERSION_{java_version}"


def replace_text(path: Path, matcher: str, replacement: str) -> None:
    text = path.read_text(encoding="utf-8")
    updated = re.sub(matcher, replacement, text, flags=re.MULTILINE)
    path.write_text(updated, encoding="utf-8")


def update_key_value_assignment(path: Path, key: str, value: str) -> None:
    text = path.read_text(encoding="utf-8")
    pattern = re.compile(rf"^(\s*{re.escape(key)}\s*=\s*).*$", re.MULTILINE)
    if pattern.search(text):
        updated = pattern.sub(lambda match: f"{match.group(1)}{value}", text)
    else:
        separator = "" if text.endswith("\n") else "\n"
        updated = f"{text}{separator}{key}={value}\n"
    path.write_text(updated, encoding="utf-8")


def update_settings_gradle(path: Path, project_name: str) -> None:
    line = f'rootProject.name = "{project_name}"\n'
    if not path.exists():
        path.write_text(line, encoding="utf-8")
        return
    text = path.read_text(encoding="utf-8")
    if "rootProject.name" in text:
        updated = re.sub(r'rootProject\.name\s*=.*', line.strip(), text)
    else:
        separator = "" if text.endswith("\n") else "\n"
        updated = f"{text}{separator}{line}"
    path.write_text(updated, encoding="utf-8")


def replace_known_placeholders_in_entrypoint(entrypoint_file: Path, metadata: ModMetadata) -> None:
    if not entrypoint_file.exists():
        return
    text = entrypoint_file.read_text(encoding="utf-8")
    replacements = [
        (r'(@Mod\(")[^"]+("\))', metadata.mod_id),
        (r'(MODID\s*=\s*")[^"]+(")', metadata.mod_id),
        (r'(MOD_ID\s*=\s*")[^"]+(")', metadata.mod_id),
        (r'(NAME\s*=\s*")[^"]+(")', metadata.name),
        (r'(VERSION\s*=\s*")[^"]+(")', metadata.mod_version),
    ]
    updated = text
    for pattern, replacement in replacements:
        updated = re.sub(pattern, lambda match, value=replacement: f"{match.group(1)}{value}{match.group(2)}", updated)
    entrypoint_file.write_text(updated, encoding="utf-8")


def entrypoint_source_candidates(source_root: Path, entrypoint_class: str) -> list[Path]:
    package_parts = entrypoint_class.split(".")
    file_parts = package_parts[:-1]
    class_name = package_parts[-1]
    candidates = []
    for stem in ("src/main/java", "src/main/kotlin", "src/client/java", "src/client/kotlin"):
        for suffix in (".java", ".kt"):
            candidates.append(source_root / stem / Path(*file_parts) / f"{class_name}{suffix}")
    return candidates


def ensure_parent(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def find_text_files(root: Path) -> list[Path]:
    return [
        path
        for path in root.rglob("*")
        if path.is_file() and path.suffix.lower() in TEXT_FILE_SUFFIXES
    ]


def sanitize_env_path(java_home: str, existing_path: str | None) -> str:
    java_bin = str(Path(java_home) / "bin")
    if existing_path:
        return os.pathsep.join([java_bin, existing_path])
    return java_bin
