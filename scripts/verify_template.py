#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from modcompiler.adapters import prepare_workspace
from modcompiler.common import (
    ModCompilerError,
    ModMetadata,
    copy_tree,
    expand_minecraft_version_spec,
    find_built_jars,
    java_home_for_version,
    load_json,
    parse_version_tuple,
    resolve_java_version,
    sanitize_env_path,
    write_json,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify a template builds for a given range + loader.")
    parser.add_argument("--range-folder", required=True)
    parser.add_argument("--loader", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--versions", default="")
    parser.add_argument("--timeout-minutes", type=int, default=0)
    return parser.parse_args()


def detect_entrypoint_class(source_root: Path) -> str:
    candidates = sorted(list(source_root.rglob("*.java")) + list(source_root.rglob("*.kt")))
    preferred_tokens = ("ModInitializer", "ClientModInitializer", "DedicatedServerModInitializer", "@Mod")

    def extract_class_name(text: str) -> str | None:
        package_match = re.search(r"(?m)^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;", text)
        class_match = re.search(r"\\bclass\\s+([A-Za-z0-9_]+)", text)
        if not class_match:
            return None
        package = package_match.group(1) if package_match else ""
        class_name = class_match.group(1)
        return f"{package}.{class_name}" if package else class_name

    for path in candidates:
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        if any(token in text for token in preferred_tokens):
            found = extract_class_name(text)
            if found:
                return found

    for path in candidates:
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        found = extract_class_name(text)
        if found:
            return found

    return "com.example.TemplateMod"


def build_metadata(entrypoint_class: str) -> ModMetadata:
    return ModMetadata(
        mod_id="template_test",
        name="Template Test",
        mod_version="0.0.0",
        group="com.example",
        entrypoint_class=entrypoint_class,
        runtime_side="both",
        description="Template compile verification.",
        authors=["ModCompiler"],
        license="MIT",
        homepage=None,
        sources=None,
        issues=None,
    )


def resolve_versions(range_entry: dict[str, Any], loader_config: dict[str, Any], override: str) -> list[str]:
    if override:
        return [v.strip() for v in override.split(",") if v.strip()]
    anchor = loader_config.get("anchor_version")
    if anchor:
        return [anchor]
    if loader_config.get("supported_versions"):
        return list(loader_config["supported_versions"])
    return expand_minecraft_version_spec(f"{range_entry['min_version']}-{range_entry['max_version']}")


def run_build(
    *,
    manifest: dict[str, Any],
    range_folder: str,
    range_entry: dict[str, Any],
    loader: str,
    loader_config: dict[str, Any],
    minecraft_version: str,
    template_dir: Path,
    output_dir: Path,
    timeout_seconds: int | None,
) -> dict[str, Any]:
    result = {
        "range_folder": range_folder,
        "loader": loader,
        "minecraft_version": minecraft_version,
        "status": "failed",
        "jar_names": [],
        "log_relpath": f"{minecraft_version}.log",
        "error": "",
    }

    source_dir = template_dir / "src"
    if not source_dir.exists():
        result["error"] = f"Template source folder missing: {source_dir}"
        return result

    entrypoint = detect_entrypoint_class(source_dir)
    metadata = build_metadata(entrypoint)

    build_command = list(loader_config.get("build_command", ["./gradlew", "build", "--no-daemon"]))
    jar_glob = loader_config.get("jar_glob", "build/libs/*.jar")

    output_dir.mkdir(parents=True, exist_ok=True)
    log_path = output_dir / result["log_relpath"]

    with tempfile.TemporaryDirectory(prefix=f"template-{range_folder}-{loader}-{minecraft_version}-") as temp_dir:
        workspace = Path(temp_dir) / "workspace"
        source_copy = Path(temp_dir) / "source"
        copy_tree(template_dir, workspace)
        copy_tree(source_dir, source_copy)

        try:
            prepare_workspace(
                manifest=manifest,
                range_folder=range_folder,
                loader=loader,
                source_dir=source_copy,
                workspace=workspace,
                minecraft_version=minecraft_version,
                metadata=metadata,
            )
        except ModCompilerError as error:
            result["error"] = str(error)
            return result

        gradlew = workspace / "gradlew"
        if gradlew.exists():
            gradlew.chmod(gradlew.stat().st_mode | 0o111)

        env = os.environ.copy()
        java_version = resolve_java_version(loader_config, minecraft_version)
        java_home = java_home_for_version(int(java_version), env)
        env["JAVA_HOME"] = java_home
        env["PATH"] = sanitize_env_path(java_home, env.get("PATH"))

        with log_path.open("w", encoding="utf-8") as log_file:
            log_file.write("$ " + " ".join(build_command) + "\n\n")
            try:
                build_run = subprocess.run(
                    build_command,
                    cwd=workspace,
                    env=env,
                    stdout=log_file,
                    stderr=subprocess.STDOUT,
                    text=True,
                    timeout=timeout_seconds,
                )
            except subprocess.TimeoutExpired:
                result["error"] = f"Build timed out after {timeout_seconds} seconds."
                log_file.write(f"\n[modcompiler] Build timed out after {timeout_seconds} seconds.\n")
                return result

        if build_run.returncode != 0:
            result["error"] = f"Build exited with {build_run.returncode}"
            return result

        jars = find_built_jars(workspace, jar_glob)
        if not jars:
            result["error"] = "Build succeeded but no jar matched jar_glob."
            return result

        jar_dir = output_dir / "jars"
        jar_dir.mkdir(parents=True, exist_ok=True)
        for jar_path in jars:
            shutil.copy2(jar_path, jar_dir / jar_path.name)
        result["jar_names"] = [jar_path.name for jar_path in jars]
        result["status"] = "success"
    return result


def render_summary(results: list[dict[str, Any]]) -> str:
    lines = [
        "# Template Verify Summary",
        "",
        "| Range | Loader | Minecraft | Status | Jar | Log |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for result in results:
        jar = ", ".join(result.get("jar_names", [])) or "-"
        lines.append(
            "| {range_folder} | {loader} | {minecraft_version} | {status} | {jar} | {log_relpath} |".format(
                range_folder=result["range_folder"],
                loader=result["loader"],
                minecraft_version=result["minecraft_version"],
                status=result["status"],
                jar=jar,
                log_relpath=result["log_relpath"],
            )
        )
        if result.get("error"):
            lines.append(f"- Error: {result['error']}")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    args = parse_args()
    manifest = load_json(Path("version-manifest.json"))
    range_folder = args.range_folder
    loader = args.loader

    range_entry = next((entry for entry in manifest["ranges"] if entry["folder"] == range_folder), None)
    if not range_entry:
        raise ModCompilerError(f"Unknown range folder '{range_folder}'")
    loader_config = range_entry["loaders"].get(loader)
    if not loader_config:
        raise ModCompilerError(f"Loader '{loader}' not configured for range '{range_folder}'")

    template_dir = Path(loader_config["template_dir"])
    if not template_dir.exists():
        raise ModCompilerError(f"Template directory missing: {template_dir}")

    versions = resolve_versions(range_entry, loader_config, args.versions)
    versions = sorted(set(versions), key=parse_version_tuple)

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    results = []
    for version in versions:
        result = run_build(
            manifest=manifest,
            range_folder=range_folder,
            range_entry=range_entry,
            loader=loader,
            loader_config=loader_config,
            minecraft_version=version,
            template_dir=template_dir,
            output_dir=output_dir,
            timeout_seconds=args.timeout_minutes * 60 if args.timeout_minutes else None,
        )
        results.append(result)

    write_json(output_dir / "result.json", {"results": results})
    (output_dir / "SUMMARY.md").write_text(render_summary(results), encoding="utf-8")

    failures = [r for r in results if r["status"] != "success"]
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
