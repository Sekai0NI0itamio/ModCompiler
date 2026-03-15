from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import traceback
import zipfile
from pathlib import Path
from typing import Any

from modcompiler.common import (
    ModCompilerError,
    build_prepare_plan,
    copy_file,
    copy_tree,
    find_built_jars,
    find_plan_entry,
    jar_contains_classes,
    java_home_for_version,
    load_json,
    load_plan,
    render_summary_markdown,
    sanitize_env_path,
    safe_rmtree,
    write_json,
)
from modcompiler.decompile import command_decompile_jar
from modcompiler.modrinth import command_publish_modrinth, select_primary_jar
from modcompiler.auto_update import command_auto_update_decompose, command_ai_rebuild

try:
    import tomllib
except ModuleNotFoundError:  # pragma: no cover - python<3.11 fallback
    import tomli as tomllib


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    prepare_parser = subparsers.add_parser("prepare")
    prepare_parser.add_argument("--zip-path", required=True)
    prepare_parser.add_argument("--manifest", required=True)
    prepare_parser.add_argument("--output-dir", required=True)

    build_parser = subparsers.add_parser("build-one")
    build_parser.add_argument("--plan", required=True)
    build_parser.add_argument("--manifest", required=True)
    build_parser.add_argument("--prepared-dir", required=True)
    build_parser.add_argument("--slug", required=True)
    build_parser.add_argument("--artifact-dir", required=True)

    bundle_parser = subparsers.add_parser("bundle")
    bundle_parser.add_argument("--artifacts-root", required=True)
    bundle_parser.add_argument("--output-dir", required=True)

    decompile_parser = subparsers.add_parser("decompile-jar")
    decompile_parser.add_argument("--jar-path", required=True)
    decompile_parser.add_argument("--manifest", required=True)
    decompile_parser.add_argument("--decompiler-jar", required=True)
    decompile_parser.add_argument("--artifact-dir", required=True)

    publish_parser = subparsers.add_parser("publish-modrinth")
    publish_parser.add_argument("--artifacts-root", required=True)
    publish_parser.add_argument("--project", required=True)
    publish_parser.add_argument("--artifact-dir", required=True)

    auto_update_decompose_parser = subparsers.add_parser("auto-update-decompose")
    auto_update_decompose_parser.add_argument("--mod-jar-path", required=False, default="")
    auto_update_decompose_parser.add_argument("--modrinth-project-url", required=False, default="")
    auto_update_decompose_parser.add_argument("--mod-description", required=False, default="")
    auto_update_decompose_parser.add_argument("--auto-fetch-modrinth", required=False, default="true")
    auto_update_decompose_parser.add_argument("--auto-fix-corrupted", required=False, default="false")
    auto_update_decompose_parser.add_argument("--auto-fix-only", required=False, default="false")
    auto_update_decompose_parser.add_argument("--auto-fix-corrupted-downloads-dir", required=False, default="")
    auto_update_decompose_parser.add_argument("--auto-fix-corrupted-decompiled-dir", required=False, default="")
    auto_update_decompose_parser.add_argument("--auto-fix-corrupted-report-dir", required=False, default="")
    auto_update_decompose_parser.add_argument("--only-target", required=False, default="")
    auto_update_decompose_parser.add_argument("--plan-only", required=False, default="false")
    auto_update_decompose_parser.add_argument("--reuse-decompiled-dir", required=False, default="")
    auto_update_decompose_parser.add_argument("--version-range", required=False, default="all")
    auto_update_decompose_parser.add_argument("--update-mode", required=False, default="all-versions")
    auto_update_decompose_parser.add_argument("--publish-mode", required=False, default="bundle-only")
    auto_update_decompose_parser.add_argument("--manifest", required=True)
    auto_update_decompose_parser.add_argument("--output-dir", required=True)

    ai_rebuild_parser = subparsers.add_parser("ai-rebuild")
    ai_rebuild_parser.add_argument("--version-dir", required=True)
    ai_rebuild_parser.add_argument("--output-dir", required=True)
    ai_rebuild_parser.add_argument("--artifact-dir", required=True)

    args = parser.parse_args(argv)
    try:
        if args.command == "prepare":
            return command_prepare(args)
        if args.command == "build-one":
            return command_build_one(args)
        if args.command == "bundle":
            return command_bundle(args)
        if args.command == "decompile-jar":
            return command_decompile_jar(args)
        if args.command == "publish-modrinth":
            return command_publish_modrinth(args)
        if args.command == "auto-update-decompose":
            return command_auto_update_decompose(args)
        if args.command == "ai-rebuild":
            return command_ai_rebuild(args)
    except ModCompilerError as error:
        print(str(error), file=sys.stderr)
        return 1
    return 1


def command_prepare(args: argparse.Namespace) -> int:
    manifest = load_json(Path(args.manifest))
    zip_path = Path(args.zip_path)
    if not zip_path.is_absolute():
        zip_path = Path.cwd() / zip_path
    if not zip_path.exists():
        raise ModCompilerError(f"Zip path does not exist: {zip_path}")

    output_dir = Path(args.output_dir)
    safe_rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    plan = build_prepare_plan(zip_path, output_dir, manifest)
    write_json(output_dir / "build-plan.json", plan)
    matrix = {"include": [{"slug": mod["slug"]} for mod in plan["mods"]]}
    write_json(output_dir / "matrix.json", matrix)
    summary = render_summary_markdown(
        [
            {
                **mod,
                "status": "prepared",
                "log_relpath": "-",
                "jar_names": [],
            }
            for mod in plan["mods"]
        ]
    )
    (output_dir / "PREPARE_SUMMARY.md").write_text(summary, encoding="utf-8")
    print(json.dumps(matrix, separators=(",", ":")))
    return 0


def command_build_one(args: argparse.Namespace) -> int:
    plan = load_plan(Path(args.plan))
    manifest = load_json(Path(args.manifest))
    mod = find_plan_entry(plan, args.slug)
    prepared_dir = Path(args.prepared_dir)
    artifact_dir = Path(args.artifact_dir)
    safe_rmtree(artifact_dir)
    artifact_dir.mkdir(parents=True, exist_ok=True)

    result: dict[str, Any] = {
        "slug": mod["slug"],
        "loader": mod["loader"],
        "requested_version_spec": mod.get("requested_version_spec", mod["minecraft_version"]),
        "minecraft_version": mod["minecraft_version"],
        "range_folder": mod["range_folder"],
        "metadata": mod["metadata"],
        "java_version": mod["java_version"],
        "warnings": list(mod["warnings"]),
        "status": "failed",
        "jar_names": [],
        "log_relpath": "build.log",
    }
    log_path = artifact_dir / "build.log"
    metadata_path = artifact_dir / "input-metadata.json"
    write_json(metadata_path, mod)

    build_exit = 1
    precheck_error = mod.get("precheck_error")
    if precheck_error:
        log_path.write_text(
            "Pre-build validation failed.\n\n"
            f"Requested version spec: {mod.get('requested_version_spec', mod['minecraft_version'])}\n"
            f"Exact build target: {mod['minecraft_version']}\n"
            f"Loader: {mod['loader']}\n"
            f"Source folder: {mod['folder_name_in_zip']}\n\n"
            f"{precheck_error}\n",
            encoding="utf-8",
        )
        write_json(artifact_dir / "result.json", result)
        return 1

    try:
        with tempfile.TemporaryDirectory(prefix=f"modcompiler-{mod['slug']}-") as temp_dir:
            workspace = Path(temp_dir) / "workspace"
            copy_tree(Path(mod["template_dir"]), workspace)
            adapter_metadata_path = Path(temp_dir) / "metadata.json"
            write_json(adapter_metadata_path, mod["metadata"])
            adapter_script = Path(mod["range_folder"]) / "build_adapter.py"
            adapter_command = [
                sys.executable,
                str(adapter_script),
                "--loader",
                mod["loader"],
                "--metadata-json",
                str(adapter_metadata_path),
                "--source-dir",
                str(prepared_dir / mod["mod_dir"] / "src"),
                "--template-workspace",
                str(workspace),
                "--minecraft-version",
                mod["minecraft_version"],
                "--manifest",
                str(Path(args.manifest).resolve()),
            ]
            env = os.environ.copy()
            java_home = java_home_for_version(int(mod["java_version"]), env)
            env["JAVA_HOME"] = java_home
            env["PATH"] = sanitize_env_path(java_home, env.get("PATH"))

            build_command = list(mod["build_command"])
            with log_path.open("w", encoding="utf-8") as log_file:
                log_file.write("$ " + " ".join(adapter_command) + "\n\n")
                adapter_run = subprocess.run(
                    adapter_command,
                    cwd=Path.cwd(),
                    env=env,
                    stdout=log_file,
                    stderr=subprocess.STDOUT,
                    text=True,
                )
                if adapter_run.returncode != 0:
                    build_exit = adapter_run.returncode
                else:
                    log_file.write("\n$ " + " ".join(build_command) + "\n\n")
                    build_run = subprocess.run(
                        build_command,
                        cwd=workspace,
                        env=env,
                        stdout=log_file,
                        stderr=subprocess.STDOUT,
                        text=True,
                    )
                    build_exit = build_run.returncode
            if build_exit == 0:
                jars = find_built_jars(workspace, mod["jar_glob"])
                if not jars:
                    result["warnings"].append("Gradle exited successfully but no primary jar was found.")
                    build_exit = 1
                else:
                    classful = [jar_path for jar_path in jars if jar_contains_classes(jar_path)]
                    if not classful:
                        result["warnings"].append("Built jars contain no .class files.")
                        with log_path.open("a", encoding="utf-8") as log_file:
                            log_file.write(
                                "\nBuild produced jars without .class files. "
                                "Check source layout (expected under src/main/java).\n"
                            )
                        build_exit = 1
                    jar_dir = artifact_dir / "jars"
                    jar_dir.mkdir(parents=True, exist_ok=True)
                    for jar_path in jars:
                        copy_file(jar_path, jar_dir / jar_path.name)
                    result["jar_names"] = [jar_path.name for jar_path in jars]
    except Exception as error:  # pragma: no cover - defensive fallback
        with log_path.open("a", encoding="utf-8") as log_file:
            log_file.write("\nUnhandled build exception:\n")
            log_file.write("".join(traceback.format_exception(error)))
        result["warnings"].append(str(error))
        build_exit = 1

    result["status"] = "success" if build_exit == 0 else "failed"
    write_json(artifact_dir / "result.json", result)
    return build_exit


def command_bundle(args: argparse.Namespace) -> int:
    artifacts_root = Path(args.artifacts_root)
    output_dir = Path(args.output_dir)
    safe_rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    result_files = sorted(artifacts_root.rglob("result.json"))
    if not result_files:
        raise ModCompilerError(f"No per-mod result.json files were found under {artifacts_root}")

    def extract_metadata_from_jar(jar_path: Path) -> dict[str, Any] | None:
        if not jar_path or not jar_path.exists():
            return None
        try:
            with zipfile.ZipFile(jar_path) as jar:
                names = set(jar.namelist())
                if "fabric.mod.json" in names:
                    data = json.loads(jar.read("fabric.mod.json").decode("utf-8"))
                    mod_id = data.get("id") or data.get("modid") or "unknown"
                    name = data.get("name") or mod_id
                    version = data.get("version") or "0.0.0"
                    return {"mod_id": mod_id, "name": name, "mod_version": version}
                for toml_name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                    if toml_name in names:
                        data = tomllib.loads(jar.read(toml_name).decode("utf-8"))
                        mods = data.get("mods") or data.get("mod")
                        entry = mods[0] if isinstance(mods, list) and mods else {}
                        mod_id = entry.get("modId") or entry.get("modid") or "unknown"
                        name = entry.get("displayName") or entry.get("display_name") or entry.get("name") or mod_id
                        version = entry.get("version") or "0.0.0"
                        return {"mod_id": mod_id, "name": name, "mod_version": version}
                if "mcmod.info" in names:
                    data = json.loads(jar.read("mcmod.info").decode("utf-8"))
                    entry: dict[str, Any] = {}
                    if isinstance(data, list) and data:
                        entry = data[0]
                    elif isinstance(data, dict):
                        mod_list = data.get("modList") or data.get("modlist")
                        if isinstance(mod_list, list) and mod_list:
                            entry = mod_list[0]
                    if entry:
                        mod_id = entry.get("modid") or entry.get("modId") or "unknown"
                        name = entry.get("name") or mod_id
                        version = entry.get("version") or "0.0.0"
                        return {"mod_id": mod_id, "name": name, "mod_version": version}
        except Exception:
            return None
        return None

    normalized: dict[str, dict[str, Any]] = {}
    sources: dict[str, Path] = {}
    scores: dict[str, int] = {}

    for result_path in result_files:
        result = load_json(result_path)
        if not all(key in result for key in ("slug", "loader", "minecraft_version")):
            continue

        slug = result["slug"]
        source_root = result_path.parent
        jar_root = source_root / "jars"

        metadata = result.get("metadata")
        if not isinstance(metadata, dict) or not metadata:
            jar_path = select_primary_jar(jar_root)
            metadata = extract_metadata_from_jar(jar_path) if jar_path else None
            if not metadata:
                raise ModCompilerError(
                    f"Missing metadata for {slug}. Ensure jars are present so metadata can be parsed."
                )
            result["metadata"] = metadata
        else:
            if "mod_id" not in metadata and "primary_mod_id" in metadata:
                metadata["mod_id"] = metadata["primary_mod_id"]
            result["metadata"] = metadata

        if "range_folder" not in result:
            result["range_folder"] = "-"
        if "log_relpath" not in result:
            result["log_relpath"] = "build.log" if (source_root / "build.log").exists() else "-"
        if "jar_names" not in result:
            if jar_root.exists():
                result["jar_names"] = [p.name for p in sorted(jar_root.glob("*.jar")) if p.is_file()]
            else:
                result["jar_names"] = []

        score = 0
        if result.get("status") == "success":
            score += 2
        if result["jar_names"]:
            score += 1

        if slug not in normalized or score > scores.get(slug, -1):
            normalized[slug] = result
            sources[slug] = source_root
            scores[slug] = score

    if not normalized:
        raise ModCompilerError(f"No usable mod results were found under {artifacts_root}")

    results = list(normalized.values())
    for slug, source_root in sources.items():
        target_root = output_dir / "mods" / slug
        copy_tree(source_root, target_root)

    summary_markdown = render_summary_markdown(results)
    (output_dir / "SUMMARY.md").write_text(summary_markdown, encoding="utf-8")
    write_json(output_dir / "run-summary.json", {"mods": results})
    has_failures = any(result["status"] != "success" for result in results)
    return 1 if has_failures else 0
