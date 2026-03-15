from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import zipfile
import urllib.parse
import urllib.request
import urllib.error
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from modcompiler.common import (
    ModCompilerError,
    copy_tree,
    expand_minecraft_version_spec,
    find_text_files,
    load_json,
    parse_version_tuple,
    safe_extract_zip,
    safe_rmtree,
    version_inclusive_between,
    write_json,
)
from modcompiler.decompile import DecompileResult, decompile_jar_internal
from modcompiler.modrinth import (
    ModrinthClient,
    build_modrinth_user_agent,
    normalize_modrinth_project_ref,
    select_primary_modrinth_file,
)

MAX_SRC_SIZE_BYTES = 100 * 1024
PRIORITY_JAVA_PATTERNS = [
    r".*Mod\.java$",
    r".*Mod\.kt$",
    r".*Client\.java$",
    r".*Client\.kt$",
    r".*Common\.java$",
    r".*Common\.kt$",
    r".*Init\.java$",
    r".*Init\.kt$",
    r".*Registry\.java$",
    r".*Registry\.kt$",
    r"main/.*\.java$",
    r"main/.*\.kt$",
]
EXCLUDE_EXTENSIONS = {".json", ".lang", ".properties", ".png", ".ogg", ".txt", ".mcmeta", ".cfg"}


@dataclass
class AutoUpdateConfig:
    mod_jar_path: str
    modrinth_project_url: str | None
    mod_description: str
    auto_fetch_modrinth: bool
    auto_fix_corrupted: bool
    auto_fix_only: bool
    auto_fix_corrupted_downloads_dir: str | None
    auto_fix_corrupted_decompiled_dir: str | None
    auto_fix_corrupted_report_dir: str | None
    only_target: str | None
    plan_only: bool
    reuse_decompiled_dir: str | None
    version_range: str
    update_mode: str
    publish_mode: str
    manifest_path: str
    output_dir: Path


@dataclass
class DecomposedMod:
    src_path: Path
    mod_info: dict[str, Any]
    current_version: str
    current_loader: str
    metadata: dict[str, Any]


@dataclass
class VersionTarget:
    minecraft_version: str
    loader: str
    slug: str


def parse_version_input(version_input: str, manifest: dict[str, Any], current_loader: str) -> list[VersionTarget]:
    version_input = version_input.strip().lower()
    if not version_input or version_input == "all":
        return _get_all_supported_versions(manifest, current_loader)

    if "," in version_input:
        targets = []
        for part in version_input.split(","):
            part = part.strip()
            if not part:
                continue
            parsed = _parse_explicit_version_pair(part, manifest)
            for p in parsed:
                targets.append(p)
        return targets

    if "-" in version_input:
        return _parse_version_range(version_input, manifest, current_loader)

    return _parse_explicit_version_pair(version_input, manifest)


def _get_all_supported_versions(manifest: dict[str, Any], current_loader: str) -> list[VersionTarget]:
    targets = []
    for range_entry in manifest["ranges"]:
        folder = range_entry["folder"]
        loaders = range_entry.get("loaders", {})
        if current_loader not in loaders:
            continue
        loader_config = loaders[current_loader]
        supported_versions = loader_config.get("supported_versions", [])
        
        if not supported_versions:
            min_ver = range_entry.get("min_version", "")
            max_ver = range_entry.get("max_version", "")
            if min_ver and max_ver:
                supported_versions = [max_ver]
        
        if not supported_versions:
            continue
        for mc_version in supported_versions:
            targets.append(VersionTarget(
                minecraft_version=mc_version,
                loader=current_loader,
                slug=f"{mc_version}-{current_loader}",
            ))
    return targets


def _get_all_supported_versions_for_loaders(
    manifest: dict[str, Any],
    loaders: list[str],
) -> list[VersionTarget]:
    targets: list[VersionTarget] = []
    for range_entry in manifest["ranges"]:
        entry_loaders = range_entry.get("loaders", {})
        for loader in loaders:
            if loader not in entry_loaders:
                continue
            loader_config = entry_loaders[loader]
            supported_versions = loader_config.get("supported_versions", [])
            if not supported_versions:
                min_ver = range_entry.get("min_version", "")
                max_ver = range_entry.get("max_version", "")
                if min_ver and max_ver:
                    supported_versions = [max_ver]
            if not supported_versions:
                continue
            for mc_version in supported_versions:
                targets.append(VersionTarget(
                    minecraft_version=mc_version,
                    loader=loader,
                    slug=f"{mc_version}-{loader}",
                ))
    return targets


def _parse_explicit_version_pair(part: str, manifest: dict[str, Any]) -> list[VersionTarget]:
    match = re.match(r"^(\d+\.\d+(?:\.\d+)?)(fabric|forge|neoforge)?$", part)
    if not match:
        raise ModCompilerError(f"Invalid version format: {part}")
    version = match.group(1)
    loader = match.group(2) if match.group(2) else None
    return [VersionTarget(minecraft_version=version, loader=loader or "fabric", slug=f"{version}-{loader or 'fabric'}")]


def _parse_version_range(version_input: str, manifest: dict[str, Any], current_loader: str) -> list[VersionTarget]:
    if "-" not in version_input:
        raise ModCompilerError(f"Invalid version range: {version_input}")

    lower_raw, upper_raw = version_input.split("-", 1)
    lower_raw = lower_raw.strip()
    upper_raw = upper_raw.strip()

    lower_parts = lower_raw.split(".")
    upper_parts = upper_raw.split(".")
    major = lower_parts[0]
    minor = lower_parts[1] if len(lower_parts) > 1 else "0"
    lower = f"{major}.{minor}"
    upper = f"{major}.{upper_parts[1]}" if len(upper_parts) > 1 else f"{major}.{minor}"

    targets = []
    for range_entry in manifest["ranges"]:
        mc_version = range_entry.get("min_version", "")
        if not mc_version.startswith(lower.split(".")[0]):
            continue

        mc_parts = mc_version.split(".")
        if len(mc_parts) < 2:
            continue
        mc_minor = mc_parts[1]
        if mc_minor < lower.split(".")[1] or mc_minor > upper.split(".")[1]:
            continue

        loaders = range_entry.get("loaders", {})
        if current_loader not in loaders:
            continue

        loader_config = loaders[current_loader]
        supported_versions = loader_config.get("supported_versions", [])
        
        if not supported_versions:
            min_ver = range_entry.get("min_version", "")
            max_ver = range_entry.get("max_version", "")
            if min_ver and max_ver:
                supported_versions = [max_ver]
        
        for mc_ver in supported_versions:
            targets.append(VersionTarget(
                minecraft_version=mc_ver,
                loader=current_loader,
                slug=f"{mc_ver}-{current_loader}",
            ))

    return targets


def check_modrinth_versions(modrinth_project_url: str, loader: str) -> dict[str, Any]:
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if not token:
        raise ModCompilerError("MODRINTH_TOKEN not configured")

    project_ref = normalize_modrinth_project_ref(modrinth_project_url)
    user_agent = build_modrinth_user_agent()
    client = ModrinthClient(token=token, user_agent=user_agent)

    project = client.resolve_project(project_ref)
    params = {"include_changelog": "false"}
    if loader:
        params["loaders"] = json.dumps([loader])
    versions = client.request_json(
        "GET",
        f"/project/{urllib.parse.quote(project_ref, safe='')}/version",
        params=params,
    )

    existing = {"versions": [], "loaders": set()}
    versions_by_loader: dict[str, set[str]] = {}
    if isinstance(versions, list):
        for v in versions:
            game_versions = v.get("game_versions", [])
            loaders = v.get("loaders", [])
            for gv in game_versions:
                existing["versions"].append(gv)
            for l in loaders:
                existing["loaders"].add(l)
                versions_by_loader.setdefault(l, set()).update(game_versions)

    existing["versions"] = list(set(existing["versions"]))
    versions_by_loader_serialized = {k: sorted(list(v)) for k, v in versions_by_loader.items()}
    return {
        "project_id": project.get("id", ""),
        "project_slug": project.get("slug", ""),
        "existing_versions": existing["versions"],
        "existing_loaders": list(existing["loaders"]),
        "existing_versions_by_loader": versions_by_loader_serialized,
    }


def _manifest_supports_version(manifest: dict[str, Any], loader: str, mc_version: str) -> bool:
    for range_entry in manifest.get("ranges", []):
        if not version_inclusive_between(
            mc_version,
            range_entry.get("min_version", ""),
            range_entry.get("max_version", ""),
        ):
            continue
        loaders = range_entry.get("loaders", {})
        if loader not in loaders:
            continue
        loader_config = loaders[loader]
        supported_versions = loader_config.get("supported_versions", [])
        if supported_versions and mc_version not in supported_versions:
            continue
        return True
    return False


def _extract_json_payload(text: str) -> dict[str, Any] | None:
    if not text:
        return None
    stripped = text.strip()
    if stripped.startswith("{") and stripped.endswith("}"):
        try:
            return json.loads(stripped)
        except json.JSONDecodeError:
            return None
    start = stripped.find("{")
    end = stripped.rfind("}")
    if start == -1 or end == -1 or end <= start:
        return None
    try:
        return json.loads(stripped[start:end + 1])
    except json.JSONDecodeError:
        return None


def _bump_mod_version(base_version: str, used_versions: set[str]) -> str:
    base_version = base_version.strip() or "0.0.0"
    parts = base_version.split(".")
    if all(p.isdigit() for p in parts):
        numbers = [int(p) for p in parts]
        while True:
            numbers[-1] += 1
            candidate = ".".join(str(n) for n in numbers)
            if candidate not in used_versions:
                used_versions.add(candidate)
                return candidate
    suffix_index = 1
    while True:
        candidate = f"{base_version}-fix{suffix_index}"
        if candidate not in used_versions:
            used_versions.add(candidate)
            return candidate
        suffix_index += 1


def _verify_mod_source_with_ai(
    *,
    client: "OpenRouterClient",
    project_name: str,
    project_description: str,
    version_label: str,
    loader: str,
    game_versions: list[str],
    src_dir: Path,
) -> dict[str, Any]:
    files = _list_src_files(src_dir)
    summary = _generate_src_summary(src_dir, files)
    key_files = _identify_key_files(files)[:6]

    excerpts: list[str] = []
    for rel_path, _priority in key_files:
        file_path = src_dir / rel_path
        if not file_path.exists():
            continue
        try:
            content = file_path.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        if len(content) > 3000:
            content = content[:3000] + "\n... (truncated)"
        excerpts.append(f"File: {rel_path}\n```\n{content}\n```")

    system_msg = (
        "You are verifying whether a Minecraft mod's source code matches its Modrinth listing. "
        "Return ONLY valid JSON with keys: tag (working/corrupted), confidence (0-1), "
        "rating (0-20, higher means better match), reason."
    )
    excerpts_text = "\n\n".join(excerpts) if excerpts else "(no key files found)"
    user_msg = (
        f"Mod name: {project_name}\n"
        f"Mod description:\n{project_description}\n\n"
        f"Modrinth version: {version_label}\n"
        f"Loader: {loader}\n"
        f"Game versions: {', '.join(game_versions)}\n\n"
        f"Source summary:\n{summary}\n\n"
        f"Key files:\n{excerpts_text}\n\n"
        "Evaluate whether this source appears to implement the described mod. "
        "Use tag=working only if the source matches the description and intended purpose. "
        "Use tag=corrupted if the source is missing, unrelated, or does not match the description. "
        "If uncertain, tag must be corrupted."
    )

    messages = [
        {"role": "system", "content": system_msg},
        {"role": "user", "content": user_msg},
    ]

    def parse_payload(text: str) -> dict[str, Any]:
        payload = _extract_json_payload(text) or {}
        tag = str(payload.get("tag", "")).strip().lower()
        confidence = payload.get("confidence")
        try:
            confidence = float(confidence)
        except (TypeError, ValueError):
            confidence = 0.0
        rating = payload.get("rating")
        try:
            rating = float(rating)
        except (TypeError, ValueError):
            rating = 0.0
        rating = max(0.0, min(20.0, rating))
        confidence = max(0.0, min(1.0, confidence))
        reason = str(payload.get("reason", "")).strip()
        return {
            "tag": tag,
            "confidence": confidence,
            "rating": rating,
            "reason": reason,
            "raw": text,
        }

    def call_ai(msgs: list[dict[str, str]], max_tokens: int) -> tuple[str | None, str | None]:
        try:
            response = client.chat_completion_with_fallback(
                messages=msgs,
                temperature=0.2,
                max_tokens=max_tokens,
            )
            content = response.get("choices", [{}])[0].get("message", {}).get("content", "")
            if not content or not str(content).strip():
                return None, "Empty AI response"
            return content, None
        except Exception as exc:
            return None, f"{type(exc).__name__}: {exc}"

    def call_ai_with_retry(
        msgs: list[dict[str, str]],
        max_tokens: int,
        retries: int = 10,
        max_seconds: float | None = None,
    ) -> tuple[str | None, str | None]:
        last_error: str | None = None
        start = time.time()
        for attempt in range(1, retries + 1):
            if max_seconds is not None and time.time() - start > max_seconds:
                break
            content, error = call_ai(msgs, max_tokens)
            if content is not None:
                return content, None
            last_error = error
            if attempt < retries:
                delay = min(2.0, 0.5 * attempt)
                if max_seconds is not None and time.time() - start + delay > max_seconds:
                    break
                time.sleep(delay)
        return None, last_error

    def call_ai_with_race(
        msgs: list[dict[str, str]],
        max_tokens: int,
        first_timeout: float = 60.0,
        second_timeout: float = 240.0,
    ) -> tuple[str | None, str | None]:
        result1: dict[str, Any] = {"done": False}
        result2: dict[str, Any] = {"done": False}

        def worker(result: dict[str, Any], max_seconds: float | None) -> None:
            content, error = call_ai_with_retry(msgs, max_tokens, max_seconds=max_seconds)
            result["content"] = content
            result["error"] = error
            result["done"] = True

        thread1 = threading.Thread(target=worker, args=(result1, 300.0), daemon=True)
        thread1.start()
        thread1.join(timeout=first_timeout)

        if result1.get("done") and result1.get("content"):
            return result1["content"], None

        thread2 = threading.Thread(target=worker, args=(result2, second_timeout), daemon=True)
        thread2.start()

        deadline = time.time() + second_timeout
        while time.time() < deadline:
            if result1.get("done") and result1.get("content"):
                return result1["content"], None
            if result2.get("done") and result2.get("content"):
                return result2["content"], None
            if result1.get("done") and result2.get("done"):
                break
            time.sleep(0.25)

        for result in (result1, result2):
            if result.get("done") and result.get("content"):
                return result["content"], None

        errors = [err for err in [result1.get("error"), result2.get("error")] if err]
        if errors:
            return None, "; ".join(errors)
        return None, "AI verify timed out (no response after 1+4 minutes)."

    content, error = call_ai_with_race(messages, 400)
    if content is None:
        return {
            "tag": "corrupted",
            "verdict": "fail",
            "confidence": 0.0,
            "rating": 0.0,
            "reason": f"AI request failed: {error}",
            "raw": "",
        }

    parsed = parse_payload(content)
    if parsed["tag"] not in {"working", "corrupted"}:
        messages.append({
            "role": "user",
            "content": (
                "Your previous response was invalid. Return ONLY JSON with keys: "
                "tag (working/corrupted), rating (0-20), confidence (0-1), reason."
            ),
        })
        content, error = call_ai_with_race(messages, 200)
        if content is None:
            return {
                "tag": "corrupted",
                "verdict": "fail",
                "confidence": 0.0,
                "rating": 0.0,
                "reason": f"AI request failed: {error}",
                "raw": "",
            }
        parsed = parse_payload(content)

    if parsed["tag"] not in {"working", "corrupted"}:
        parsed["tag"] = "corrupted"
        if not parsed.get("reason"):
            parsed["reason"] = "Invalid AI response; missing tag."
    parsed["verdict"] = "pass" if parsed["tag"] == "working" else "fail"
    return parsed


def _run_auto_fix_corrupted(
    *,
    manifest: dict[str, Any],
    modrinth_project_url: str,
    output_dir: Path,
    downloads_dir: Path | None = None,
    predecompiled_dir: Path | None = None,
    verification_entries: list[dict[str, Any]] | None = None,
) -> tuple[list[VersionTarget], dict[str, dict[str, Any]], dict[str, dict[str, Any]], Path]:
    token = os.environ.get("MODRINTH_TOKEN", "").strip() or None
    project_ref = normalize_modrinth_project_ref(modrinth_project_url)
    project, versions = _fetch_modrinth_project_and_versions(project_ref, token)

    project_name = str(project.get("title") or project.get("name") or project.get("slug") or project_ref)
    project_description = _extract_modrinth_description(project) or str(project.get("description", "") or "")

    client = None
    if verification_entries is None:
        from modcompiler.openrouter import OpenRouterClient
        client = OpenRouterClient()
        if not client.key_states:
            raise ModCompilerError("auto-fix-corrupted requires OPENROUTER_API_KEY to be set.")

    scan_root = output_dir / "_corrupt_scan"
    downloads_root = downloads_dir if downloads_dir else (scan_root / "downloads")
    downloads_root = Path(downloads_root)
    decompiled_dir = scan_root / "decompiled"
    predecompiled_root = Path(predecompiled_dir) if predecompiled_dir else None
    role_models_dir = scan_root / "role-models"
    downloads_root.mkdir(parents=True, exist_ok=True)
    decompiled_dir.mkdir(parents=True, exist_ok=True)
    role_models_dir.mkdir(parents=True, exist_ok=True)

    report_entries: list[dict[str, Any]] = []
    role_models: dict[str, dict[str, Any]] = {}
    corrupted_map: dict[tuple[str, str], dict[str, Any]] = {}
    src_dirs_by_version: dict[str, Path] = {}

    if verification_entries is not None:
        report_entries = verification_entries
        if predecompiled_root:
            for entry in report_entries:
                version_id = str(entry.get("version_id") or "")
                if not version_id:
                    continue
                candidate = predecompiled_root / version_id
                if candidate.exists() and (list(candidate.rglob("*.java")) or list(candidate.rglob("*.kt"))):
                    src_dirs_by_version[version_id] = candidate
    else:
        for version in versions:
            version_id = str(version.get("id") or version.get("version_number") or "unknown")
            version_number = str(version.get("version_number") or "")
            loaders = [str(l) for l in version.get("loaders", [])]
            game_versions = [str(v) for v in version.get("game_versions", [])]
            date_published = str(version.get("date_published") or version.get("date") or "")

            file_info = select_primary_modrinth_file(version.get("files", []))
            if not file_info:
                report_entries.append({
                    "version_id": version_id,
                    "version_number": version_number,
                    "loaders": loaders,
                    "game_versions": game_versions,
                    "tag": "corrupted",
                    "verdict": "fail",
                    "confidence": 0.0,
                    "reason": "No downloadable file found.",
                    "date_published": date_published,
                })
                continue

            file_url = str(file_info.get("url", "")).strip()
            filename = str(file_info.get("filename", "")).strip() or f"{project_ref}-{version_id}.jar"
            if not file_url:
                report_entries.append({
                    "version_id": version_id,
                    "version_number": version_number,
                    "loaders": loaders,
                    "game_versions": game_versions,
                    "tag": "corrupted",
                    "verdict": "fail",
                    "confidence": 0.0,
                    "reason": "Missing download URL.",
                    "date_published": date_published,
                })
                continue

            pre_src_dir = None
            if predecompiled_root:
                candidate = predecompiled_root / version_id
                if candidate.exists():
                    has_sources = any(candidate.rglob("*.java")) or any(candidate.rglob("*.kt"))
                    if has_sources:
                        pre_src_dir = candidate

            if pre_src_dir:
                src_dir = pre_src_dir
                decompile_status = "success"
            else:
                jar_path = downloads_root / version_id / filename
                if not jar_path.exists():
                    _download_modrinth_file(file_url, jar_path)

                decomp_output = decompiled_dir / version_id
                decompile_result = decompile_jar_internal(jar_path, manifest, output_dir=decomp_output)
                src_dir = decompile_result.extracted_src
                decompile_status = decompile_result.status

            src_dirs_by_version[version_id] = src_dir

            verdict_payload = {
                "tag": "corrupted",
                "verdict": "fail",
                "confidence": 0.0,
                "reason": "Decompiler produced no sources.",
            }
            if decompile_status == "success":
                verdict_payload = _verify_mod_source_with_ai(
                    client=client,
                    project_name=project_name,
                    project_description=project_description,
                    version_label=version_number or version_id,
                    loader=loaders[0] if loaders else "unknown",
                    game_versions=game_versions,
                    src_dir=src_dir,
                )

            tag = str(verdict_payload.get("tag", "")).lower()
            if tag not in {"working", "corrupted"}:
                verdict = verdict_payload.get("verdict", "").lower()
                tag = "working" if verdict == "pass" else "corrupted"
            confidence = float(verdict_payload.get("confidence", 0.0) or 0.0)
            rating = float(verdict_payload.get("rating", 0.0) or 0.0)
            passed = tag == "working"
            verdict = "pass" if passed else "fail"

            report_entries.append({
                "version_id": version_id,
                "version_number": version_number,
                "loaders": loaders,
                "game_versions": game_versions,
                "tag": tag,
                "verdict": verdict,
                "confidence": confidence,
                "rating": rating,
                "reason": verdict_payload.get("reason", ""),
                "date_published": date_published,
            })

    candidates_by_loader: dict[str, list[dict[str, Any]]] = {}
    for entry in report_entries:
        version_id = str(entry.get("version_id") or "")
        version_number = str(entry.get("version_number") or "")
        loaders = [str(l) for l in entry.get("loaders", [])]
        game_versions = [str(v) for v in entry.get("game_versions", [])]
        date_published = str(entry.get("date_published") or "")
        tag = str(entry.get("tag") or "").lower()
        if tag not in {"working", "corrupted"}:
            verdict = str(entry.get("verdict", "")).lower()
            tag = "working" if verdict == "pass" else "corrupted"
        confidence = float(entry.get("confidence", 0.0) or 0.0)
        rating = float(entry.get("rating", 0.0) or 0.0)
        passed = tag == "working"
        verdict = "pass" if passed else "fail"

        src_dir = src_dirs_by_version.get(version_id)
        if src_dir is not None:
            published_at = _parse_modrinth_datetime(date_published)
            candidate = {
                "src_dir": src_dir,
                "version_id": version_id,
                "version_number": version_number,
                "game_versions": game_versions,
                "published_at": published_at,
                "rating": rating,
                "confidence": confidence,
                "tag": tag,
                "verdict": verdict,
            }
            for loader in loaders:
                candidates_by_loader.setdefault(loader, []).append(candidate)

        if not passed:
            published_at = _parse_modrinth_datetime(date_published)
            for loader in loaders:
                for mc_version in game_versions:
                    if not _manifest_supports_version(manifest, loader, mc_version):
                        continue
                    key = (loader, mc_version)
                    current = corrupted_map.get(key)
                    if not current or published_at > current.get("published_at", 0.0):
                        corrupted_map[key] = {
                            "loader": loader,
                            "minecraft_version": mc_version,
                            "version_id": version_id,
                            "version_number": version_number,
                            "published_at": published_at,
                        }

    role_models: dict[str, dict[str, Any]] = {}
    for loader, candidates in candidates_by_loader.items():
        passed_candidates = [c for c in candidates if c.get("tag") == "working"]
        pool = passed_candidates or candidates
        if not pool:
            continue
        max_rating = max(c["rating"] for c in pool)
        top_candidates = [c for c in pool if c["rating"] == max_rating]
        role_models[loader] = {
            "candidates": top_candidates,
            "unverified": not passed_candidates,
        }

    role_model_paths: dict[str, dict[str, Any]] = {}
    for loader, info in role_models.items():
        dest = role_models_dir / loader
        safe_rmtree(dest)
        dest.mkdir(parents=True, exist_ok=True)
        candidates_info: list[dict[str, Any]] = []
        for cand in info.get("candidates", []):
            version_id = cand.get("version_id") or "unknown"
            subdir = dest / re.sub(r"[^A-Za-z0-9._-]+", "_", str(version_id))
            safe_rmtree(subdir)
            copy_tree(cand["src_dir"], subdir)
            candidate_payload = {
                "path": str(subdir),
                "version_id": cand.get("version_id", ""),
                "version_number": cand.get("version_number", ""),
                "game_versions": cand.get("game_versions", []),
                "published_at": cand.get("published_at", 0.0),
                "rating": cand.get("rating", 0.0),
                "confidence": cand.get("confidence", 0.0),
                "tag": cand.get("tag", ""),
                "verdict": cand.get("verdict", ""),
            }
            candidates_info.append(candidate_payload)
        role_model_paths[loader] = {
            "candidates": candidates_info,
            "unverified": bool(info.get("unverified")),
        }

    corrupted_targets: list[VersionTarget] = []
    fix_info_map: dict[str, dict[str, Any]] = {}
    for (_loader, _version), info in sorted(corrupted_map.items()):
        slug = f"{info['minecraft_version']}-{info['loader']}"
        corrupted_targets.append(VersionTarget(
            minecraft_version=info["minecraft_version"],
            loader=info["loader"],
            slug=slug,
        ))
        fix_info_map[slug] = {
            "fix_corrupted": True,
            "fix_source_version": info.get("version_number", ""),
            "fix_source_modrinth_id": info.get("version_id", ""),
        }

    report_path = scan_root / "corruption_report.json"
    write_json(report_path, {
        "project_ref": project_ref,
        "project_name": project_name,
        "entries": report_entries,
        "role_models": role_model_paths,
    })

    return corrupted_targets, fix_info_map, role_model_paths, report_path


def _parse_bool(value: str | bool | None, default: bool) -> bool:
    if isinstance(value, bool):
        return value
    if value is None:
        return default
    text = str(value).strip().lower()
    if text in {"1", "true", "yes", "y", "on"}:
        return True
    if text in {"0", "false", "no", "n", "off"}:
        return False
    return default


def _parse_modrinth_datetime(value: str) -> float:
    import datetime

    try:
        return datetime.datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()
    except Exception:
        return 0.0


def _version_distance(a: str, b: str) -> float:
    try:
        a_tuple = parse_version_tuple(a)
        b_tuple = parse_version_tuple(b)
    except Exception:
        return float("inf")
    return sum(abs(ax - bx) for ax, bx in zip(a_tuple, b_tuple))


def _select_closest_role_model(target_version: str, candidates: list[dict[str, Any]]) -> dict[str, Any] | None:
    if not candidates:
        return None
    best = None
    best_distance = float("inf")
    best_rating = -1.0
    best_published = -1.0
    for cand in candidates:
        game_versions = [str(v) for v in cand.get("game_versions", []) if str(v).strip()]
        if game_versions:
            distance = min(_version_distance(target_version, gv) for gv in game_versions)
        else:
            distance = float("inf")
        rating = float(cand.get("rating", 0.0) or 0.0)
        published = float(cand.get("published_at", 0.0) or 0.0)
        if (
            distance < best_distance
            or (distance == best_distance and rating > best_rating)
            or (distance == best_distance and rating == best_rating and published > best_published)
        ):
            best = cand
            best_distance = distance
            best_rating = rating
            best_published = published
    return best


def _select_latest_modrinth_version(versions: list[dict[str, Any]]) -> dict[str, Any] | None:
    if not versions:
        return None

    def sort_key(version: dict[str, Any]) -> float:
        for key in (
            "date_published",
            "date",
            "published",
            "updated",
            "date_created",
            "date_modified",
        ):
            value = version.get(key)
            if value:
                return _parse_modrinth_datetime(str(value))
        return 0.0

    return max(versions, key=sort_key)


def _fetch_modrinth_project_and_versions(
    project_ref: str,
    token: str | None,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    user_agent = build_modrinth_user_agent()
    client = ModrinthClient(token=token, user_agent=user_agent)
    project = client.resolve_project(project_ref)
    versions = client.request_json(
        "GET",
        f"/project/{urllib.parse.quote(project_ref, safe='')}/version",
        params={"include_changelog": "false"},
    )
    if not isinstance(versions, list):
        raise ModCompilerError("Unexpected Modrinth versions response.")
    return project, versions


def _extract_modrinth_description(project: dict[str, Any]) -> str:
    body = str(project.get("body", "") or "").strip()
    if body:
        return body
    return str(project.get("description", "") or "").strip()


def _download_modrinth_file(url: str, dest: Path) -> None:
    req = urllib.request.Request(
        url,
        headers={"User-Agent": build_modrinth_user_agent()},
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            dest.parent.mkdir(parents=True, exist_ok=True)
            with dest.open("wb") as handle:
                shutil.copyfileobj(response, handle)
    except urllib.error.HTTPError as error:
        raise ModCompilerError(f"Failed to download Modrinth file: HTTP {error.code}") from None
    except urllib.error.URLError as error:
        raise ModCompilerError(f"Failed to download Modrinth file: {error.reason}") from None


def filter_versions_to_build(
    version_targets: list[VersionTarget],
    modrinth_info: dict[str, Any] | None,
    update_mode: str,
) -> list[VersionTarget]:
    if not modrinth_info or update_mode == "all-versions":
        return version_targets

    existing_by_loader = modrinth_info.get("existing_versions_by_loader")
    if isinstance(existing_by_loader, dict) and existing_by_loader:
        filtered = [
            vt
            for vt in version_targets
            if vt.minecraft_version not in set(existing_by_loader.get(vt.loader, []))
        ]
    else:
        existing = set(modrinth_info.get("existing_versions", []))
        filtered = [vt for vt in version_targets if vt.minecraft_version not in existing]
    return filtered


def trim_src_for_context(src_path: Path) -> Path:
    temp_dir = src_path.parent / "_trimmed_src"
    safe_rmtree(temp_dir)

    all_files = list(src_path.rglob("*"))
    total_size = sum(f.stat().st_size for f in all_files if f.is_file())

    if total_size <= MAX_SRC_SIZE_BYTES:
        shutil.copytree(src_path, temp_dir)
        return temp_dir

    temp_dir.mkdir(parents=True)

    java_files = [f for f in all_files if f.suffix in {".java", ".kt", ".kts"} and f.is_file()]
    resource_files = [f for f in all_files if f.suffix.lower() in EXCLUDE_EXTENSIONS and f.is_file()]

    priority_java = []
    other_java = []
    for jf in java_files:
        is_priority = any(re.match(p, jf.name) for p in PRIORITY_JAVA_PATTERNS)
        if is_priority:
            priority_java.append(jf)
        else:
            other_java.append(jf)

    current_size = 0
    kept_files = []

    for pf in priority_java:
        size = pf.stat().st_size
        if current_size + size <= MAX_SRC_SIZE_BYTES:
            kept_files.append(pf)
            current_size += size

    for of in other_java:
        size = of.stat().st_size
        if current_size + size <= MAX_SRC_SIZE_BYTES:
            kept_files.append(of)
            current_size += size

    for rf in resource_files:
        size = rf.stat().st_size
        if current_size + size <= MAX_SRC_SIZE_BYTES:
            kept_files.append(rf)
            current_size += size

    for kf in kept_files:
        rel_path = kf.relative_to(src_path)
        dest = temp_dir / rel_path
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(kf, dest)

    (temp_dir / "CONTEXT_NOTE.txt").write_text(
        f"Context was trimmed from {total_size // 1024}KB to ~{current_size // 1024}KB. "
        "Priority was given to main mod classes (*Mod.java, *Client.java, etc.).",
        encoding="utf-8",
    )

    return temp_dir


def generate_version_context(
    decomposed: DecomposedMod,
    target: VersionTarget,
    info_txt: str,
    trimmed_src: Path,
    manifest: dict[str, Any] | None = None,
) -> dict[str, Any]:
    template_dir = ""
    supported_versions_for_loader: list[str] = []
    supported_versions_for_range: list[str] = []
    latest_supported_version = ""
    if manifest:
        for range_entry in manifest.get("ranges", []):
            loaders = range_entry.get("loaders", {})
            if target.loader not in loaders:
                continue
            loader_config = loaders[target.loader]
            if version_inclusive_between(
                target.minecraft_version,
                range_entry.get("min_version", ""),
                range_entry.get("max_version", ""),
            ):
                template_dir = loader_config.get("template_dir", "")
                supported_versions_for_range = (
                    loader_config.get("supported_versions")
                    or expand_minecraft_version_spec(f"{range_entry.get('min_version', '')}-{range_entry.get('max_version', '')}")
                )
            supported_versions_for_loader.extend(
                loader_config.get("supported_versions")
                or expand_minecraft_version_spec(f"{range_entry.get('min_version', '')}-{range_entry.get('max_version', '')}")
            )

        if supported_versions_for_loader:
            supported_versions_for_loader = sorted(set(supported_versions_for_loader), key=parse_version_tuple)
            latest_supported_version = supported_versions_for_loader[-1]
        if supported_versions_for_range:
            supported_versions_for_range = sorted(set(supported_versions_for_range), key=parse_version_tuple)
    
    lib_sources = _get_library_sources(Path("."), target.minecraft_version, target.loader, allow_prepare=False)
    library_sources_path = str(lib_sources) if lib_sources else ""

    import datetime
    today = datetime.date.today()
    current_date = f"{today.strftime('%B')} {today.day}, {today.year}"
    
    return {
        "target_version": target.minecraft_version,
        "target_loader": target.loader,
        "current_version": decomposed.current_version,
        "current_loader": decomposed.current_loader,
        "mod_info": decomposed.metadata,
        "user_description": info_txt,
        "src_size": sum(f.stat().st_size for f in trimmed_src.rglob("*") if f.is_file()),
        "template_dir": template_dir,
        "library_sources": library_sources_path,
        "supported_versions_for_loader": supported_versions_for_loader,
        "supported_versions_for_range": supported_versions_for_range,
        "latest_supported_version": latest_supported_version,
        "current_year": today.year,
        "current_date": current_date,
    }


def get_tools_definition() -> list[dict[str, Any]]:
    return [
        {
            "type": "function",
            "function": {
                "name": "read_file",
                "description": "Read original mod source. Paths are relative to src/. Use 'java/com/package/ClassName.java', 'src/java/...', or 'src/main/java/...'.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Path from src/ root (java/, kotlin/, resources/). e.g., 'java/com/itamio/fpsdisplay/FpsDisplay.java'"
                        }
                    },
                    "required": ["path"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "description": "List original source files in src/",
                "name": "list_files",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Directory under src/: '.', 'java', 'src/java', 'src/main/java', 'resources', 'kotlin', etc."
                        }
                    }
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "read_reference",
                "description": "Read example mod file. Use path like 'src/main/java/com/example/ExampleMod.java'",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Path in reference/, e.g., 'src/main/java/com/example/ExampleMod.java'"
                        }
                    },
                    "required": ["path"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "list_reference",
                "description": "List example files available for target version",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Directory in reference/ (default: root)"
                        }
                    }
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "write_mod_file",
                "description": "Write updated Java/Kotlin file. Writes to 'src/java/' (auto-adds prefix). Use path like 'com/package/ClassName.java'.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Package path: 'com/itamio/fpsdisplay/FpsDisplay.java'"
                        },
                        "content": {
                            "type": "string",
                            "description": "Java source code"
                        }
                    },
                    "required": ["path", "content"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "write_resource",
                "description": "Write resource file (mcmod.info, pack.mcmeta). Auto-adds 'src/main/resources/' prefix.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Filename: 'mcmod.info', 'pack.mcmeta'"
                        },
                        "content": {
                            "type": "string",
                            "description": "Resource content"
                        }
                    },
                    "required": ["path", "content"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "copy_to_structure",
                "description": "Copy file from original src/ to build src/main/java/",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "source_path": {
                            "type": "string",
                            "description": "Path like 'java/com/itamio/fpsdisplay/FpsDisplay.java'"
                        }
                    },
                    "required": ["source_path"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "build",
                "description": "Build the mod. Gradle setup is automatic - just call this when ready!",
                "parameters": {
                    "type": "object",
                    "properties": {}
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "read_build_log",
                "description": "Read the build.log from the last build. Optionally filter with regex or show only the last N lines.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "pattern": {
                            "type": "string",
                            "description": "Regex pattern to match lines in build.log (optional)."
                        },
                        "tail_lines": {
                            "type": "integer",
                            "description": "Number of lines from the end of build.log to show (optional)."
                        }
                    }
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "complete",
                "description": "Mark task complete after successful build",
                "parameters": {
                    "type": "object",
                    "properties": {}
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "library_dir",
                "description": "List directory structure of decompiled Minecraft sources for the target version",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Directory path relative to Minecraft sources root, e.g., 'net/minecraft/client' or 'net/minecraftforge/fml/common'"
                        },
                        "depth": {
                            "type": "integer",
                            "description": "Depth of directory listing (default: 2)"
                        }
                    }
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "library_read",
                "description": "Read a decompiled Minecraft source file to understand API patterns",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Full path to Minecraft source file, e.g., 'net/minecraft/client/Minecraft.java'"
                        }
                    },
                    "required": ["path"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "library_search",
                "description": "Search for a class or method in Minecraft sources",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "Search query - class name, method name, or pattern"
                        }
                    },
                    "required": ["query"]
                }
            }
        }
    ]


def _identify_key_files(files: list[dict[str, Any]]) -> list[tuple[str, str]]:
    priority_patterns = [
        r".*[Mm]od\.java$",
        r".*[Cc]lient\.java$",
        r".*[Cc]ommon\.java$",
        r".*[Ii]nit\.java$",
        r".*[Rr]egistry\.java$",
        r".*[Mm]ain\.java$",
    ]
    import re
    
    key_files = []
    for f in files:
        path = f["path"]
        for pattern in priority_patterns:
            if re.search(pattern, path):
                key_files.append((path, "priority"))
                break
    
    for f in files:
        if f not in [x[0] for x in key_files]:
            key_files.append((f["path"], "other"))
    
    return key_files[:15]


def build_ai_system_prompt(context: dict[str, Any]) -> str:
    target_v = context["target_version"]
    target_l = context["target_loader"]
    current_v = context["current_version"]
    current_l = context["current_loader"]
    mod_info = context["mod_info"]
    desc = context["user_description"]
    current_date = context.get("current_date", "")
    current_year = context.get("current_year", "")
    supported_versions = context.get("supported_versions_for_loader", [])
    supported_versions_for_range = context.get("supported_versions_for_range", [])
    latest_supported = context.get("latest_supported_version", "")
    role_model_dir = context.get("role_model_dir", "")
    role_model_version = context.get("role_model_version", "")
    role_model_unverified = bool(context.get("role_model_unverified"))
    role_model_loader = context.get("role_model_loader", "")
    role_model_selected_rating = context.get("role_model_selected_rating", "")
    role_model_candidates = context.get("role_model_candidates", [])
    role_model_selected_path = context.get("role_model_selected_path", "")
    role_model_selected_source_subdir = context.get("role_model_selected_source_subdir", "")
    fix_corrupted = bool(context.get("fix_corrupted"))
    supported_versions_text = ", ".join(supported_versions) if supported_versions else "unknown"
    range_versions_text = ", ".join(supported_versions_for_range) if supported_versions_for_range else "unknown"

    role_model_block = ""
    if role_model_dir:
        label = "ROLE MODEL SOURCE (verified working)" if not role_model_unverified else "ROLE MODEL SOURCE (unverified fallback)"
        role_loader = role_model_loader or target_l
        candidate_lines = ""
        if role_model_candidates:
            summary = []
            for cand in role_model_candidates:
                tag = cand.get("tag", "")
                summary.append(
                    f"{cand.get('version_number', cand.get('version_id', 'unknown'))} "
                    f"(tag {tag or 'unknown'}, rating {cand.get('rating', 0)}/20, "
                    f"mc={','.join(cand.get('game_versions', [])) or 'unknown'})"
                )
            candidate_lines = "\nTop candidates:\n- " + "\n- ".join(summary)
        selected_path_line = ""
        if role_model_selected_path:
            selected_path_line = f"Selected candidate folder: {role_model_selected_path}\n"
        if role_model_selected_source_subdir:
            selected_path_line += f"Selected source subfolder: {role_model_selected_source_subdir}\n"
        role_model_block = (
            f"\n{label}: {role_model_dir} "
            f"(built for loader {role_loader}, version {role_model_version or 'unknown'}, "
            f"rating {role_model_selected_rating or 'unknown'}/20)\n"
            f"Target loader/version: {target_l} {target_v}\n"
            f"{selected_path_line}{candidate_lines}\n"
            "The role model directory may contain multiple candidates in subfolders; "
            "prioritize the selected one above.\n"
            "Read this if you need a known-good reference for behavior.\n"
        )
    fix_block = ""
    if fix_corrupted:
        fix_block = "\nTHIS TARGET IS MARKED AS CORRUPTED: ensure the mod matches the description and intended behavior.\n"

    return f"""You are an expert Minecraft mod developer. Your task is to update this mod from {current_l} {current_v} to {target_l} {target_v}.

CURRENT DATE: {current_date} (Year {current_year})

SUPPORTED MINECRAFT VERSIONS IN THIS SYSTEM (loader={target_l}): {supported_versions_text}
SUPPORTED VERSIONS FOR THIS TARGET RANGE: {range_versions_text}
LATEST SUPPORTED VERSION: {latest_supported or "unknown"}
TARGET VERSION: {target_v} (authoritative and valid in this system — do NOT change it, do NOT "correct" it, and do NOT switch versions)

MOD INFORMATION:
- Name: {mod_info.get('name', 'Unknown')}
- Mod ID: {mod_info.get('mod_id', 'unknown')}
- Current Version: {mod_info.get('mod_version', '1.0.0')}
- Description: {desc}
{fix_block}{role_model_block}

{current_v} uses SRG names (e.g., Minecraft.func_71410_x()), {target_v} uses MCP names (e.g., Minecraft.getMinecraft()).

**FOLDER STRUCTURE - IMPORTANT:**
- Original source files: `src/` (read with read_file using paths like "java/com/examplemod/Mod.java")
- Reference examples: `reference/` (read with read_reference - these show correct {target_v} patterns!)
- YOUR output: `src/java/` (write with write_mod_file - auto-adds prefix)
- Resources output: `src/resources/` (write with write_resource)

If original source has no Java/Kotlin files, treat it as missing and use the selected role model folder as your starting point.

**PATHS: The original source is under src/java/ NOT src/main/java/**

**YOUR TASK - DO THIS QUICKLY:**
1. Read 2-3 key files from reference/ first (these show correct imports/patterns)
2. Read the main mod file from src/java/ (use path like "java/com/package/Mod.java")
3. Read any other key files needed
4. Write updated files to src/java/ using write_mod_file
5. IMMEDIATELY call Build when ready - don't keep exploring!

**CRITICAL RULES:**
- Call Build as soon as you have written the essential files - don't wait!
- The build system handles gradle automatically
- If build fails, use read_build_log (with a regex pattern if needed) to find the error, fix it, and rebuild
- Don't re-read files you've already read - use the info you have
- Never change the target Minecraft version or loader
- Do NOT create or edit gradle/build config files (gradle.properties, build.gradle, settings.gradle)
- Avoid using library_* tools unless you have a specific missing class; some library context is preloaded

**TOOLS:**
- read_file: Use path "java/com/package/File.java" (src/ prefix is auto-added)
- write_mod_file: Use path "com/package/File.java" (src/java/ prefix auto-added)
- write_resource: Just filename like "mcmod.info" (src/resources/ prefix auto-added)
- build: Compile the mod - do this as soon as possible!
- read_build_log: Read build.log if a build fails (optional regex via pattern)
- complete: Mark done after successful build"""


def create_version_folder(
    output_dir: Path,
    decomposed: DecomposedMod,
    target: VersionTarget,
    context: dict[str, Any],
    trimmed_src: Path,
    manifest: dict[str, Any] | None = None,
) -> Path:
    version_folder = output_dir / f"{target.minecraft_version}-{target.loader}"
    safe_rmtree(version_folder)
    version_folder.mkdir(parents=True)

    write_json(version_folder / "versions.txt", {
        "minecraft_version": target.minecraft_version,
        "loader": target.loader,
        "slug": target.slug,
    })

    write_json(version_folder / "context.json", context)

    info_kit = version_folder / "info-kit"
    info_kit.mkdir(parents=True)

    (info_kit / "mod_info.txt").write_text(
        _format_mod_info_txt(decomposed.mod_info),
        encoding="utf-8",
    )

    if decomposed.mod_info.get("description"):
        (info_kit / "description.txt").write_text(decomposed.mod_info["description"], encoding="utf-8")

    src_dest = version_folder / "src"
    shutil.copytree(trimmed_src, src_dest)

    template_dir = context.get("template_dir", "")
    if template_dir:
        template_source = Path(template_dir)
        if template_source.exists():
            ref_dest = version_folder / "reference"
            shutil.copytree(template_source, ref_dest)
            print(f"DEBUG: Copied template to reference folder: {ref_dest}")

    return version_folder


def _format_mod_info_txt(mod_info: dict[str, Any]) -> str:
    lines = [
        f"jar_name={mod_info.get('jar_name', 'unknown.jar')}",
        f"loader={mod_info.get('loader', 'unknown')}",
        f"supported_minecraft={mod_info.get('supported_minecraft', 'unknown')}",
        f"primary_mod_id={mod_info.get('primary_mod_id', 'unknown')}",
        f"name={mod_info.get('name', 'Unknown')}",
        f"mod_version={mod_info.get('mod_version', '1.0.0')}",
        f"entrypoint_class={mod_info.get('entrypoint_class', '')}",
        f"description={mod_info.get('description', '')}",
        f"authors={', '.join(mod_info.get('authors', []))}",
    ]
    return "\n".join(lines)


def command_auto_update_decompose(args: argparse.Namespace) -> int:
    auto_fetch_modrinth = _parse_bool(
        getattr(args, "auto_fetch_modrinth", None),
        default=True,
    )
    auto_fix_corrupted = _parse_bool(
        getattr(args, "auto_fix_corrupted", None),
        default=False,
    )
    auto_fix_only = _parse_bool(
        getattr(args, "auto_fix_only", None),
        default=False,
    )
    if auto_fix_only:
        auto_fix_corrupted = True
    config = AutoUpdateConfig(
        mod_jar_path=args.mod_jar_path if hasattr(args, "mod_jar_path") else "",
        modrinth_project_url=args.modrinth_project_url if hasattr(args, "modrinth_project_url") else None,
        mod_description=args.mod_description if hasattr(args, "mod_description") else "",
        auto_fetch_modrinth=auto_fetch_modrinth,
        auto_fix_corrupted=auto_fix_corrupted,
        auto_fix_only=auto_fix_only,
        auto_fix_corrupted_downloads_dir=(
            args.auto_fix_corrupted_downloads_dir
            if hasattr(args, "auto_fix_corrupted_downloads_dir")
            else ""
        ),
        auto_fix_corrupted_decompiled_dir=(
            args.auto_fix_corrupted_decompiled_dir
            if hasattr(args, "auto_fix_corrupted_decompiled_dir")
            else ""
        ),
        auto_fix_corrupted_report_dir=(
            args.auto_fix_corrupted_report_dir
            if hasattr(args, "auto_fix_corrupted_report_dir")
            else ""
        ),
        only_target=(
            args.only_target.strip()
            if hasattr(args, "only_target") and args.only_target.strip()
            else None
        ),
        plan_only=_parse_bool(getattr(args, "plan_only", "false"), False),
        reuse_decompiled_dir=(
            args.reuse_decompiled_dir.strip()
            if hasattr(args, "reuse_decompiled_dir") and args.reuse_decompiled_dir.strip()
            else None
        ),
        version_range=args.version_range if hasattr(args, "version_range") else "all",
        update_mode=args.update_mode if hasattr(args, "update_mode") else "all-versions",
        publish_mode=args.publish_mode if hasattr(args, "publish_mode") else "bundle-only",
        manifest_path=args.manifest,
        output_dir=Path(args.output_dir),
    )

    safe_rmtree(config.output_dir)
    config.output_dir.mkdir(parents=True)

    manifest = load_json(Path(config.manifest_path))

    mod_description_input = config.mod_description.strip()
    info_txt = ""
    description_source = "none"
    if mod_description_input:
        potential_path = Path(mod_description_input)
        if potential_path.exists() and potential_path.is_file():
            info_txt = potential_path.read_text(encoding="utf-8")
            description_source = "user"
        else:
            info_txt = mod_description_input
            description_source = "user"

    mod_jar_input = config.mod_jar_path.strip()
    auto_fetch_enabled = config.auto_fetch_modrinth
    mod_jar_source = "custom"
    modrinth_project_ref = ""
    modrinth_project: dict[str, Any] | None = None
    modrinth_versions: list[dict[str, Any]] | None = None

    if mod_jar_input:
        if auto_fetch_enabled:
            print("Auto-fetch disabled because mod-jar-path was provided.")
        auto_fetch_enabled = False
        jar_path = Path(mod_jar_input)
        if not jar_path.is_absolute():
            jar_path = Path.cwd() / jar_path
        if not jar_path.exists():
            raise ModCompilerError(f"Mod jar not found: {jar_path}")
    else:
        if not auto_fetch_enabled:
            raise ModCompilerError("Mod jar path is required when auto-fetch from Modrinth is disabled.")
        if not config.modrinth_project_url:
            raise ModCompilerError("Auto-fetch from Modrinth requires a Modrinth project URL or slug.")

        modrinth_project_ref = normalize_modrinth_project_ref(config.modrinth_project_url)
        token = os.environ.get("MODRINTH_TOKEN", "").strip() or None
        modrinth_project, modrinth_versions = _fetch_modrinth_project_and_versions(modrinth_project_ref, token)

        latest_version = _select_latest_modrinth_version(modrinth_versions)
        if latest_version is None:
            raise ModCompilerError("No Modrinth versions were available to download.")
        file_info = select_primary_modrinth_file(latest_version.get("files", []))
        if not file_info:
            raise ModCompilerError("No downloadable file was found for the latest Modrinth version.")
        file_url = str(file_info.get("url", "")).strip()
        if not file_url:
            raise ModCompilerError("Modrinth file entry is missing a download URL.")
        filename = str(file_info.get("filename", "")).strip()
        if not filename:
            parsed = urllib.parse.urlparse(file_url)
            filename = Path(parsed.path).name or f"{modrinth_project_ref}.jar"
        download_dir = config.output_dir / "_downloads"
        jar_path = download_dir / filename
        _download_modrinth_file(file_url, jar_path)
        mod_jar_source = "modrinth"

    corrupted_targets: list[VersionTarget] = []
    fix_info_map: dict[str, dict[str, Any]] = {}
    role_models: dict[str, dict[str, Any]] = {}
    corruption_report_path = ""

    if config.auto_fix_corrupted:
        if not config.modrinth_project_url:
            raise ModCompilerError("auto-fix-corrupted requires a Modrinth project URL.")
        if config.auto_fix_only and not config.modrinth_project_url:
            raise ModCompilerError("auto-fix-only requires a Modrinth project URL.")
        downloads_dir = None
        if config.auto_fix_corrupted_downloads_dir:
            downloads_dir = Path(config.auto_fix_corrupted_downloads_dir)
        predecompiled_dir = None
        if config.auto_fix_corrupted_decompiled_dir:
            predecompiled_dir = Path(config.auto_fix_corrupted_decompiled_dir)
        verification_entries = None
        if config.auto_fix_corrupted_report_dir:
            report_root = Path(config.auto_fix_corrupted_report_dir)
            if report_root.exists():
                entries = []
                for path in sorted(report_root.glob("*.json")):
                    try:
                        payload = load_json(path)
                    except Exception:
                        continue
                    if isinstance(payload, dict):
                        entries.append(payload)
                if entries:
                    verification_entries = entries
        (
            corrupted_targets,
            fix_info_map,
            role_models,
            report_path,
        ) = _run_auto_fix_corrupted(
            manifest=manifest,
            modrinth_project_url=config.modrinth_project_url,
            output_dir=config.output_dir,
            downloads_dir=downloads_dir,
            predecompiled_dir=predecompiled_dir,
            verification_entries=verification_entries,
        )
        corruption_report_path = str(report_path)

    decomp_dir = config.output_dir / "_decompiled"
    if config.reuse_decompiled_dir:
        reuse_path = Path(config.reuse_decompiled_dir)
        if reuse_path.exists():
            safe_rmtree(decomp_dir)
            copy_tree(reuse_path, decomp_dir)
            src_path = decomp_dir
        else:
            decomp_result = decompile_jar_internal(jar_path, manifest, output_dir=decomp_dir)
            src_path = decomp_result.extracted_src
    else:
        decomp_result = decompile_jar_internal(jar_path, manifest, output_dir=decomp_dir)
        src_path = decomp_result.extracted_src
    mod_info = _parse_mod_info(src_path)

    if not info_txt and auto_fetch_enabled:
        if modrinth_project is None and config.modrinth_project_url:
            modrinth_project_ref = normalize_modrinth_project_ref(config.modrinth_project_url)
            token = os.environ.get("MODRINTH_TOKEN", "").strip() or None
            modrinth_project, _ = _fetch_modrinth_project_and_versions(modrinth_project_ref, token)
        if modrinth_project:
            info_txt = _extract_modrinth_description(modrinth_project)
            if info_txt:
                description_source = "modrinth"

    current_loader = mod_info.get("loader", "fabric")
    current_version = mod_info.get("supported_minecraft", "1.20.1")

    version_range_clean = config.version_range.strip().lower()
    include_all_loaders_missing = (
        config.update_mode == "missing-only"
        and version_range_clean in {"", "all"}
        and config.modrinth_project_url
    )

    if include_all_loaders_missing:
        loaders_to_include = set()
        for entry in manifest.get("ranges", []):
            loaders_to_include.update(entry.get("loaders", {}).keys())
        if not loaders_to_include:
            loaders_to_include = {current_loader}
        version_targets = _get_all_supported_versions_for_loaders(manifest, sorted(loaders_to_include))
    else:
        version_targets = parse_version_input(config.version_range, manifest, current_loader)

    modrinth_info = None
    if config.modrinth_project_url:
        try:
            loader_filter = "" if include_all_loaders_missing else current_loader
            modrinth_info = check_modrinth_versions(config.modrinth_project_url, loader_filter)
        except Exception as e:
            print(f"Warning: Could not fetch Modrinth versions: {e}", file=sys.stderr)

    filtered_targets = filter_versions_to_build(version_targets, modrinth_info, config.update_mode)
    if config.auto_fix_only:
        filtered_targets = list(corrupted_targets)
    elif corrupted_targets:
        existing_slugs = {t.slug for t in filtered_targets}
        for target in corrupted_targets:
            if target.slug not in existing_slugs:
                filtered_targets.append(target)
                existing_slugs.add(target.slug)

    if config.only_target:
        target_key = config.only_target.strip()
        target_version = ""
        target_loader = ""
        if ":" in target_key:
            target_version, target_loader = [part.strip() for part in target_key.split(":", 1)]
        elif "-" in target_key:
            maybe_version, maybe_loader = target_key.rsplit("-", 1)
            if maybe_loader in {"forge", "fabric", "neoforge"}:
                target_version = maybe_version.strip()
                target_loader = maybe_loader.strip()
        filtered_targets = [
            t for t in filtered_targets
            if t.slug == target_key
            or (target_version and target_loader and t.minecraft_version == target_version and t.loader == target_loader)
        ]
        if not filtered_targets:
            raise ModCompilerError(f"No target matched --only-target {config.only_target}")

    if not config.plan_only:
        trimmed_src = trim_src_for_context(src_path)
        src_has_sources = bool(list(trimmed_src.rglob("*.java")) or list(trimmed_src.rglob("*.kt")))
        role_model_trimmed_cache: dict[str, Path] = {}

        used_fix_versions: set[str] = set()
        for target in filtered_targets:
            mod_info_for_target = dict(mod_info)
            fix_info = fix_info_map.get(target.slug)
            if fix_info:
                old_version = fix_info.get("fix_source_version") or mod_info_for_target.get("mod_version", "0.0.0")
                new_version = _bump_mod_version(str(old_version), used_fix_versions)
                mod_info_for_target["mod_version"] = new_version
                mod_info_for_target["fix_corrupted"] = True
                mod_info_for_target["fix_source_version"] = old_version
                if fix_info.get("fix_source_modrinth_id"):
                    mod_info_for_target["fix_source_modrinth_id"] = fix_info["fix_source_modrinth_id"]

            decomposed = DecomposedMod(
                src_path=src_path,
                mod_info=mod_info_for_target,
                current_version=current_version,
                current_loader=current_loader,
                metadata=mod_info_for_target,
            )

            effective_src = trimmed_src
            if not src_has_sources and target.loader in role_models:
                model_info = role_models[target.loader]
                candidates = model_info.get("candidates", []) or []
                selected = _select_closest_role_model(target.minecraft_version, candidates)
                if selected and selected.get("version_id") and selected.get("path"):
                    selected_id = str(selected.get("version_id"))
                    if selected_id in role_model_trimmed_cache:
                        effective_src = role_model_trimmed_cache[selected_id]
                    else:
                        selected_src = Path(selected.get("path"))
                        if selected_src.exists():
                            effective_src = trim_src_for_context(selected_src)
                            role_model_trimmed_cache[selected_id] = effective_src
                            mod_info_for_target["source_origin"] = "role_model_selected"
            context = generate_version_context(decomposed, target, info_txt, effective_src, manifest)
            if fix_info:
                context["fix_corrupted"] = True
                context["fix_source_version"] = mod_info_for_target.get("fix_source_version", "")
                context["fix_source_modrinth_id"] = mod_info_for_target.get("fix_source_modrinth_id", "")
            if target.loader in role_models:
                model_info = role_models[target.loader]
                candidates = model_info.get("candidates", []) or []
                selected = _select_closest_role_model(target.minecraft_version, candidates)
                context["role_model_dir"] = "reference/role-model"
                context["role_model_unverified"] = bool(model_info.get("unverified"))
                context["role_model_loader"] = target.loader
                context["role_model_candidates"] = [
                    {
                        "version_id": c.get("version_id", ""),
                        "version_number": c.get("version_number", ""),
                        "game_versions": c.get("game_versions", []),
                        "rating": c.get("rating", 0.0),
                        "tag": c.get("tag", ""),
                    }
                    for c in candidates
                ]
                if selected:
                    context["role_model_version"] = selected.get("version_number", "")
                    context["role_model_selected_version_id"] = selected.get("version_id", "")
                    context["role_model_selected_rating"] = selected.get("rating", 0.0)
                    context["role_model_selected_game_versions"] = selected.get("game_versions", [])
                    selected_id = selected.get("version_id", "")
                    if selected_id:
                        context["role_model_selected_subdir"] = re.sub(r"[^A-Za-z0-9._-]+", "_", str(selected_id))
                        context["role_model_selected_path"] = "reference/role-model/selected"
                        context["role_model_selected_source_subdir"] = f"reference/role-model/{context['role_model_selected_subdir']}"

            version_folder = create_version_folder(config.output_dir, decomposed, target, context, effective_src, manifest)
            role_model = role_models.get(target.loader)
            if role_model:
                ref_root = version_folder / "reference"
                ref_root.mkdir(parents=True, exist_ok=True)
                role_dest = ref_root / "role-model"
                safe_rmtree(role_dest)
                role_dest.mkdir(parents=True, exist_ok=True)
                for cand in role_model.get("candidates", []) or []:
                    source_path = Path(cand.get("path", ""))
                    version_id = cand.get("version_id") or "candidate"
                    subdir = role_dest / re.sub(r"[^A-Za-z0-9._-]+", "_", str(version_id))
                    safe_rmtree(subdir)
                    if source_path.exists():
                        copy_tree(source_path, subdir)
                selected_subdir = context.get("role_model_selected_subdir")
                if selected_subdir:
                    selected_path = role_dest / selected_subdir
                    if selected_path.exists():
                        selected_alias = role_dest / "selected"
                        safe_rmtree(selected_alias)
                        copy_tree(selected_path, selected_alias)

    if config.modrinth_project_url and not modrinth_project_ref:
        modrinth_project_ref = normalize_modrinth_project_ref(config.modrinth_project_url)

    state = {
        "mod_jar_path": str(jar_path),
        "mod_jar_source": mod_jar_source,
        "modrinth_project_url": config.modrinth_project_url,
        "modrinth_project_ref": modrinth_project_ref,
        "info_txt": info_txt,
        "description_source": description_source,
        "auto_fetch_modrinth_requested": config.auto_fetch_modrinth,
        "auto_fetch_modrinth_effective": auto_fetch_enabled,
        "auto_fix_corrupted": config.auto_fix_corrupted,
        "auto_fix_only": config.auto_fix_only,
        "corruption_report": corruption_report_path,
        "role_models": role_models,
        "current_version": current_version,
        "current_loader": current_loader,
        "version_targets": [
            {"minecraft_version": t.minecraft_version, "loader": t.loader, "slug": t.slug}
            for t in filtered_targets
        ],
        "update_mode": config.update_mode,
        "publish_mode": config.publish_mode,
        "modrinth_info": modrinth_info,
    }
    write_json(config.output_dir / "state.json", state)

    matrix = {"include": [{"slug": t.slug, "version": t.minecraft_version, "loader": t.loader} for t in filtered_targets]}
    write_json(config.output_dir / "matrix.json", matrix)

    print(json.dumps(matrix, separators=(",", ":")))
    return 0


def _parse_mod_info(src_path: Path) -> dict[str, Any]:
    mod_info_path = src_path / "mod_info.txt"
    if mod_info_path.exists():
        return _parse_key_value_file(mod_info_path)

    fabric_json = src_path / "fabric.mod.json"
    if fabric_json.exists():
        data = load_json(fabric_json)
        return {
            "jar_name": "unknown.jar",
            "loader": "fabric",
            "supported_minecraft": data.get("schema_version", "1.20"),
            "primary_mod_id": data.get("id", "unknown"),
            "name": data.get("name", "Unknown"),
            "mod_version": data.get("version", "1.0.0"),
            "entrypoint_class": data.get("entrypoint", ""),
            "description": data.get("description", ""),
            "authors": data.get("authors", []),
        }

    mods_toml = src_path / "mods.toml"
    if mods_toml.exists():
        text = mods_toml.read_text(encoding="utf-8")
        mod_id_match = re.search(r'modId\s*=\s*"([^"]+)"', text)
        name_match = re.search(r'name\s*=\s*"([^"]+)"', text)
        version_match = re.search(r'version\s*=\s*"([^"]+)"', text)
        return {
            "jar_name": "unknown.jar",
            "loader": "forge",
            "supported_minecraft": "1.20",
            "primary_mod_id": mod_id_match.group(1) if mod_id_match else "unknown",
            "name": name_match.group(1) if name_match else "Unknown",
            "mod_version": version_match.group(1) if version_match else "1.0.0",
            "entrypoint_class": "",
            "description": "",
            "authors": [],
        }

    return {
        "jar_name": "unknown.jar",
        "loader": "fabric",
        "supported_minecraft": "1.20",
        "primary_mod_id": "unknown",
        "name": "Unknown",
        "mod_version": "1.0.0",
        "entrypoint_class": "",
        "description": "",
        "authors": [],
    }


def _parse_key_value_file(path: Path) -> dict[str, Any]:
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def load_state(state_path: Path) -> dict[str, Any]:
    return load_json(state_path)


def command_ai_rebuild(args: argparse.Namespace) -> int:
    import sys
    print(f"DEBUG: Starting ai-rebuild", file=sys.stderr)
    
    version_dir = Path(args.version_dir)
    output_dir = Path(args.output_dir)
    artifact_dir = Path(args.artifact_dir)
    
    print(f"DEBUG: version_dir={version_dir}, exists={version_dir.exists()}", file=sys.stderr)
    print(f"DEBUG: output_dir={output_dir}, exists={output_dir.exists()}", file=sys.stderr)
    print(f"DEBUG: artifact_dir={artifact_dir}", file=sys.stderr)

    safe_rmtree(artifact_dir)
    artifact_dir.mkdir(parents=True)
    output_dir.mkdir(parents=True, exist_ok=True)

    versions_txt = version_dir / "versions.txt"
    context_json = version_dir / "context.json"

    if not versions_txt.exists():
        raise ModCompilerError(f"versions.txt not found in {version_dir}")
    if not context_json.exists():
        raise ModCompilerError(f"context.json not found in {version_dir}")

    version_info = load_json(versions_txt)
    context = load_json(context_json)
    
    print(f"DEBUG: Loaded version_info={version_info}", file=sys.stderr)
    print(f"DEBUG: context target_version={context.get('target_version')}", file=sys.stderr)

    target_version = version_info["minecraft_version"]
    target_loader = version_info["loader"]
    slug = version_info["slug"]

    result: dict[str, Any] = {
        "slug": slug,
        "loader": target_loader,
        "minecraft_version": target_version,
        "status": "failed",
        "chat_history": [],
        "warnings": [],
    }
    result["metadata"] = context.get("mod_info", {})

    log_path = artifact_dir / "ai_rebuild.log"
    messages = []
    build_attempted = False
    build_success = False

    try:
        import os
        print(f"DEBUG: Checking for OpenRouter keys in env...", file=sys.stderr)
        raw_keys = os.environ.get("OPENROUTER_API_KEY", "")
        parsed_keys = [line.strip() for line in raw_keys.splitlines() if line.strip()] if raw_keys else []
        print(
            f"DEBUG: OPENROUTER_API_KEY = {'set' if raw_keys else 'not set'}; parsed_keys={len(parsed_keys)}",
            file=sys.stderr,
        )
        
        from modcompiler.openrouter import OpenRouterClient

        client = OpenRouterClient()
        print(f"DEBUG: OpenRouterClient initialized with {len(client.key_states)} keys", file=sys.stderr)
        
        if not client.key_states:
            raise ModCompilerError("No OpenRouter API keys available. Please set the OPENROUTER_API_KEY secret.")

        print(f"DEBUG: Building system prompt...", file=sys.stderr)
        system_prompt = build_ai_system_prompt(context)
        messages = [
            {"role": "system", "content": system_prompt},
        ]

        src_dir = version_dir / "src"
        ref_dir = version_dir / "reference"
        template_dir = context.get("template_dir", "")
        if template_dir and not ref_dir.exists():
            template_source = Path(template_dir)
            if template_source.exists():
                shutil.copytree(template_source, ref_dir)
                print(f"DEBUG: Copied template to reference folder: {ref_dir}", file=sys.stderr)
            else:
                print(f"DEBUG: Template directory missing: {template_source}", file=sys.stderr)

        def get_dir_tree(path: Path, prefix: str = "") -> str:
            if not path.exists():
                return ""
            items = []
            for item in sorted(path.iterdir()):
                rel = item.relative_to(path)
                items.append(f"{prefix}{rel}{'/' if item.is_dir() else ''}")
            return "\n".join(items)

        def get_all_java_files(base_dir: Path) -> list[tuple[str, str]]:
            files = []
            if base_dir.exists():
                for f in sorted(base_dir.rglob("*")):
                    if f.suffix not in {".java", ".kt", ".kts"}:
                        continue
                    try:
                        content = f.read_text(encoding="utf-8")
                        rel_path = str(f.relative_to(base_dir))
                        files.append((rel_path, content))
                    except Exception:
                        pass
            return files

        def get_all_ref_files(base_dir: Path) -> list[tuple[str, str]]:
            files = []
            if base_dir.exists():
                for f in sorted(base_dir.rglob("*")):
                    if f.is_file() and f.suffix in [".java", ".kt", ".kts", ".info", ".mcmeta", ".json"]:
                        try:
                            content = f.read_text(encoding="utf-8")
                            rel_path = str(f.relative_to(base_dir))
                            files.append((rel_path, content))
                        except Exception:
                            pass
            return files

        def guess_library_queries(source_files: list[tuple[str, str]]) -> list[str]:
            combined = "\n".join(content for _, content in source_files).lower()
            queries: list[str] = []

            def add(query: str) -> None:
                if query not in queries:
                    queries.append(query)

            add("Minecraft")
            if any(token in combined for token in ("overlay", "hud", "gui", "render", "screen")):
                add("GuiGraphics")
                add("Screen")
                add("Font")
            if "entity" in combined:
                add("Entity")
            if "world" in combined or "level" in combined:
                add("Level")
            if "block" in combined:
                add("Block")
            if "item" in combined:
                add("Item")
            return queries[:5]

        def extract_first_match(search_result: str) -> str | None:
            for line in search_result.splitlines():
                stripped = line.strip()
                if stripped.startswith("- "):
                    return stripped[2:].strip()
            return None

        src_tree = get_dir_tree(src_dir)
        ref_tree = get_dir_tree(ref_dir) if ref_dir.exists() else ""
        
        initial_msg = f"""Update this mod for {target_loader} {target_version}.

USER DESCRIPTION:
{context.get('user_description', 'No description provided')}

DIRECTORY STRUCTURE - ORIGINAL SOURCE:
{src_tree}

DIRECTORY STRUCTURE - REFERENCE TEMPLATE:
{ref_tree}

The above shows all available files. Use read_file, read_reference tools to read them.

PATH INFO:
- READ original source: "java/com/package/File.java"
- WRITE updated source: "com/package/File.java" (goes to src/java/)
- Resources: "mcmod.info" (goes to src/resources/)

ACTION: Read files, update them, write to src/java/, then call build."""

        messages = [
            {"role": "system", "content": system_prompt},
        ]

        all_src_contents = []
        for rel_path, content in get_all_java_files(src_dir):
            all_src_contents.append((rel_path, content))

        all_ref_contents = []
        for rel_path, content in get_all_ref_files(ref_dir) if ref_dir.exists() else []:
            all_ref_contents.append((rel_path, content))

        role_model_note = ""
        if context.get("role_model_dir"):
            label = "ROLE MODEL AVAILABLE"
            if context.get("role_model_unverified"):
                label = "ROLE MODEL AVAILABLE (UNVERIFIED)"
            role_loader = context.get("role_model_loader") or target_loader
            candidates = context.get("role_model_candidates", [])
            selected_rating = context.get("role_model_selected_rating", "")
            selected_version = context.get("role_model_version", "unknown")
            selected_path = context.get("role_model_selected_path", "")
            selected_source_subdir = context.get("role_model_selected_source_subdir", "")
            candidate_lines = ""
            if candidates:
                summary = []
                for cand in candidates:
                    tag = cand.get("tag", "")
                    summary.append(
                        f"{cand.get('version_number', cand.get('version_id', 'unknown'))} "
                        f"(tag {tag or 'unknown'}, rating {cand.get('rating', 0)}/20, "
                        f"mc={','.join(cand.get('game_versions', [])) or 'unknown'})"
                    )
                candidate_lines = "\nTop candidates:\n- " + "\n- ".join(summary)
            selected_path_line = ""
            if selected_path:
                selected_path_line = f"\nSelected candidate folder: {selected_path}\n"
            if selected_source_subdir:
                selected_path_line += f"Selected source subfolder: {selected_source_subdir}\n"
            role_model_note = (
                f"\n{label}: {context.get('role_model_dir')} "
                f"(built for loader {role_loader}, version {selected_version}, rating {selected_rating or 'unknown'}/20)\n"
                f"Target loader/version: {target_loader} {target_version}\n"
                f"{selected_path_line}{candidate_lines}\n"
                "Role model subfolders may contain multiple candidates; prefer the selected one.\n"
            )
        fix_note = "\nNOTE: This target is marked as corrupted; ensure behavior matches the description.\n" if context.get("fix_corrupted") else ""

        user_msg_1 = f"""Update this mod for {target_loader} {target_version}.

USER DESCRIPTION:
{context.get('user_description', 'No description provided')}

TARGET VERSION: {target_version} (valid in this system — do NOT change it)
{fix_note}{role_model_note}

PATH INFO:
- READ original source: "java/com/package/File.java"
- WRITE updated source: "com/package/File.java" (goes to src/java/)
- RESOURCES: "mcmod.info" (goes to src/resources/)

NOTE: If src/java has no .java/.kt files, treat the original source as missing and start from the selected role model folder.

ACTION: Update files for {target_version}, write to src/java/, then build."""

        tool_calls_1 = [
            {"id": "call_1", "type": "function", "function": {"name": "list_files", "arguments": json.dumps({"path": "."})}},
            {"id": "call_2", "type": "function", "function": {"name": "list_reference", "arguments": json.dumps({"path": "."})}},
        ]

        list_files_result = _tool_list_files(version_dir, {"path": "."})
        list_reference_result = _tool_list_reference(version_dir, {"path": "."}) if ref_dir.exists() else "Error: reference folder not found"

        def _format_read_file_result(path: str, content: str) -> str:
            if len(content) > 10000:
                content = content[:10000] + "\n... (truncated)"
            return f"File: {path}\n```\n{content}\n```"

        def _format_read_reference_result(path: str, content: str) -> str:
            if len(content) > 15000:
                content = content[:15000] + "\n... (truncated)"
            return f"Reference file: {path}\n```\n{content}\n```"

        all_file_read_calls = []
        all_file_read_results: list[dict[str, str]] = []
        for rel_path, content in all_ref_contents:
            call_id = f"call_ref_{len(all_file_read_calls)+1}"
            all_file_read_calls.append({
                "id": call_id,
                "type": "function",
                "function": {"name": "read_reference", "arguments": json.dumps({"path": rel_path})},
            })
            all_file_read_results.append({
                "role": "tool",
                "tool_call_id": call_id,
                "content": _format_read_reference_result(rel_path, content),
            })
        for rel_path, content in all_src_contents:
            call_id = f"call_src_{len(all_file_read_calls)+1}"
            all_file_read_calls.append({
                "id": call_id,
                "type": "function",
                "function": {"name": "read_file", "arguments": json.dumps({"path": rel_path})},
            })
            all_file_read_results.append({
                "role": "tool",
                "tool_call_id": call_id,
                "content": _format_read_file_result(rel_path, content),
            })

        messages.append({"role": "user", "content": user_msg_1})

        messages.append({
            "role": "assistant",
            "content": "",
            "tool_calls": tool_calls_1,
        })
        messages.append({
            "role": "tool",
            "tool_call_id": "call_1",
            "content": list_files_result,
        })
        messages.append({
            "role": "tool",
            "tool_call_id": "call_2",
            "content": list_reference_result,
        })

        messages.append({
            "role": "assistant",
            "content": "",
            "tool_calls": all_file_read_calls,
        })
        messages.extend(all_file_read_results)

        lib_sources = _get_library_sources(version_dir, target_version, target_loader)
        if lib_sources:
            library_queries = guess_library_queries(all_src_contents)
            search_calls: list[dict[str, Any]] = []
            search_results: list[dict[str, str]] = []
            read_calls: list[dict[str, Any]] = []
            read_results: list[dict[str, str]] = []
            reads_used = 0

            for query in library_queries:
                call_id = f"call_lib_search_{len(search_calls) + 1}"
                search_calls.append({
                    "id": call_id,
                    "type": "function",
                    "function": {"name": "library_search", "arguments": json.dumps({"query": query})},
                })
                search_result = _tool_library_search({"query": query}, lib_sources)
                search_results.append({
                    "role": "tool",
                    "tool_call_id": call_id,
                    "content": search_result,
                })

                if reads_used < 2 and search_result.startswith("Found"):
                    match_path = extract_first_match(search_result)
                    if match_path:
                        read_id = f"call_lib_read_{len(read_calls) + 1}"
                        read_calls.append({
                            "id": read_id,
                            "type": "function",
                            "function": {"name": "library_read", "arguments": json.dumps({"path": match_path})},
                        })
                        read_results.append({
                            "role": "tool",
                            "tool_call_id": read_id,
                            "content": _tool_library_read({"path": match_path}, lib_sources),
                        })
                        reads_used += 1

            if search_calls:
                messages.append({
                    "role": "assistant",
                    "content": "",
                    "tool_calls": search_calls,
                })
                messages.extend(search_results)
            if read_calls:
                messages.append({
                    "role": "assistant",
                    "content": "",
                    "tool_calls": read_calls,
                })
                messages.extend(read_results)

        messages.append({
            "role": "assistant",
            "content": f"I have all the source and reference files. Now I'll update the mod from {context.get('current_loader')} {context.get('current_version')} to {target_loader} {target_version}. I'll write updated files to src/java/ and then build."
        })

        debug_history_path = artifact_dir / "ai_context_debug.txt" if artifact_dir else None
        if debug_history_path:
            debug_lines = []
            for i, m in enumerate(messages):
                debug_lines.append(f"\n=== MESSAGE {i}: role={m.get('role')} ===")
                if "tool_calls" in m:
                    debug_lines.append(f"tool_calls: {len(m.get('tool_calls', []))} calls")
                content = m.get("content", "")
                if len(content) > 1500:
                    debug_lines.append(content)
                else:
                    debug_lines.append(content)
            debug_content = "\n".join(debug_lines)
            debug_history_path.write_text(debug_content, encoding="utf-8")
            print(f"DEBUG: Saved AI context to {debug_history_path}", file=sys.stderr)
            print(f"DEBUG: Total messages: {len(messages)}", file=sys.stderr)

        max_iterations = 1000
        print(f"DEBUG: Starting AI conversation loop (max {max_iterations} iterations)...", file=sys.stderr)
        
        files_written_count = 0
        last_tool = None
        tools_without_build = 0
        api_call_count = 0
        api_latency_total = 0.0
        api_latency_samples: list[float] = []
        total_tool_calls = 0
        
        for iteration in range(max_iterations):
            print(f"DEBUG: Iteration {iteration+1}: Calling API...", file=sys.stderr)
            try:
                call_started = time.perf_counter()
                response = None
                timeout_retries = 20
                last_timeout: Exception | None = None
                for attempt in range(1, timeout_retries + 1):
                    try:
                        response = client.chat_completion_with_fallback(
                            messages,
                            temperature=0.7,
                            max_tokens=4000,
                            tools=get_tools_definition(),
                        )
                        last_timeout = None
                        break
                    except Exception as e:
                        msg = str(e).lower()
                        is_timeout = isinstance(e, TimeoutError) or "timed out" in msg or "timeout" in msg
                        if not is_timeout:
                            raise
                        last_timeout = e
                        print(f"DEBUG: API timeout on attempt {attempt}/{timeout_retries}: {e}", file=sys.stderr)
                        if attempt < timeout_retries:
                            time.sleep(min(5.0, 0.5 * attempt))
                if response is None:
                    raise last_timeout or ModCompilerError("OpenRouter timed out without a response.")
                call_elapsed = time.perf_counter() - call_started
                api_call_count += 1
                api_latency_total += call_elapsed
                api_latency_samples.append(call_elapsed)
            except Exception as e:
                print(f"DEBUG: API call failed: {e}", file=sys.stderr)
                raise
            
            print(f"DEBUG: Got response, processing...", file=sys.stderr)
            print(f"DEBUG: response keys: {response.keys()}", file=sys.stderr)
            print(f"DEBUG: choices: {response.get('choices', [])[:1]}", file=sys.stderr)
            
            choice = response["choices"][0]
            assistant_payload = choice.get("message", {})
            assistant_message = assistant_payload.get("content") or ""
            tool_calls = assistant_payload.get("tool_calls", [])
            total_tool_calls += len(tool_calls)

            assistant_entry: dict[str, Any] = {"role": "assistant", "content": assistant_message}
            if tool_calls:
                assistant_entry["tool_calls"] = tool_calls
            messages.append(assistant_entry)
            print(f"DEBUG: tool_calls: {len(tool_calls)} found", file=sys.stderr)
            
            if not tool_calls:
                msg_preview = (assistant_message[:200] + "...") if assistant_message else "(no content)"
                print(f"DEBUG: No tool calls, assistant message: {msg_preview}", file=sys.stderr)
                print(f"DEBUG: Sending continue prompt...", file=sys.stderr)
                messages.append({
                    "role": "user", 
                    "content": "Please continue with a tool call. Don't respond without using a tool - use Read File, List Files, File Write, Move File, or Build when ready to continue updating the mod."
                })
                continue

            for tool_call in tool_calls:
                function = tool_call.get("function", {})
                name = function.get("name", "")
                arguments_str = function.get("arguments", "{}")
                print(f"DEBUG: Processing tool_call: name={name}, args={arguments_str[:100]}...", file=sys.stderr)

                try:
                    arguments = json.loads(arguments_str) if arguments_str else {}
                except json.JSONDecodeError:
                    arguments = {}

                result_msg = ""
                try:
                    if name == "read_file":
                        print(f"DEBUG: Calling _tool_read_file...", file=sys.stderr)
                        result_msg = _tool_read_file(version_dir, arguments)
                    elif name == "list_files":
                        print(f"DEBUG: Calling _tool_list_files...", file=sys.stderr)
                        result_msg = _tool_list_files(version_dir, arguments)
                    elif name == "write_mod_file":
                        print(f"DEBUG: Calling _tool_write_mod_file...", file=sys.stderr)
                        result_msg = _tool_write_mod_file(version_dir, arguments)
                    elif name == "write_resource":
                        print(f"DEBUG: Calling _tool_write_resource...", file=sys.stderr)
                        result_msg = _tool_write_resource(version_dir, arguments)
                    elif name == "read_reference":
                        print(f"DEBUG: Calling _tool_read_reference...", file=sys.stderr)
                        result_msg = _tool_read_reference(version_dir, arguments)
                    elif name == "list_reference":
                        print(f"DEBUG: Calling _tool_list_reference...", file=sys.stderr)
                        result_msg = _tool_list_reference(version_dir, arguments)
                    elif name == "copy_to_structure":
                        print(f"DEBUG: Calling _tool_copy_to_structure...", file=sys.stderr)
                        result_msg = _tool_copy_to_structure(version_dir, arguments)
                    elif name == "file_write":
                        print(f"DEBUG: Calling _tool_file_write...", file=sys.stderr)
                        result_msg = _tool_file_write(version_dir, arguments)
                    elif name == "file_edit":
                        print(f"DEBUG: Calling _tool_file_edit...", file=sys.stderr)
                        result_msg = _tool_file_edit(version_dir, arguments)
                    elif name == "move_file":
                        print(f"DEBUG: Calling _tool_move_file...", file=sys.stderr)
                        result_msg = _tool_move_file(version_dir, arguments)
                    elif name == "build":
                        print(f"DEBUG: Calling _tool_build...", file=sys.stderr)
                        result_msg, build_success = _tool_build(version_dir, artifact_dir, context, arguments)
                        build_attempted = True
                        tools_without_build = 0
                    elif name == "read_build_log":
                        print(f"DEBUG: Calling _tool_read_build_log...", file=sys.stderr)
                        result_msg = _tool_read_build_log(artifact_dir, version_dir, arguments)
                    elif name == "library_dir":
                        print(f"DEBUG: Calling _tool_library_dir...", file=sys.stderr)
                        lib_sources = _get_library_sources(version_dir, context.get("target_version", ""), context.get("target_loader", ""))
                        result_msg = _tool_library_dir(arguments, lib_sources)
                    elif name == "library_read":
                        print(f"DEBUG: Calling _tool_library_read...", file=sys.stderr)
                        lib_sources = _get_library_sources(version_dir, context.get("target_version", ""), context.get("target_loader", ""))
                        result_msg = _tool_library_read(arguments, lib_sources)
                    elif name == "library_search":
                        print(f"DEBUG: Calling _tool_library_search...", file=sys.stderr)
                        lib_sources = _get_library_sources(version_dir, context.get("target_version", ""), context.get("target_loader", ""))
                        result_msg = _tool_library_search(arguments, lib_sources)
                    elif name == "write_mod_file" or name == "write_resource":
                        files_written_count += 1
                        tools_without_build += 1
                    elif name == "complete":
                        print(f"DEBUG: Processing complete tool...", file=sys.stderr)
                        if build_attempted and build_success:
                            result["status"] = "success"
                            result_msg = "Marked as complete. Build was successful."
                        else:
                            result_msg = "Cannot complete: No successful build found. Use Build tool first."
                    else:
                        result_msg = f"Unknown tool: {name}"
                except Exception as tool_e:
                    print(f"DEBUG: Tool execution error: {tool_e}", file=sys.stderr)
                    result_msg = f"Error executing tool: {tool_e}"

                messages.append({
                    "role": "tool",
                    "tool_call_id": tool_call.get("id", "unknown"),
                    "content": result_msg,
                })

            if tools_without_build >= 3 and not build_attempted:
                nudge = f"\n\n[HINT: You've written {files_written_count} files but haven't tried building yet. Call 'build' now to compile!]"
                messages.append({"role": "user", "content": nudge})
                tools_without_build = 0
                print(f"DEBUG: Sending build nudge...", file=sys.stderr)

            if result["status"] == "success":
                print(f"DEBUG: Build successful, exiting loop", file=sys.stderr)
                break

        print(f"DEBUG: AI loop completed. result_status={result['status']}, build_attempted={build_attempted}, build_success={build_success}", file=sys.stderr)
        assistant_message_count = sum(1 for m in messages if m.get("role") == "assistant")
        result["ai_metrics"] = {
            "api_calls": api_call_count,
            "api_latency_seconds_total": api_latency_total,
            "api_latency_seconds_avg": (api_latency_total / api_call_count) if api_call_count else 0.0,
            "assistant_messages": assistant_message_count,
            "tool_calls": total_tool_calls,
        }
        result["chat_history"] = messages[:50]

    except Exception as e:
        import traceback
        print(f"DEBUG: Exception caught: {e}", file=sys.stderr)
        print(f"DEBUG: Traceback: {traceback.format_exc()}", file=sys.stderr)
        result["warnings"].append(str(e))
        with log_path.open("w", encoding="utf-8") as f:
            f.write(f"AI rebuild error: {e}\n")
            f.write(traceback.format_exc())
            if messages:
                f.write("\n--- Messages ---\n")
                f.write("\n".join(str(m) for m in messages))

    print(f"DEBUG: Writing result.json with status: {result.get('status')}", file=sys.stderr)
    write_json(artifact_dir / "result.json", result)
    print(f"DEBUG: Returning exit code: {0 if result['status'] == 'success' else 1}", file=sys.stderr)
    return 0 if result["status"] == "success" else 1


def _list_src_files(src_dir: Path) -> list[dict[str, Any]]:
    files = []
    if src_dir.exists():
        for f in sorted(src_dir.rglob("*")):
            if f.is_file():
                files.append({
                    "path": str(f.relative_to(src_dir)),
                    "size": f.stat().st_size,
                })
    return files


def _generate_src_summary(src_dir: Path, files: list[dict[str, Any]]) -> str:
    if not files:
        return "No source files found."

    summary_lines = []
    for f in files[:20]:
        summary_lines.append(f"  - {f['path']} ({f['size']} bytes)")

    if len(files) > 20:
        summary_lines.append(f"  ... and {len(files) - 20} more files")

    return "\n".join(summary_lines)


def _tool_read_file(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    if not path:
        return "Error: path is required"

    path = _normalize_src_path(path, for_file=True)
    if not path:
        return "Error: path is required"

    file_path = version_dir / "src" / path
    if not file_path.exists():
        available = []
        src_dir = version_dir / "src"
        if src_dir.exists():
            for f in sorted(src_dir.rglob("*")):
                if f.is_file() and f.suffix in {".java", ".kt", ".kts"}:
                    rel = f.relative_to(src_dir)
                    available.append(str(rel))
                if len(available) >= 10:
                    break
        avail_str = "\n  - ".join(available) if available else "none"
        return (
            f"File not found: {path}\n\nAvailable source files:\n  - {avail_str}\n\n"
            "Tip: Use path like 'java/com/package/ClassName.java'"
        )

    try:
        content = file_path.read_text(encoding="utf-8")
        if len(content) > 10000:
            content = content[:10000] + "\n... (truncated)"
        return f"File: {path}\n```\n{content}\n```"
    except Exception as e:
        return f"Error reading file: {e}"


def _tool_read_build_log(artifact_dir: Path, version_dir: Path, args: dict[str, str]) -> str:
    pattern = args.get("pattern")
    tail_lines = args.get("tail_lines")
    try:
        tail_lines = int(tail_lines) if tail_lines is not None else 200
    except (TypeError, ValueError):
        tail_lines = 200

    log_path = artifact_dir / "build.log"
    if not log_path.exists():
        fallback = version_dir / "build.log"
        if fallback.exists():
            log_path = fallback
        else:
            return "Error: build.log not found."

    try:
        content = log_path.read_text(encoding="utf-8", errors="replace")
    except Exception as e:
        return f"Error reading build.log: {e}"

    lines = content.splitlines()
    if pattern:
        try:
            regex = re.compile(pattern)
        except re.error as e:
            return f"Error: invalid regex pattern: {e}"
        matches = []
        for idx, line in enumerate(lines, start=1):
            if regex.search(line):
                matches.append(f"{idx}: {line}")
        if not matches:
            return f"No matches for pattern: {pattern}"
        return "Matches:\n" + "\n".join(matches[:200])

    if tail_lines <= 0:
        tail_lines = 200
    tail = "\n".join(lines[-tail_lines:])
    return f"Last {min(tail_lines, len(lines))} lines:\n{tail}"


def _normalize_src_path(raw: str, *, for_file: bool) -> str:
    path = (raw or "").strip().lstrip("/")
    if not path or path == ".":
        return "" if for_file else "."
    if path == "src":
        return "" if for_file else "."
    if path.startswith("src/"):
        path = path[4:]
    if path.startswith("main/"):
        path = path[5:]

    roots = ("java", "resources", "kotlin", "client")
    if any(path == root or path.startswith(root + "/") for root in roots):
        return path

    return f"java/{path}" if for_file or path else path


def _tool_list_files(version_dir: Path, args: dict[str, str]) -> str:
    raw_path = args.get("path", ".")
    path = _normalize_src_path(raw_path, for_file=False)

    dir_path = version_dir / "src"
    if path and path != ".":
        dir_path = dir_path / path
    if not dir_path.exists():
        return (
            f"Directory not found: src/{path}\n\n"
            "Original source is in src/java (and src/resources/src/kotlin if present). "
            "Try 'java', 'resources', or 'src/main/java'."
        )

    items = []
    for item in sorted(dir_path.iterdir()):
        rel_path = item.relative_to(version_dir / "src")
        items.append(f"  - {rel_path}{'/' if item.is_dir() else ''}")

    return "\n".join(items) if items else "  (empty)"


def _tool_read_template(version_dir: Path, context: dict[str, Any], args: dict[str, str]) -> str:
    path = args.get("path", "")
    if not path:
        return "Error: path is required"
    
    template_dir = context.get("template_dir", "")
    if not template_dir:
        return "Error: No template directory configured"
    
    full_template_path = Path(template_dir) / path
    if not full_template_path.exists():
        return f"Error: template file not found: {path}"
    
    try:
        content = full_template_path.read_text(encoding="utf-8")
        if len(content) > 15000:
            content = content[:15000] + "\n... (truncated)"
        return f"Template file: {path}\n```\n{content}\n```"
    except Exception as e:
        return f"Error reading template file: {e}"


def _tool_list_template(version_dir: Path, context: dict[str, Any], args: dict[str, str]) -> str:
    path = args.get("path", "")
    
    template_dir = context.get("template_dir", "")
    if not template_dir:
        return "Error: No template directory configured"
    
    full_template_path = Path(template_dir) / path if path else Path(template_dir)
    if not full_template_path.exists():
        return f"Error: template directory not found: {path or template_dir}"
    
    items = []
    for item in sorted(full_template_path.rglob("*")):
        if item.is_file():
            rel_path = item.relative_to(Path(template_dir))
            items.append(f"  - {rel_path}")
    
    return "\n".join(items) if items else "  (empty)"


def _tool_write_mod_file(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    content = args.get("content", "")

    if not path:
        return "Error: path is required"
    
    if not path.startswith("src/java/"):
        if path.startswith("src/main/java/"):
            path = path.replace("src/main/java/", "src/java/")
        elif path.startswith("main/java/"):
            path = path.replace("main/java/", "src/java/")
        elif not path.startswith("src/"):
            path = "src/java/" + path

    file_path = version_dir / path
    file_path.parent.mkdir(parents=True, exist_ok=True)
    file_path.write_text(content, encoding="utf-8")

    return f"Java file written to {path} ({len(content)} bytes)"


def _tool_write_resource(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    content = args.get("content", "")

    if not path:
        return "Error: path is required"
    
    if not path.startswith("src/resources/"):
        if path.startswith("src/main/resources/"):
            path = path.replace("src/main/resources/", "src/resources/")
        elif path.startswith("main/resources/"):
            path = path.replace("main/resources/", "src/resources/")
        elif not path.startswith("src/"):
            path = "src/resources/" + path

    file_path = version_dir / path
    file_path.parent.mkdir(parents=True, exist_ok=True)
    file_path.write_text(content, encoding="utf-8")

    return f"Resource written to {path} ({len(content)} bytes)"


def _tool_read_reference(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    if not path:
        return "Error: path is required"
    
    ref_dir = version_dir / "reference"
    if not ref_dir.exists():
        return "Error: reference folder not found"
    
    file_path = ref_dir / path
    if not file_path.exists():
        return f"Error: reference file not found: {path}"
    
    try:
        content = file_path.read_text(encoding="utf-8")
        if len(content) > 15000:
            content = content[:15000] + "\n... (truncated)"
        return f"Reference file: {path}\n```\n{content}\n```"
    except Exception as e:
        return f"Error reading reference: {e}"


def _tool_list_reference(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    
    ref_dir = version_dir / "reference"
    if not ref_dir.exists():
        return "Error: reference folder not found"
    
    if path:
        full_path = ref_dir / path
    else:
        full_path = ref_dir
    
    if not full_path.exists():
        return f"Error: reference path not found: {path}"
    
    items = []
    for item in sorted(full_path.rglob("*")):
        if item.is_file():
            rel_path = item.relative_to(ref_dir)
            items.append(f"  - {rel_path}")
    
    return "\n".join(items) if items else "  (empty)"


def _tool_copy_to_structure(version_dir: Path, args: dict[str, str]) -> str:
    source_path = args.get("source_path", "")

    if not source_path:
        return "Error: source_path is required"
    
    src_dir = version_dir / "src"
    
    if not source_path.startswith("src/"):
        source_path = "src/" + source_path
    
    source_file = version_dir / source_path
    
    if not source_file.exists():
        return f"Error: source file not found: {source_path}"
    
    if source_path.startswith("src/java/"):
        dest_path = source_path.replace("src/java/", "src/main/java/")
    elif source_path.startswith("src/kotlin/"):
        dest_path = source_path.replace("src/kotlin/", "src/main/kotlin/")
    elif source_path.startswith("src/client/java/"):
        dest_path = source_path.replace("src/client/java/", "src/client/java/")
    elif source_path.startswith("src/client/kotlin/"):
        dest_path = source_path.replace("src/client/kotlin/", "src/client/kotlin/")
    elif source_path.startswith("src/resources/"):
        dest_path = source_path.replace("src/resources/", "src/main/resources/")
    else:
        dest_path = source_path.replace("src/", "src/main/")
    
    dest_file = version_dir / dest_path
    dest_file.parent.mkdir(parents=True, exist_ok=True)
    
    if source_file.is_file():
        import shutil
        shutil.copy2(source_file, dest_file)
        return f"Copied {source_path} to {dest_path}"
    else:
        import shutil
        if dest_file.exists():
            shutil.rmtree(dest_file)
        shutil.copytree(source_file, dest_file)
        return f"Copied directory {source_path} to {dest_path}"


def _is_sources_root(path: Path) -> bool:
    return path.is_dir() and (path / "net" / "minecraft").exists()


def _find_sources_root(path: Path) -> Path | None:
    if _is_sources_root(path):
        return path
    try:
        for match in path.rglob("net/minecraft"):
            if match.is_dir():
                return match.parent
    except Exception:
        return None
    return None


def _select_best_source_candidate(
    candidates: list[Path],
    target_version: str,
    target_loader: str,
) -> Path | None:
    if not candidates:
        return None
    scored: list[tuple[int, Path]] = []
    for path in candidates:
        score = 0
        path_str = str(path)
        if target_version and target_version in path_str:
            score += 10
        if target_loader and target_loader in path_str:
            score += 3
        if target_loader in {"forge", "neoforge"} and "forge-loom" in path_str:
            score += 5
        if target_loader == "fabric" and "fabric-loom" in path_str:
            score += 5
        scored.append((score, path))
    scored.sort(key=lambda item: item[0], reverse=True)
    return scored[0][1]


def _extract_sources_jar(jar_path: Path, dest_root: Path) -> Path | None:
    from modcompiler.common import safe_extract_zip, safe_rmtree

    if not jar_path.exists():
        return None
    safe_rmtree(dest_root)
    dest_root.mkdir(parents=True, exist_ok=True)
    try:
        safe_extract_zip(jar_path, dest_root)
    except Exception as exc:
        print(f"DEBUG[_extract_sources_jar]: Failed to extract {jar_path}: {exc}", file=sys.stderr)
        return None
    return _find_sources_root(dest_root)


def _resolve_template_dir_for_version(
    target_version: str,
    target_loader: str,
    manifest: dict[str, Any],
) -> tuple[str, dict[str, Any]] | None:
    for range_entry in manifest.get("ranges", []):
        if version_inclusive_between(target_version, range_entry.get("min_version", ""), range_entry.get("max_version", "")):
            loaders = range_entry.get("loaders", {})
            if target_loader in loaders:
                return range_entry["folder"], loaders[target_loader]
    return None


def _prepare_gradle_sources(
    version_dir: Path,
    target_version: str,
    target_loader: str,
) -> None:
    import subprocess
    from modcompiler.common import (
        copy_tree,
        safe_rmtree,
        load_json,
        resolve_java_version,
        java_home_for_version,
        sanitize_env_path,
    )

    manifest_path = Path("version-manifest.json")
    if not manifest_path.exists():
        return
    manifest = load_json(manifest_path)
    resolved = _resolve_template_dir_for_version(target_version, target_loader, manifest)
    if not resolved:
        return
    range_folder, loader_config = resolved
    template_dir = loader_config.get("template_dir", "")
    if not template_dir:
        return

    workspace_root = version_dir / ".workflow_state" / "minecraft-sources" / f"{target_version}-{target_loader}" / "workspace"
    safe_rmtree(workspace_root)
    workspace_root.mkdir(parents=True, exist_ok=True)

    source_path = Path(template_dir)
    if (source_path / "template").exists():
        copy_tree(source_path / "template", workspace_root)
    else:
        copy_tree(source_path, workspace_root)

    adapter_family = loader_config.get("adapter_family", "")
    task = "genSources"
    if adapter_family.startswith("forge_legacy"):
        task = "setupDecompWorkspace"

    gradlew = workspace_root / "gradlew"
    if not gradlew.exists():
        return
    try:
        gradlew.chmod(gradlew.stat().st_mode | 0o111)
    except OSError:
        pass

    java_version = resolve_java_version(loader_config, target_version)
    env = os.environ.copy()
    try:
        java_home = java_home_for_version(java_version, env)
        env["JAVA_HOME"] = java_home
        env["PATH"] = sanitize_env_path(java_home, env.get("PATH"))
    except Exception as exc:
        print(f"DEBUG[_prepare_gradle_sources]: Failed to resolve JAVA_HOME: {exc}", file=sys.stderr)
        env = os.environ.copy()

    print(
        f"DEBUG[_prepare_gradle_sources]: Running {task} for {target_version}-{target_loader} "
        f"(java {java_version})",
        file=sys.stderr,
    )
    try:
        result = subprocess.run(
            ["./gradlew", task, "--no-daemon"],
            cwd=workspace_root,
            env=env,
            capture_output=True,
            text=True,
            timeout=900,
        )
        if result.returncode != 0:
            print(
                f"DEBUG[_prepare_gradle_sources]: {task} failed (exit {result.returncode}). "
                f"STDOUT: {result.stdout[-1000:] if result.stdout else ''} "
                f"STDERR: {result.stderr[-1000:] if result.stderr else ''}",
                file=sys.stderr,
            )
    except Exception as exc:
        print(f"DEBUG[_prepare_gradle_sources]: Failed to run {task}: {exc}", file=sys.stderr)


_library_sources_cache: dict[str, Path] = {}


def _get_library_sources(
    version_dir: Path,
    target_version: str,
    target_loader: str,
    *,
    allow_prepare: bool = True,
) -> Path | None:
    cache_key = f"{target_version}-{target_loader}"

    if cache_key in _library_sources_cache:
        cached_path = _library_sources_cache[cache_key]
        if cached_path.exists():
            return cached_path

    local_root = version_dir / ".workflow_state" / "minecraft-sources" / f"{target_version}-{target_loader}"
    local_source = _find_sources_root(local_root) if local_root.exists() else None
    if local_source:
        _library_sources_cache[cache_key] = local_source
        return local_source

    gradle_caches = Path.home() / ".gradle" / "caches"
    source_dirs: list[Path] = []
    try:
        for path in gradle_caches.rglob("sources"):
            if path.is_dir() and _is_sources_root(path):
                source_dirs.append(path)
    except Exception:
        source_dirs = []

    selected = _select_best_source_candidate(source_dirs, target_version, target_loader)
    if selected:
        _library_sources_cache[cache_key] = selected
        return selected

    sources_jars: list[Path] = []
    try:
        for jar_path in gradle_caches.rglob("*sources.jar"):
            if target_version and target_version not in str(jar_path):
                continue
            sources_jars.append(jar_path)
    except Exception:
        sources_jars = []

    jar_selected = _select_best_source_candidate(sources_jars, target_version, target_loader)
    if jar_selected:
        extracted = _extract_sources_jar(jar_selected, local_root / "extracted")
        if extracted:
            _library_sources_cache[cache_key] = extracted
            return extracted

    if allow_prepare:
        _prepare_gradle_sources(version_dir, target_version, target_loader)

    source_dirs = []
    try:
        for path in gradle_caches.rglob("sources"):
            if path.is_dir() and _is_sources_root(path):
                source_dirs.append(path)
    except Exception:
        source_dirs = []

    selected = _select_best_source_candidate(source_dirs, target_version, target_loader)
    if selected:
        _library_sources_cache[cache_key] = selected
        return selected

    sources_jars = []
    try:
        for jar_path in gradle_caches.rglob("*sources.jar"):
            if target_version and target_version not in str(jar_path):
                continue
            sources_jars.append(jar_path)
    except Exception:
        sources_jars = []

    jar_selected = _select_best_source_candidate(sources_jars, target_version, target_loader)
    if jar_selected:
        extracted = _extract_sources_jar(jar_selected, local_root / "extracted")
        if extracted:
            _library_sources_cache[cache_key] = extracted
            return extracted

    return None


def _tool_library_dir(args: dict[str, str], library_sources: Path | None) -> str:
    if not library_sources:
        return "Error: Minecraft library sources not available. Run build first to download/setup Minecraft sources."
    
    path = args.get("path", "")
    depth = int(args.get("depth", 2))
    
    base_path = library_sources
    if path:
        base_path = library_sources / path
        if not base_path.exists():
            return f"Error: Directory not found: {path}"
    
    def list_dir_tree(p: Path, current_depth: int, max_depth: int) -> str:
        if current_depth >= max_depth:
            return ""
        items = []
        try:
            for item in sorted(p.iterdir()):
                items.append(f"{'  ' * current_depth}{item.name}{'/' if item.is_dir() else ''}")
                if item.is_dir():
                    items.append(list_dir_tree(item, current_depth + 1, max_depth))
        except PermissionError:
            return f"{'  ' * current_depth}[permission denied]"
        return "\n".join(items)
    
    result = f"Minecraft sources ({library_sources.name}):\n"
    result += list_dir_tree(base_path, 0, depth)
    return result


def _tool_library_read(args: dict[str, str], library_sources: Path | None) -> str:
    if not library_sources:
        return "Error: Minecraft library sources not available."
    
    path = args.get("path", "")
    if not path:
        return "Error: path is required"
    
    if not path.endswith(".java"):
        path = path.replace(".", "/") + ".java"
    
    file_path = library_sources / path
    if not file_path.exists():
        return f"Error: File not found: {path}\n\nSearch for it using library_search first."
    
    try:
        content = file_path.read_text(encoding="utf-8")
        if len(content) > 15000:
            content = content[:15000] + "\n... (truncated)"
        return f"=== Minecraft: {path} ===\n{content}"
    except Exception as e:
        return f"Error reading file: {e}"


def _tool_library_search(args: dict[str, str], library_sources: Path | None) -> str:
    if not library_sources:
        return "Error: Minecraft library sources not available."
    
    query = args.get("query", "").lower()
    if not query:
        return "Error: query is required"
    
    results = []
    query_parts = query.replace(".", "/").split("/")
    
    search_pattern = f"*{query}*.java"
    if len(query_parts) > 1:
        search_pattern = f"*{query_parts[-1]}*.java"
    
    try:
        for java_file in library_sources.rglob(search_pattern):
            if java_file.is_file():
                rel = java_file.relative_to(library_sources)
                results.append(str(rel))
    except Exception:
        pass
    
    if not results:
        for java_file in library_sources.rglob("*.java"):
            if query in java_file.stem.lower():
                rel = java_file.relative_to(library_sources)
                results.append(str(rel))
                if len(results) >= 20:
                    break
    
    if not results:
        return f"No results found for: {query}"
    
    return f"Found {len(results)} matches for '{query}':\n" + "\n".join(f"  - {r}" for r in results[:30])


def _tool_file_write(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    content = args.get("content", "")

    if not path:
        return "Error: path is required"

    file_path = version_dir / path
    file_path.parent.mkdir(parents=True, exist_ok=True)
    file_path.write_text(content, encoding="utf-8")

    return f"File written: {path} ({len(content)} bytes)"


def _tool_file_edit(version_dir: Path, args: dict[str, str]) -> str:
    path = args.get("path", "")
    old_content = args.get("old_content", "")
    new_content = args.get("new_content", "")

    if not path:
        return "Error: path is required"

    path = _normalize_src_path(path, for_file=True)
    if not path:
        return "Error: path is required"

    file_path = version_dir / "src" / path
    if not file_path.exists():
        return f"Error: file not found: {path}"

    try:
        current = file_path.read_text(encoding="utf-8")
        if old_content not in current:
            return f"Error: old_content not found in file. File has {len(current)} chars, old_content has {len(old_content)} chars."

        updated = current.replace(old_content, new_content)
        file_path.write_text(updated, encoding="utf-8")
        return f"File edited: {path}"
    except Exception as e:
        return f"Error editing file: {e}"


def _tool_move_file(version_dir: Path, args: dict[str, str]) -> str:
    source = args.get("source", "")
    destination = args.get("destination", "")

    if not source or not destination:
        return "Error: source and destination are required"

    if ".." in source or ".." in destination:
        return "Error: cannot use .. in paths"

    src_path = version_dir / source
    dst_path = version_dir / destination

    if not src_path.exists():
        return f"Error: source not found: {source}"

    dst_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(src_path), str(dst_path))

    return f"Moved: {source} -> {destination}"


def _tool_build(version_dir: Path, artifact_dir: Path, context: dict[str, Any], args: dict[str, str]) -> tuple[str, bool]:
    import sys
    from modcompiler.common import load_json
    print(f"DEBUG[_tool_build]: Starting build for {context.get('target_version')}-{context.get('target_loader')}", file=sys.stderr)
    
    target_version = context["target_version"]
    target_loader = context["target_loader"]
    metadata = context["mod_info"].copy()
    
    if "mod_id" not in metadata and "primary_mod_id" in metadata:
        metadata["mod_id"] = metadata["primary_mod_id"]
    
    print(f"DEBUG[_tool_build]: target_version={target_version}, target_loader={target_loader}", file=sys.stderr)
    print(f"DEBUG[_tool_build]: metadata keys: {list(metadata.keys())}", file=sys.stderr)

    local_template = version_dir / "template"
    resolved_range_folder = ""
    build_command_list: list[str] = []
    jar_glob_pattern = "build/libs/*.jar"
    template_dir = ""

    manifest_path = Path("version-manifest.json")
    if not manifest_path.exists():
        return "Error: version-manifest.json not found", False

    manifest = load_json(manifest_path)
    loader_config: dict[str, Any] | None = None

    for range_entry in manifest["ranges"]:
        if version_inclusive_between(target_version, range_entry["min_version"], range_entry["max_version"]):
            if target_loader in range_entry["loaders"]:
                resolved_range_folder = range_entry["folder"]
                loader_config = range_entry["loaders"][target_loader]
                break

    if not loader_config:
        return f"Error: No version folder found for {target_version}", False

    build_command_list = loader_config.get("build_command", ["./modcompiler-build.sh"])
    if not build_command_list:
        build_command_list = ["./modcompiler-build.sh"]
    jar_glob_pattern = loader_config.get("jar_glob", "build/libs/*.jar")

    preferred_template = context.get("template_dir", "")
    if preferred_template and Path(preferred_template).exists():
        template_dir = preferred_template
        if local_template.exists():
            print(
                f"DEBUG[_tool_build]: Local template exists but using manifest template: {template_dir}",
                file=sys.stderr,
            )
    elif local_template.exists():
        template_dir = str(local_template)
        print(f"DEBUG[_tool_build]: Using local template: {template_dir}", file=sys.stderr)
    else:
        template_dir = loader_config.get("template_dir", "")

    if not template_dir:
        return f"Error: Template directory not configured for {target_version}", False

    workspace = version_dir / "_build_workspace"
    safe_rmtree(workspace)
    workspace.mkdir(parents=True)

    try:
        from modcompiler.common import copy_tree

        source_path = Path(template_dir)
        if (source_path / "template").exists():
            copy_tree(source_path / "template", workspace)
        else:
            copy_tree(source_path, workspace)

        build_script_source = Path(__file__).resolve().parents[1] / "scripts" / "modcompiler-build.sh"
        if build_script_source.exists():
            target_script = workspace / "modcompiler-build.sh"
            shutil.copy2(build_script_source, target_script)
            try:
                target_script.chmod(target_script.stat().st_mode | 0o111)
            except OSError:
                pass

        metadata_json = workspace / "metadata.json"
        from modcompiler.common import write_json
        write_json(metadata_json, metadata)

        adapter_script = Path(resolved_range_folder) / "build_adapter.py"
        adapter_command = [
            sys.executable,
            str(adapter_script),
            "--loader",
            target_loader,
            "--metadata-json",
            str(metadata_json),
            "--source-dir",
            str(version_dir / "src"),
            "--template-workspace",
            str(workspace),
            "--minecraft-version",
            target_version,
            "--manifest",
            "version-manifest.json",
        ]

        from modcompiler.common import (
            copy_file,
            find_built_jars,
            jar_contains_classes,
            java_home_for_version,
            resolve_java_version,
            sanitize_env_path,
        )

        env = os.environ.copy()
        java_version = resolve_java_version(loader_config, target_version)

        java_home = java_home_for_version(java_version, env)
        env["JAVA_HOME"] = java_home
        env["PATH"] = sanitize_env_path(java_home, env.get("PATH"))

        build_command = list(build_command_list)

        version_info = f"""=== BUILD VERSION INFO ===
Target Version: {target_version}
Target Loader: {target_loader}
Range Folder: {resolved_range_folder}
Template Dir: {template_dir}
Build Command: {build_command}
Java Version: {java_version}
Java Home: {java_home}
Manifest: version-manifest.json
==================="""

        print(f"DEBUG[_tool_build]: {version_info}", file=sys.stderr)

        log_path = artifact_dir / "build.log"
        version_log_path = artifact_dir / "build_version_info.log"
        version_log_path.write_text(version_info, encoding="utf-8")
        
        print(f"DEBUG[_tool_build]: adapter_command={' '.join(str(x) for x in adapter_command)}", file=sys.stderr)
        
        repo_root = Path.cwd()
        adapter_cwd = repo_root
        print(f"DEBUG[_tool_build]: adapter_cwd (repo root): {adapter_cwd}", file=sys.stderr)
        print(f"DEBUG[_tool_build]: adapter_script exists: {adapter_script.exists()}", file=sys.stderr)
        build_failed_code: int | None = None
        with log_path.open("w", encoding="utf-8") as log_file:
            log_file.write(version_info + "\n\n")
            log_file.write(f"Working directory: {adapter_cwd}\n")
            log_file.write("$ " + " ".join(str(x) for x in adapter_command) + "\n\n")
            log_file.flush()
            adapter_run = subprocess.run(
                adapter_command,
                cwd=adapter_cwd,
                env=env,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
            )
            log_file.flush()
            
            if adapter_run.returncode != 0:
                log_file.write(f"\n\n=== ADAPTER FAILED with exit code {adapter_run.returncode} ===\n")
                log_file.write(f"STDOUT: {adapter_run.stdout}\n")
                log_file.write(f"STDERR: {adapter_run.stderr}\n")
                log_file.write(f"\n{version_info}\n")
                log_file.flush()
                log_content = log_path.read_text(encoding="utf-8")
                print(f"DEBUG[_tool_build]: Adapter failed with exit {adapter_run.returncode}", file=sys.stderr)
                print(f"DEBUG[_tool_build]: Adapter output (first 2000 chars): {log_content[:2000]}", file=sys.stderr)
                return f"Build failed (exit {adapter_run.returncode}). Adapter error:\n\n{adapter_run.stderr[:1500] if adapter_run.stderr else 'No stderr'}\n\nCheck build.log for full details.", False

            log_file.write("\n$ " + " ".join(build_command) + "\n\n")
            log_file.flush()
            build_run = subprocess.run(
                build_command,
                cwd=workspace,
                env=env,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
            )
            log_file.flush()
            
            if build_run.returncode != 0:
                log_file.write(f"\n\n=== GRADLE BUILD FAILED with exit code {build_run.returncode} ===\n")
                log_file.write(f"STDOUT: {build_run.stdout}\n")
                log_file.write(f"STDERR: {build_run.stderr}\n")
                log_file.flush()
                build_failed_code = build_run.returncode

        if build_failed_code is not None:
            log_content = log_path.read_text(encoding="utf-8", errors="replace")
            tail = log_content[-3000:] if len(log_content) > 3000 else log_content
            return (
                f"Build failed (exit {build_failed_code}). Check build.log for details.\n\n"
                f"Last 3000 chars:\n\n{tail}"
            ), False

        jars = find_built_jars(workspace, jar_glob_pattern)
        print(f"DEBUG[_tool_build]: Found jars: {jars}", file=sys.stderr)

        if not jars:
            log_content = log_path.read_text(encoding="utf-8")
            print(f"DEBUG[_tool_build]: No jars found, log content length: {len(log_content)}", file=sys.stderr)
            return f"Build completed but no jar found. Check build.log for errors.\n\nLast 3000 chars:\n\n{log_content[-3000:]}", False
        
        classful_jars = [jar for jar in jars if jar_contains_classes(jar)]
        if not classful_jars:
            log_content = log_path.read_text(encoding="utf-8")
            return (
                "Build completed but jars contain no .class files. "
                "This usually means sources were not placed under src/main/java. "
                "Check build.log for details.\n\nLast 3000 chars:\n\n"
                f"{log_content[-3000:]}"
            ), False

        jars_dir = artifact_dir / "jars"
        jars_dir.mkdir(parents=True, exist_ok=True)

        for jar_path in jars:
            copy_file(jar_path, jars_dir / jar_path.name)

        return f"Build successful! Found: {[j.name for j in jars]}", True

    except Exception as e:
        return f"Build error: {e}", False
    finally:
        safe_rmtree(workspace)
