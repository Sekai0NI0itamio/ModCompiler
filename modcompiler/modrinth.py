from __future__ import annotations

import argparse
import json
import mimetypes
import os
import random
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Any

from modcompiler.common import ModCompilerError, load_json, safe_rmtree, write_json


MODRINTH_API_BASE = "https://api.modrinth.com/v2"
PRIMARY_JAR_EXCLUDE_MARKERS = (
    "-sources",
    "-source",
    "-javadoc",
    "-dev",
    "-deobf",
    "-api",
)


def command_publish_modrinth(args: argparse.Namespace) -> int:
    artifacts_root = Path(args.artifacts_root)
    artifact_dir = Path(args.artifact_dir)
    safe_rmtree(artifact_dir)
    artifact_dir.mkdir(parents=True, exist_ok=True)

    result: dict[str, Any] = {
        "status": "failed",
        "project_input": args.project,
        "project_ref": "",
        "project_id": "",
        "project_slug": "",
        "uploads": [],
        "warnings": [],
    }

    try:
        token = os.environ.get("MODRINTH_TOKEN", "").strip()
        if not token:
            raise ModCompilerError(
                "Modrinth publishing was requested, but the MODRINTH_TOKEN secret is not configured."
            )

        project_ref = normalize_modrinth_project_ref(args.project)
        user_agent = build_modrinth_user_agent()
        client = ModrinthClient(token=token, user_agent=user_agent)
        project = client.resolve_project(project_ref)
        result["project_ref"] = project_ref
        result["project_id"] = project.get("id", "")
        result["project_slug"] = project.get("slug", "")

        summary_path = artifacts_root / "run-summary.json"
        if not summary_path.exists():
            raise ModCompilerError(f"Combined build summary was not found at {summary_path}")
        run_summary = load_json(summary_path)

        uploads: list[dict[str, Any]] = []
        failures = 0
        published = 0
        for mod in run_summary.get("mods", []):
            entry = publish_one_mod(
                client=client,
                artifacts_root=artifacts_root,
                project_id=result["project_id"],
                mod=mod,
            )
            uploads.append(entry)
            if entry["publish_status"] == "uploaded":
                published += 1
            if entry["publish_status"] == "failed":
                failures += 1

        result["uploads"] = uploads
        if failures:
            result["status"] = "partial" if published else "failed"
        else:
            result["status"] = "success" if published else "skipped"
            if published == 0:
                result["warnings"].append("No successful build artifacts were eligible for Modrinth upload.")
    except ModCompilerError as error:
        result["warnings"].append(str(error))
    finally:
        summary_markdown = render_modrinth_summary_markdown(result)
        (artifact_dir / "SUMMARY.md").write_text(summary_markdown, encoding="utf-8")
        write_json(artifact_dir / "result.json", result)

    return 0 if result["status"] in {"success", "skipped"} else 1


def publish_one_mod(
    *,
    client: "ModrinthClient",
    artifacts_root: Path,
    project_id: str,
    mod: dict[str, Any],
) -> dict[str, Any]:
    entry = {
        "slug": mod["slug"],
        "loader": mod["loader"],
        "minecraft_version": mod["minecraft_version"],
        "mod_id": mod["metadata"]["mod_id"],
        "name": mod["metadata"]["name"],
        "mod_version": mod["metadata"]["mod_version"],
        "jar_name": "",
        "publish_status": "skipped",
        "note": "",
        "version_id": "",
    }

    if mod.get("status") != "success":
        entry["publish_status"] = "skipped"
        entry["note"] = "Build did not succeed."
        return entry

    jar_root = artifacts_root / "mods" / mod["slug"] / "jars"
    jar_path = select_primary_jar(jar_root)
    if jar_path is None:
        entry["publish_status"] = "failed"
        entry["note"] = f"No primary jar was found under {jar_root}"
        return entry

    entry["jar_name"] = jar_path.name

    # Find any existing version for this loader+MC combination
    existing = client.find_any_existing_version(
        project_id=project_id,
        loader=mod["loader"],
        minecraft_version=mod["minecraft_version"],
    )

    if existing is not None:
        existing_size = _get_version_primary_file_size(existing)
        new_jar_size = jar_path.stat().st_size
        # A shell is a version with no real classes (< 5000 bytes)
        # or where our new jar is at least 10x larger (clearly more content)
        is_shell = existing_size < 5000 or (existing_size > 0 and new_jar_size > existing_size * 10)

        if not is_shell:
            # Real version already exists — skip
            entry["publish_status"] = "skipped"
            entry["note"] = f"Already exists on Modrinth as version {existing.get('id', '-')} (size={existing_size}B)"
            entry["version_id"] = existing.get("id", "")
            return entry

        # Shell detected — bump the version number and upload a new version
        # Find the highest existing version number for this project+loader+MC
        bumped_version = _bump_version_number(
            client=client,
            project_id=project_id,
            loader=mod["loader"],
            minecraft_version=mod["minecraft_version"],
            base_version=mod["metadata"]["mod_version"],
        )
        entry["note"] = (
            f"Shell version {existing.get('id', '-')} found (size={existing_size}B). "
            f"Uploading as v{bumped_version} instead of deleting."
        )
        # Override the version number in the mod metadata for this upload
        mod = dict(mod)
        mod["metadata"] = dict(mod["metadata"])
        mod["metadata"]["mod_version"] = bumped_version
        mod["shell_replacement"] = True
        # Rename the jar to match the new version number
        new_jar_name = jar_path.name.replace(
            mod["metadata"].get("mod_version", ""), bumped_version
        ) if bumped_version not in jar_path.name else jar_path.name
        entry["jar_name"] = new_jar_name

    payload = build_modrinth_version_payload(project_id=project_id, mod=mod, jar_name=entry["jar_name"])
    try:
        created = client.create_version(payload=payload, jar_path=jar_path)
    except ModCompilerError as error:
        entry["publish_status"] = "failed"
        entry["note"] = (entry.get("note", "") + f" Upload failed: {error}").strip()
        return entry

    entry["publish_status"] = "uploaded"
    if not entry["note"]:
        entry["note"] = "Uploaded to Modrinth."
    else:
        entry["note"] += f" Uploaded as version ID {created.get('id', '-')}."
    entry["version_id"] = created.get("id", "")
    return entry


def normalize_modrinth_project_ref(value: str) -> str:
    raw = value.strip()
    if not raw:
        raise ModCompilerError("A Modrinth project URL or slug is required when publishing is enabled.")
    if "://" not in raw:
        return raw.strip("/")

    parsed = urllib.parse.urlparse(raw)
    if "modrinth.com" not in (parsed.netloc or "") and "api.modrinth.com" not in (parsed.netloc or ""):
        raise ModCompilerError("The Modrinth project input must be a modrinth.com or api.modrinth.com URL, or a slug.")

    parts = [part for part in parsed.path.split("/") if part]
    if not parts:
        raise ModCompilerError(f"Could not extract a project reference from '{value}'.")

    if parts[0] == "project" and len(parts) >= 2:
        return parts[1]
    if parts[0] in {"mod", "plugin", "modpack", "resourcepack", "shader"} and len(parts) >= 2:
        return parts[1]
    if parts[0] == "v2" and len(parts) >= 3 and parts[1] == "project":
        return parts[2]
    raise ModCompilerError(f"Could not extract a project reference from '{value}'.")


def build_modrinth_user_agent() -> str:
    repository = os.environ.get("GITHUB_REPOSITORY", "local/ModCompiler")
    server_url = os.environ.get("GITHUB_SERVER_URL", "https://github.com")
    return f"ModCompiler/1.0 ({server_url}/{repository})"


def select_primary_jar(jar_root: Path) -> Path | None:
    if not jar_root.exists():
        return None
    jars = sorted(path for path in jar_root.glob("*.jar") if path.is_file())
    if not jars:
        return None
    preferred = [
        path
        for path in jars
        if not any(marker in path.stem.lower() for marker in PRIMARY_JAR_EXCLUDE_MARKERS)
    ]
    return preferred[0] if preferred else jars[0]


def _get_version_primary_file_size(version: dict[str, Any]) -> int:
    """Return the size in bytes of the primary file in a Modrinth version object.
    Returns 0 if no file info is available."""
    files = version.get("files", [])
    if not files:
        return 0
    # Prefer the file marked primary
    for f in files:
        if f.get("primary", False):
            return int(f.get("size", 0))
    # Fall back to first file
    return int(files[0].get("size", 0))


def _bump_version_number(
    *,
    client: "ModrinthClient",
    project_id: str,
    loader: str,
    minecraft_version: str,
    base_version: str,
) -> str:
    """Find the highest existing version number for this project+loader+MC and return
    the next incremented patch version.

    Examples:
      existing: 1.0.0  → returns 1.0.1
      existing: 1.0.1  → returns 1.0.2
      existing: 1.0.0, 1.0.1  → returns 1.0.2
      no existing  → returns base_version (e.g. 1.0.1)
    """
    params = {
        "loaders": json.dumps([loader]),
        "game_versions": json.dumps([minecraft_version]),
        "include_changelog": "false",
    }
    try:
        versions = client.request_json(
            "GET",
            f"/project/{urllib.parse.quote(project_id, safe='')}/version",
            params=params,
        )
    except ModCompilerError:
        return base_version

    if not isinstance(versions, list) or not versions:
        return base_version

    # Parse all version numbers and find the highest patch
    highest = _parse_semver(base_version)
    for v in versions:
        parsed = _parse_semver(str(v.get("version_number", "")))
        if parsed and (highest is None or parsed > highest):
            highest = parsed

    if highest is None:
        return base_version

    # Increment the patch component
    major, minor, patch = highest
    return f"{major}.{minor}.{patch + 1}"


def _parse_semver(version: str) -> tuple[int, int, int] | None:
    """Parse a semver string like '1.0.1' into (major, minor, patch).
    Returns None if the string doesn't match."""
    import re
    m = re.match(r"^(\d+)\.(\d+)\.(\d+)", version.strip())
    if not m:
        return None
    return int(m.group(1)), int(m.group(2)), int(m.group(3))


def build_modrinth_version_payload(*, project_id: str, mod: dict[str, Any], jar_name: str) -> dict[str, Any]:
    metadata = mod["metadata"]
    version_number = metadata["mod_version"]
    minecraft_version = mod["minecraft_version"]
    loader = mod["loader"]
    fix_corrupted = bool(metadata.get("fix_corrupted") or mod.get("fix_corrupted"))
    shell_replacement = bool(mod.get("shell_replacement"))
    title = f"{metadata['name']} {version_number} ({loader} {minecraft_version})"
    if fix_corrupted:
        title = f"{title} (Fixed Corrupted Version)"
    elif shell_replacement:
        title = f"{title} (Fixed Empty Version)"

    changelog_lines = []
    if fix_corrupted:
        changelog_lines.append("Fixed Corrupted Version")
        if metadata.get("fix_source_version"):
            changelog_lines.append(f"Original version: {metadata['fix_source_version']}")
        changelog_lines.append("")
    elif shell_replacement:
        changelog_lines.append("Fixed Empty/Shell Version")
        changelog_lines.append("The previous version for this Minecraft version was an empty jar with no classes.")
        changelog_lines.append("This version replaces it with a fully working build.")
        changelog_lines.append("")
    changelog_lines.extend(
        [
            "Automated upload from ModCompiler GitHub Actions.",
            "",
            f"Mod: {metadata['name']}",
            f"Mod version: {version_number}",
            f"Loader: {loader}",
            f"Minecraft version: {minecraft_version}",
            f"Built artifact: {jar_name}",
        ]
    )
    changelog = "\n".join(changelog_lines)
    return {
        "name": title,
        "version_number": version_number,
        "changelog": changelog,
        "dependencies": [],
        "game_versions": [minecraft_version],
        "version_type": "release",
        "loaders": [loader],
        "featured": False,
        "project_id": project_id,
        "file_parts": ["file"],
        "primary_file": "file",
    }


def render_modrinth_summary_markdown(result: dict[str, Any]) -> str:
    lines = [
        "# Modrinth Publish Summary",
        "",
        f"- Status: `{result['status']}`",
        f"- Project input: `{result.get('project_input', '') or '-'}`",
        f"- Project ref: `{result.get('project_ref', '') or '-'}`",
        f"- Project id: `{result.get('project_id', '') or '-'}`",
        "",
        "| Slug | Loader | Minecraft | Jar | Publish Status | Note | Version ID |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    uploads = result.get("uploads", [])
    if uploads:
        for upload in uploads:
            lines.append(
                "| "
                + " | ".join(
                    [
                        upload.get("slug", "-"),
                        upload.get("loader", "-"),
                        upload.get("minecraft_version", "-"),
                        upload.get("jar_name", "-") or "-",
                        upload.get("publish_status", "-"),
                        upload.get("note", "-") or "-",
                        upload.get("version_id", "-") or "-",
                    ]
                )
                + " |"
            )
    else:
        lines.append("| - | - | - | - | skipped | No upload attempts were made. | - |")
    warnings = result.get("warnings", [])
    if warnings:
        lines.append("")
        lines.append("## Warnings")
        for warning in warnings:
            lines.append(f"- {warning}")
    lines.append("")
    return "\n".join(lines)


def required_modrinth_scope_for_request(method: str, path: str) -> str:
    method_upper = method.upper()
    if method_upper == "POST" and path == "/project":
        return "PROJECT_CREATE"
    if method_upper == "POST" and path == "/version":
        return "VERSION_CREATE"
    if method_upper == "PATCH" and path.startswith("/project/"):
        return "PROJECT_WRITE"
    if method_upper == "PATCH" and path.startswith("/version/"):
        return "VERSION_WRITE"
    return ""


def build_modrinth_auth_failure_message(*, method: str, path: str, payload_text: str) -> str:
    detail = "Modrinth authentication failed."
    lowered = payload_text.lower()
    if "invalid authentication credentials" in lowered or "invalid authentication" in lowered:
        detail += " The token looks invalid or expired."
    elif "scope" in lowered or "no authorization to access" in lowered:
        detail += " The token looks present, but it may be missing required Modrinth scopes."
    else:
        detail += " Modrinth rejected the supplied token."

    required_scope = required_modrinth_scope_for_request(method, path)
    if required_scope:
        detail += f" Required scope for this request: {required_scope}."

    detail += " Check the MODRINTH_TOKEN secret."
    if payload_text:
        detail += f" Response: {payload_text[:300]}"
    return detail


class ModrinthClient:
    def __init__(self, *, token: str | None, user_agent: str, api_base: str = MODRINTH_API_BASE) -> None:
        self.token = token or ""
        self.user_agent = user_agent
        self.api_base = api_base.rstrip("/")
        self.request_timeout = 60

    def resolve_project(self, project_ref: str) -> dict[str, Any]:
        return self.request_json("GET", f"/project/{urllib.parse.quote(project_ref, safe='')}")

    def find_existing_version(
        self,
        *,
        project_id: str,
        loader: str,
        minecraft_version: str,
        version_number: str,
        jar_name: str,
    ) -> dict[str, Any] | None:
        params = {
            "loaders": json.dumps([loader]),
            "game_versions": json.dumps([minecraft_version]),
            "include_changelog": "false",
        }
        versions = self.request_json(
            "GET",
            f"/project/{urllib.parse.quote(project_id, safe='')}/version",
            params=params,
        )
        if not isinstance(versions, list):
            return None
        for version in versions:
            if str(version.get("version_number", "")) != version_number:
                continue
            for file_info in version.get("files", []):
                if file_info.get("filename") == jar_name:
                    return version
        return None

    def create_version(self, *, payload: dict[str, Any], jar_path: Path) -> dict[str, Any]:
        body, content_type = encode_multipart_form_data(
            fields={"data": json.dumps(payload)},
            files=[("file", jar_path.name, jar_path.read_bytes(), guess_content_type(jar_path.name))],
        )
        return self.request_json(
            "POST",
            "/version",
            body=body,
            extra_headers={"Content-Type": content_type},
        )

    def delete_version(self, *, version_id: str) -> None:
        """Delete a version from Modrinth (used to replace shell versions)."""
        self.request_json(
            "DELETE",
            f"/version/{urllib.parse.quote(version_id, safe='')}",
        )

    def find_any_existing_version(
        self,
        *,
        project_id: str,
        loader: str,
        minecraft_version: str,
    ) -> dict[str, Any] | None:
        """Find any existing version for the given loader+MC, regardless of version number.
        Used to detect shells that may have a different version number or jar name."""
        params = {
            "loaders": json.dumps([loader]),
            "game_versions": json.dumps([minecraft_version]),
            "include_changelog": "false",
        }
        versions = self.request_json(
            "GET",
            f"/project/{urllib.parse.quote(project_id, safe='')}/version",
            params=params,
        )
        if not isinstance(versions, list) or not versions:
            return None
        return versions[0]

    def get_project(self, *, project_ref: str) -> dict[str, Any]:
        response = self.request_json(
            "GET",
            f"/project/{urllib.parse.quote(project_ref, safe='')}",
        )
        if not isinstance(response, dict):
            raise ModCompilerError("Modrinth returned an invalid project response.")
        return response

    def modify_project(self, *, project_ref: str, payload: dict[str, Any]) -> None:
        self.request_json(
            "PATCH",
            f"/project/{urllib.parse.quote(project_ref, safe='')}",
            body=json.dumps(payload).encode("utf-8"),
            extra_headers={"Content-Type": "application/json"},
        )

    def change_project_icon(self, *, project_ref: str, icon_path: Path) -> None:
        ext = guess_content_type(icon_path.name)
        self.request_json(
            "PATCH",
            f"/project/{urllib.parse.quote(project_ref, safe='')}/icon",
            params={"ext": icon_path.suffix.lower().lstrip(".")},
            body=icon_path.read_bytes(),
            extra_headers={"Content-Type": ext},
        )

    def add_gallery_image(
        self,
        *,
        project_ref: str,
        image_path: Path,
        featured: bool,
        title: str,
        description: str,
        ordering: int,
    ) -> None:
        params = {
            "ext": image_path.suffix.lower().lstrip("."),
            "featured": "true" if featured else "false",
            "title": title,
            "description": description,
            "ordering": str(ordering),
        }
        self.request_json(
            "POST",
            f"/project/{urllib.parse.quote(project_ref, safe='')}/gallery",
            params=params,
            body=image_path.read_bytes(),
            extra_headers={"Content-Type": guess_content_type(image_path.name)},
        )

    def modify_version(self, *, version_id: str, payload: dict[str, Any]) -> None:
        self.request_json(
            "PATCH",
            f"/version/{urllib.parse.quote(version_id, safe='')}",
            body=json.dumps(payload).encode("utf-8"),
            extra_headers={"Content-Type": "application/json"},
        )

    def request_json(
        self,
        method: str,
        path: str,
        *,
        params: dict[str, str] | None = None,
        body: bytes | None = None,
        extra_headers: dict[str, str] | None = None,
    ) -> Any:
        url = self.api_base + path
        if params:
            query = urllib.parse.urlencode(params)
            url = f"{url}?{query}"
        headers = {"User-Agent": self.user_agent, "Accept": "application/json"}
        if self.token:
            headers["Authorization"] = self.token
        if extra_headers:
            headers.update(extra_headers)
        request = urllib.request.Request(url, data=body, method=method, headers=headers)
        method_upper = method.upper()
        max_attempts = 5 if method_upper == "GET" else 1

        for attempt in range(1, max_attempts + 1):
            try:
                with urllib.request.urlopen(request, timeout=self.request_timeout) as response:
                    payload = response.read()
                break
            except urllib.error.HTTPError as error:
                payload_text = error.read().decode("utf-8", errors="replace").strip()
                if error.code in {401, 403}:
                    raise ModCompilerError(
                        build_modrinth_auth_failure_message(
                            method=method_upper,
                            path=path,
                            payload_text=payload_text,
                        )
                    ) from None
                if error.code == 404 and path.startswith("/project/"):
                    raise ModCompilerError("The Modrinth project could not be found.") from None
                if error.code in {429, 500, 502, 503, 504} and attempt < max_attempts:
                    retry_after = error.headers.get("Retry-After") if error.headers else None
                    delay = _compute_retry_delay(attempt, retry_after)
                    time.sleep(delay)
                    continue
                detail = f"Modrinth API request failed with HTTP {error.code}."
                if payload_text:
                    detail = f"{detail} Response: {payload_text[:400]}"
                raise ModCompilerError(detail) from None
            except urllib.error.URLError as error:
                if attempt < max_attempts:
                    delay = _compute_retry_delay(attempt, None)
                    time.sleep(delay)
                    continue
                raise ModCompilerError(f"Could not reach Modrinth: {error.reason}") from None
        else:
            raise ModCompilerError("Modrinth API request failed after retries.") from None

        text = payload.decode("utf-8", errors="replace")
        if not text:
            return {}
        try:
            return json.loads(text)
        except json.JSONDecodeError as error:
            raise ModCompilerError(f"Modrinth returned invalid JSON: {error}") from None


def _compute_retry_delay(attempt: int, retry_after: str | None) -> float:
    if retry_after:
        try:
            delay = float(retry_after)
            if delay >= 0:
                return min(delay, 60.0)
        except ValueError:
            pass
    base = min(2 ** (attempt - 1), 30)
    jitter = random.uniform(0, 0.5 * base)
    return base + jitter


def encode_multipart_form_data(
    *,
    fields: dict[str, str],
    files: list[tuple[str, str, bytes, str]],
) -> tuple[bytes, str]:
    boundary = f"modcompiler-{uuid.uuid4().hex}"
    body = bytearray()

    for name, value in fields.items():
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"))
        body.extend(value.encode("utf-8"))
        body.extend(b"\r\n")

    for field_name, filename, data, content_type in files:
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(
            f'Content-Disposition: form-data; name="{field_name}"; filename="{filename}"\r\n'.encode("utf-8")
        )
        body.extend(f"Content-Type: {content_type}\r\n\r\n".encode("utf-8"))
        body.extend(data)
        body.extend(b"\r\n")

    body.extend(f"--{boundary}--\r\n".encode("utf-8"))
    return bytes(body), f"multipart/form-data; boundary={boundary}"


def guess_content_type(filename: str) -> str:
    guessed, _encoding = mimetypes.guess_type(filename)
    return guessed or "application/octet-stream"


def select_primary_modrinth_file(files: list[dict[str, Any]]) -> dict[str, Any] | None:
    if not files:
        return None

    primary = [file_info for file_info in files if file_info.get("primary")]
    candidates = primary or files

    required = [
        file_info
        for file_info in candidates
        if str(file_info.get("file_type", "")).lower() == "required"
    ]
    if required:
        candidates = required

    jar_candidates = [
        file_info
        for file_info in candidates
        if str(file_info.get("filename", "")).lower().endswith(".jar")
    ]
    if jar_candidates:
        candidates = jar_candidates

    preferred = [
        file_info
        for file_info in candidates
        if not any(marker in str(file_info.get("filename", "")).lower() for marker in PRIMARY_JAR_EXCLUDE_MARKERS)
    ]
    return preferred[0] if preferred else candidates[0]
