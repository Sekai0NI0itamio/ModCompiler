from __future__ import annotations

import argparse
import json
import re
import shutil
from pathlib import Path
from typing import Any

from modcompiler.common import (
    ModCompilerError,
    ModMetadata,
    copy_tree,
    entrypoint_source_candidates,
    ensure_parent,
    find_text_files,
    format_java_constant,
    get_range_by_folder,
    load_json,
    parse_version_tuple,
    next_major_range,
    replace_known_placeholders_in_entrypoint,
    resolve_dependency_overrides,
    resolve_java_version,
    validate_loader_exact_support,
    update_key_value_assignment,
    update_settings_gradle,
    write_json,
)


def run_range_adapter(range_folder: str, argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--loader", required=True)
    parser.add_argument("--metadata-json", required=True)
    parser.add_argument("--source-dir", required=True)
    parser.add_argument("--template-workspace", required=True)
    parser.add_argument("--minecraft-version", required=True)
    parser.add_argument("--manifest", required=True)
    args = parser.parse_args(argv)

    manifest = load_json(Path(args.manifest))
    metadata_dict = load_json(Path(args.metadata_json))
    metadata = ModMetadata(
        mod_id=metadata_dict["mod_id"],
        name=metadata_dict["name"],
        mod_version=metadata_dict["mod_version"],
        group=metadata_dict["group"],
        entrypoint_class=metadata_dict["entrypoint_class"],
        runtime_side=metadata_dict.get("runtime_side", "both"),
        description=metadata_dict["description"],
        authors=metadata_dict["authors"],
        license=metadata_dict["license"],
        homepage=metadata_dict.get("homepage"),
        sources=metadata_dict.get("sources"),
        issues=metadata_dict.get("issues"),
    )
    prepare_workspace(
        manifest=manifest,
        range_folder=range_folder,
        loader=args.loader,
        source_dir=Path(args.source_dir),
        workspace=Path(args.template_workspace),
        minecraft_version=args.minecraft_version,
        metadata=metadata,
    )
    return 0


def prepare_workspace(
    *,
    manifest: dict[str, Any],
    range_folder: str,
    loader: str,
    source_dir: Path,
    workspace: Path,
    minecraft_version: str,
    metadata: ModMetadata,
) -> None:
    range_entry = get_range_by_folder(manifest, range_folder)
    loader_config = range_entry["loaders"][loader]
    validate_loader_exact_support(loader_config, minecraft_version)
    adapter_family = loader_config["adapter_family"]
    java_version = resolve_java_version(loader_config, minecraft_version)
    dependency_overrides = resolve_dependency_overrides(loader_config, minecraft_version)

    if adapter_family.startswith("fabric"):
        if (workspace / "src").exists():
            shutil.rmtree(workspace / "src")
        copy_tree(source_dir, workspace / "src")
    else:
        for relative in ("src/main/java", "src/main/kotlin", "src/client/java", "src/client/kotlin"):
            candidate = workspace / relative
            if candidate.exists():
                shutil.rmtree(candidate)
        shutil.copytree(source_dir, workspace / "src", dirs_exist_ok=True)
    update_settings_gradle(workspace / "settings.gradle", metadata.mod_id)

    for candidate in entrypoint_source_candidates(workspace, metadata.entrypoint_class):
        replace_known_placeholders_in_entrypoint(candidate, metadata)

    if adapter_family.startswith("fabric"):
        apply_fabric_adapter(
            workspace=workspace,
            metadata=metadata,
            minecraft_version=minecraft_version,
            java_version=java_version,
            dependency_overrides=dependency_overrides,
        )
    elif adapter_family.startswith("forge_legacy"):
        apply_legacy_forge_adapter(
            workspace=workspace,
            metadata=metadata,
            minecraft_version=minecraft_version,
        )
    elif adapter_family.startswith("forge_mods_toml"):
        apply_mods_toml_forge_adapter(
            workspace=workspace,
            metadata=metadata,
            minecraft_version=minecraft_version,
            java_version=java_version,
            dependency_overrides=dependency_overrides,
        )
    elif adapter_family.startswith("neoforge_mods_toml"):
        apply_neoforge_adapter(
            workspace=workspace,
            metadata=metadata,
            minecraft_version=minecraft_version,
            java_version=java_version,
            dependency_overrides=dependency_overrides,
        )
    else:
        raise ModCompilerError(f"Unsupported adapter family '{adapter_family}'")


def apply_fabric_adapter(
    *,
    workspace: Path,
    metadata: ModMetadata,
    minecraft_version: str,
    java_version: int,
    dependency_overrides: dict[str, str],
) -> None:
    gradle_properties = workspace / "gradle.properties"
    build_gradle = workspace / "build.gradle"

    update_key_value_assignment(gradle_properties, "minecraft_version", minecraft_version)
    update_key_value_assignment(gradle_properties, "mod_version", metadata.mod_version)
    update_key_value_assignment(gradle_properties, "maven_group", metadata.group)
    update_key_value_assignment(gradle_properties, "archives_base_name", metadata.mod_id)
    for key, value in dependency_overrides.items():
        update_key_value_assignment(gradle_properties, key, value)

    if build_gradle.exists():
        text = build_gradle.read_text(encoding="utf-8")
        text = re.sub(r"JavaVersion\.VERSION_[0-9_]+", format_java_constant(java_version), text)
        text = re.sub(r"(it\.options\.release\s*=\s*)\d+", rf"\g<1>{java_version}", text)
        build_gradle.write_text(text, encoding="utf-8")

    metadata_path = workspace / "src/main/resources/fabric.mod.json"
    ensure_parent(metadata_path)
    metadata_path.write_text(
        json.dumps(build_fabric_metadata(metadata, minecraft_version, java_version, workspace), indent=2) + "\n",
        encoding="utf-8",
    )


def build_fabric_metadata(
    metadata: ModMetadata,
    minecraft_version: str,
    java_version: int,
    workspace: Path,
) -> dict[str, Any]:
    environment = "*" if metadata.runtime_side == "both" else metadata.runtime_side
    entrypoint_key = {
        "both": "main",
        "client": "client",
        "server": "server",
    }[metadata.runtime_side]
    payload: dict[str, Any] = {
        "schemaVersion": 1,
        "id": metadata.mod_id,
        "version": "${version}",
        "name": metadata.name,
        "description": metadata.description,
        "authors": metadata.authors,
        "license": metadata.license,
        "environment": environment,
        "entrypoints": {entrypoint_key: [metadata.entrypoint_class]},
        "depends": {
            "fabricloader": "*",
            "minecraft": minecraft_version,
            "java": f">={java_version}",
            "fabric-api": "*",
        },
    }
    contact = {key: value for key, value in {
        "homepage": metadata.homepage,
        "sources": metadata.sources,
        "issues": metadata.issues,
    }.items() if value}
    if contact:
        payload["contact"] = contact

    main_resources = workspace / "src/main/resources"
    client_resources = workspace / "src/client/resources"
    mixins: list[Any] = []
    for path in sorted(main_resources.rglob("*.mixins.json")) if main_resources.exists() else []:
        mixins.append(path.name)
    for path in sorted(client_resources.rglob("*.mixins.json")) if client_resources.exists() else []:
        mixins.append({"config": path.name, "environment": "client"})
    if mixins:
        payload["mixins"] = mixins

    icon_path = main_resources / "assets" / metadata.mod_id / "icon.png"
    if icon_path.exists():
        payload["icon"] = f"assets/{metadata.mod_id}/icon.png"
    return payload


def apply_legacy_forge_adapter(
    *,
    workspace: Path,
    metadata: ModMetadata,
    minecraft_version: str,
) -> None:
    build_gradle = workspace / "build.gradle"
    if build_gradle.exists():
        text = build_gradle.read_text(encoding="utf-8")
        text = re.sub(r'(?m)^version\s*=\s*["\'][^"\']+["\']', f'version = "{metadata.mod_version}"', text)
        text = re.sub(r'(?m)^group\s*=\s*["\'][^"\']+["\']', f'group = "{metadata.group}"', text)
        text = re.sub(
            r'(?m)^archivesBaseName\s*=\s*["\'][^"\']+["\']',
            f'archivesBaseName = "{metadata.mod_id}"',
            text,
        )
        build_gradle.write_text(text, encoding="utf-8")

    mcmod_path = workspace / "src/main/resources/mcmod.info"
    ensure_parent(mcmod_path)
    mcmod_payload = [
        {
            "modid": metadata.mod_id,
            "name": metadata.name,
            "description": metadata.description,
            "version": metadata.mod_version,
            "mcversion": minecraft_version,
            "url": metadata.homepage or "",
            "updateUrl": metadata.issues or "",
            "authorList": metadata.authors,
            "credits": "",
            "logoFile": "",
            "screenshots": [],
            "dependencies": [],
        }
    ]
    mcmod_path.write_text(json.dumps(mcmod_payload, indent=2) + "\n", encoding="utf-8")


def apply_mods_toml_forge_adapter(
    *,
    workspace: Path,
    metadata: ModMetadata,
    minecraft_version: str,
    java_version: int,
    dependency_overrides: dict[str, str],
) -> None:
    gradle_properties = workspace / "gradle.properties"
    build_gradle = workspace / "build.gradle"
    effective_minecraft_version = dependency_overrides.get("minecraft_version", minecraft_version)
    mapping_version = dependency_overrides.get("mapping_version", effective_minecraft_version)
    forge_version = dependency_overrides.get("forge_version")
    if gradle_properties.exists():
        update_key_value_assignment(gradle_properties, "minecraft_version", effective_minecraft_version)
        update_key_value_assignment(gradle_properties, "minecraft_version_range", next_major_range(effective_minecraft_version))
        update_key_value_assignment(gradle_properties, "mapping_version", mapping_version)
        update_key_value_assignment(gradle_properties, "mod_id", metadata.mod_id)
        update_key_value_assignment(gradle_properties, "mod_name", metadata.name)
        update_key_value_assignment(gradle_properties, "mod_license", metadata.license)
        update_key_value_assignment(gradle_properties, "mod_version", metadata.mod_version)
        update_key_value_assignment(gradle_properties, "mod_group_id", metadata.group)
        update_key_value_assignment(gradle_properties, "mod_authors", ", ".join(metadata.authors))
        update_key_value_assignment(
            gradle_properties,
            "mod_description",
            metadata.description.replace("\n", "\\n"),
        )
        for key, value in dependency_overrides.items():
            update_key_value_assignment(gradle_properties, key, value)
    if build_gradle.exists():
        text = build_gradle.read_text(encoding="utf-8")
        text = re.sub(
            r"(JavaLanguageVersion\.of\()\d+(\))",
            rf"\g<1>{java_version}\2",
            text,
        )
        text = re.sub(r"(?m)^version\s*=\s*['\"][^'\"]+['\"]", f"version = '{metadata.mod_version}'", text)
        text = re.sub(r"(?m)^group\s*=\s*['\"][^'\"]+['\"]", f"group = '{metadata.group}'", text)
        text = re.sub(
            r"(?m)^\s*archivesBaseName\s*=\s*['\"][^'\"]+['\"]",
            f"archivesBaseName = '{metadata.mod_id}'",
            text,
        )
        text = re.sub(
            r"(?m)^\s*archivesName\s*=\s*['\"][^'\"]+['\"]",
            f"archivesName = '{metadata.mod_id}'",
            text,
        )
        text = re.sub(
            r"(?m)^\s*annotationProcessor\s+['\"]net\.minecraftforge:eventbus-validator:[^'\"]+['\"]\s*\n",
            "",
            text,
        )
        text = re.sub(
            r"(mappings channel:\s*'official',\s*version:\s*)'[^']+'",
            rf"\1'{mapping_version}'",
            text,
        )
        if forge_version:
            text = re.sub(
                r"(minecraft\.dependency\('net\.minecraftforge:forge:)[^']+('\))",
                lambda match: f"{match.group(1)}{effective_minecraft_version}-{forge_version}{match.group(2)}",
                text,
            )
        build_gradle.write_text(text, encoding="utf-8")

    mods_toml = workspace / "src/main/resources/META-INF/mods.toml"
    ensure_parent(mods_toml)
    mods_toml.write_text(build_mods_toml(metadata, effective_minecraft_version), encoding="utf-8")

    pack_mcmeta = workspace / "src/main/resources/pack.mcmeta"
    ensure_parent(pack_mcmeta)
    pack_payload = build_pack_mcmeta(metadata.mod_id, effective_minecraft_version)
    pack_mcmeta.write_text(json.dumps(pack_payload, indent=2) + "\n", encoding="utf-8")


def neoforge_version_base(minecraft_version: str) -> str:
    major, minor, patch = parse_version_tuple(minecraft_version)
    if major == 1:
        return f"{minor}.{patch}"
    return f"{major}.{minor}.{patch}"


def apply_neoforge_adapter(
    *,
    workspace: Path,
    metadata: ModMetadata,
    minecraft_version: str,
    java_version: int,
    dependency_overrides: dict[str, str],
) -> None:
    gradle_properties = workspace / "gradle.properties"
    build_gradle = workspace / "build.gradle"

    effective_minecraft_version = dependency_overrides.get("minecraft_version", minecraft_version)
    base_version = neoforge_version_base(effective_minecraft_version)
    neo_version = dependency_overrides.get("neo_version", f"{base_version}.+")
    neo_version_range = dependency_overrides.get("neo_version_range", f"[{base_version},)")

    if gradle_properties.exists():
        update_key_value_assignment(gradle_properties, "minecraft_version", effective_minecraft_version)
        update_key_value_assignment(gradle_properties, "minecraft_version_range", next_major_range(effective_minecraft_version))
        update_key_value_assignment(gradle_properties, "neo_version", neo_version)
        update_key_value_assignment(gradle_properties, "neo_version_range", neo_version_range)
        update_key_value_assignment(gradle_properties, "mod_id", metadata.mod_id)
        update_key_value_assignment(gradle_properties, "mod_name", metadata.name)
        update_key_value_assignment(gradle_properties, "mod_license", metadata.license)
        update_key_value_assignment(gradle_properties, "mod_version", metadata.mod_version)
        update_key_value_assignment(gradle_properties, "mod_group_id", metadata.group)
        update_key_value_assignment(gradle_properties, "mod_authors", ", ".join(metadata.authors))
        update_key_value_assignment(
            gradle_properties,
            "mod_description",
            metadata.description.replace("\n", "\\n"),
        )
        for key, value in dependency_overrides.items():
            update_key_value_assignment(gradle_properties, key, value)

    if build_gradle.exists():
        text = build_gradle.read_text(encoding="utf-8")
        text = re.sub(
            r"(JavaLanguageVersion\.of\()\d+(\))",
            rf"\g<1>{java_version}\2",
            text,
        )
        text = re.sub(r"(?m)^version\s*=\s*['\"][^'\"]+['\"]", f"version = '{metadata.mod_version}'", text)
        text = re.sub(r"(?m)^group\s*=\s*['\"][^'\"]+['\"]", f"group = '{metadata.group}'", text)
        text = re.sub(
            r"(?m)^\s*archivesName\s*=\s*['\"][^'\"]+['\"]",
            f"archivesName = '{metadata.mod_id}'",
            text,
        )
        build_gradle.write_text(text, encoding="utf-8")

    mods_toml = workspace / "src/main/resources/META-INF/neoforge.mods.toml"
    ensure_parent(mods_toml)
    mods_toml.write_text(
        build_neoforge_mods_toml(metadata, effective_minecraft_version, neo_version_range),
        encoding="utf-8",
    )

    pack_mcmeta = workspace / "src/main/resources/pack.mcmeta"
    ensure_parent(pack_mcmeta)
    pack_payload = load_or_build_pack_mcmeta(pack_mcmeta, metadata.mod_id, effective_minecraft_version)
    pack_mcmeta.write_text(json.dumps(pack_payload, indent=2) + "\n", encoding="utf-8")


def build_pack_mcmeta(mod_id: str, minecraft_version: str) -> dict[str, Any]:
    if minecraft_version.startswith("1.21"):
        return {
            "pack": {
                "description": f"{mod_id} resources",
                "max_format": 94,
                "min_format": [94, 1],
            }
        }
    if minecraft_version in {"1.20.5", "1.20.6"}:
        return {
            "pack": {
                "description": f"{mod_id} resources",
                "pack_format": 32,
            }
        }
    if minecraft_version.startswith("1.20"):
        return {
            "pack": {
                "description": {
                    "text": f"{mod_id} resources",
                },
                "pack_format": 15,
            }
        }
    if minecraft_version.startswith("1.19"):
        return {
            "pack": {
                "description": {
                    "text": f"{mod_id} resources",
                },
                "forge:server_data_pack_format": 12,
                "pack_format": 13,
            }
        }
    if minecraft_version.startswith("1.18"):
        return {
            "pack": {
                "description": f"{mod_id} resources",
                "pack_format": 9,
                "forge:resource_pack_format": 8,
                "forge:data_pack_format": 9,
            }
        }
    if minecraft_version.startswith("1.17"):
        return {
            "pack": {
                "description": f"{mod_id} resources",
                "pack_format": 7,
            }
        }
    if minecraft_version.startswith("1.16"):
        return {
            "pack": {
                "description": f"{mod_id} resources",
                "pack_format": 6,
            }
        }
    return {
        "pack": {
            "description": f"{mod_id} resources",
            "pack_format": 3,
        }
    }


def load_or_build_pack_mcmeta(path: Path, mod_id: str, minecraft_version: str) -> dict[str, Any]:
    if path.exists():
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            payload = build_pack_mcmeta(mod_id, minecraft_version)
    else:
        payload = build_pack_mcmeta(mod_id, minecraft_version)
    pack_section = payload.setdefault("pack", {})
    description = pack_section.get("description")
    if isinstance(description, dict):
        pack_section["description"] = {"text": f"{mod_id} resources"}
    else:
        pack_section["description"] = f"{mod_id} resources"
    return payload


def toml_quote(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{escaped}"'


def toml_multiline(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace('"""', '\\"\\"\\"')
    return f'"""{escaped}"""'


def build_mods_toml(metadata: ModMetadata, minecraft_version: str) -> str:
    runtime_lines = ""
    if metadata.runtime_side == "client":
        runtime_lines = 'clientSideOnly=true\n' 'displayTest="IGNORE_ALL_VERSION"\n'
    elif metadata.runtime_side == "server":
        runtime_lines = 'displayTest="IGNORE_SERVER_VERSION"\n'
    return (
        'modLoader="javafml"\n'
        'loaderVersion="*"\n'
        f'license={toml_quote(metadata.license)}\n'
        f"{runtime_lines}"
        "\n"
        "[[mods]]\n"
        f'modId={toml_quote(metadata.mod_id)}\n'
        f'version={toml_quote(metadata.mod_version)}\n'
        f'displayName={toml_quote(metadata.name)}\n'
        f'authors={toml_quote(", ".join(metadata.authors))}\n'
        f"description={toml_multiline(metadata.description)}\n"
        "\n"
        f"[[dependencies.{metadata.mod_id}]]\n"
        'modId="forge"\n'
        "mandatory=true\n"
        'versionRange="*"\n'
        'ordering="NONE"\n'
        'side="BOTH"\n'
        "\n"
        f"[[dependencies.{metadata.mod_id}]]\n"
        'modId="minecraft"\n'
        "mandatory=true\n"
        f"versionRange={toml_quote(next_major_range(minecraft_version))}\n"
        'ordering="NONE"\n'
        'side="BOTH"\n'
    )


def build_neoforge_mods_toml(metadata: ModMetadata, minecraft_version: str, neo_version_range: str) -> str:
    runtime_lines = ""
    if metadata.runtime_side == "client":
        runtime_lines = 'clientSideOnly=true\n' 'displayTest="IGNORE_ALL_VERSION"\n'
    elif metadata.runtime_side == "server":
        runtime_lines = 'displayTest="IGNORE_SERVER_VERSION"\n'
    return (
        'modLoader="javafml"\n'
        'loaderVersion="[1,)"\n'
        f'license={toml_quote(metadata.license)}\n'
        f"{runtime_lines}"
        "\n"
        "[[mods]]\n"
        f'modId={toml_quote(metadata.mod_id)}\n'
        f'version={toml_quote(metadata.mod_version)}\n'
        f'displayName={toml_quote(metadata.name)}\n'
        f'authors={toml_quote(", ".join(metadata.authors))}\n'
        f"description={toml_multiline(metadata.description)}\n"
        "\n"
        f"[[dependencies.{metadata.mod_id}]]\n"
        'modId="neoforge"\n'
        "mandatory=true\n"
        f"versionRange={toml_quote(neo_version_range)}\n"
        'ordering="NONE"\n'
        'side="BOTH"\n'
        "\n"
        f"[[dependencies.{metadata.mod_id}]]\n"
        'modId="minecraft"\n'
        "mandatory=true\n"
        f"versionRange={toml_quote(next_major_range(minecraft_version))}\n"
        'ordering="NONE"\n'
        'side="BOTH"\n'
    )
