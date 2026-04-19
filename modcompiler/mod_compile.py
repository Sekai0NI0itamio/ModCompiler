from __future__ import annotations

import argparse
import base64
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import uuid
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from modcompiler.common import ModCompilerError, copy_file, safe_rmtree, write_json


DEFAULT_OUTPUT_DIR = "ModCompileRuns"
DEFAULT_REMOTE_INPUT_PREFIX = "incoming/mod-compile"
DEFAULT_TIMEOUT_SECONDS = 7200
GITHUB_CLI_MAX_RETRIES = 4
GITHUB_CLI_RETRY_DELAY_SECONDS = 3.0
REMOTE_BUILD_WORKFLOW_ID = "build.yml"
REMOTE_BUILD_ARTIFACT_NAME = "all-mod-builds"
REMOTE_PUBLISH_ARTIFACT_NAME = "modrinth-publish"
CLAIMED_REMOTE_RUN_IDS: set[int] = set()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Upload a local mod bundle zip to a temporary GitHub branch, dispatch the "
            "Build Mods workflow, wait for it to finish, and download the resulting "
            "artifacts back to this computer."
        )
    )
    parser.add_argument("zip_path", help="Path to the local zip bundle to compile remotely.")
    parser.add_argument(
        "--output-dir",
        default=DEFAULT_OUTPUT_DIR,
        help="Directory where downloaded run artifacts and summaries are written.",
    )
    parser.add_argument(
        "--max-parallel",
        default="all",
        help="Build workflow max_parallel input. Use all or a positive integer.",
    )
    parser.add_argument(
        "--modrinth-project-url",
        default="",
        help=(
            "Optional Modrinth project URL or slug. If set, successful jars are "
            "auto-published by the existing workflow."
        ),
    )
    parser.add_argument(
        "--github-token",
        default="",
        help="Optional GitHub token override. Defaults to GH_TOKEN/GITHUB_TOKEN discovery.",
    )
    parser.add_argument(
        "--github-repo",
        default="",
        help="Optional owner/repo override. Defaults to parsing origin remote.",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=DEFAULT_TIMEOUT_SECONDS,
        help="Maximum time to wait for the GitHub Actions run to finish.",
    )
    args = parser.parse_args(argv)
    try:
        return command_run(args)
    except ModCompilerError as error:
        print(str(error), file=sys.stderr)
        return 1


def command_run(args: argparse.Namespace) -> int:
    ensure_command_available("git")
    ensure_command_available("gh")

    zip_path = resolve_existing_zip_path(args.zip_path)
    output_root = resolve_output_dir(args.output_dir)
    output_root.mkdir(parents=True, exist_ok=True)

    github_token = (args.github_token or discover_github_token()).strip()
    github_repo = (args.github_repo or discover_github_repo()).strip()
    max_parallel = normalize_max_parallel(args.max_parallel)
    session_id = build_session_id()
    run_dir = output_root / f"mod-compile-{session_id}"
    run_dir.mkdir(parents=True, exist_ok=False)

    request: dict[str, Any] = {
        "created_at": now_iso(),
        "local_zip_path": str(zip_path),
        "github_repo": github_repo,
        "max_parallel": max_parallel,
        "modrinth_project_url": (args.modrinth_project_url or "").strip(),
        "timeout_seconds": int(args.timeout_seconds),
        "notes": [
            "The temporary GitHub branch is created from the current committed HEAD only.",
            "Uncommitted local repository changes are not uploaded to GitHub by this script.",
        ],
    }
    write_json(run_dir / "request.json", request)

    result: dict[str, Any] = {
        **request,
        "status": "failed",
        "error": "",
        "warnings": [],
        "dispatch_branch": "",
        "remote_zip_path": "",
        "github_run_id": 0,
        "github_run_url": "",
        "workflow_name": "",
        "workflow_status": "",
        "workflow_conclusion": "",
        "build_artifact_dir": "",
        "build_artifact_zip": "",
        "modrinth_artifact_dir": "",
        "modrinth_artifact_zip": "",
    }

    try:
        dispatch_branch, remote_zip_path = prepare_remote_build_input(
            zip_path=zip_path,
            github_token=github_token,
            remote_input_prefix=DEFAULT_REMOTE_INPUT_PREFIX,
            session_id=session_id,
        )
        result["dispatch_branch"] = dispatch_branch
        result["remote_zip_path"] = remote_zip_path

        run_id = dispatch_remote_build_run(
            github_repo=github_repo,
            github_branch=dispatch_branch,
            github_token=github_token,
            remote_zip_path=remote_zip_path,
            max_parallel=max_parallel,
            modrinth_project_url=(args.modrinth_project_url or "").strip(),
        )
        result["github_run_id"] = run_id

        run_info = wait_for_remote_run_completion(
            github_repo=github_repo,
            github_token=github_token,
            run_id=run_id,
            timeout_seconds=int(args.timeout_seconds),
        )
        result["github_run_url"] = str(run_info.get("url", "") or "")
        result["workflow_name"] = str(run_info.get("workflowName", "") or "")
        result["workflow_status"] = str(run_info.get("status", "") or "")
        result["workflow_conclusion"] = str(run_info.get("conclusion", "") or "")

        build_artifact = download_named_artifact(
            github_repo=github_repo,
            github_token=github_token,
            run_id=run_id,
            artifact_name=REMOTE_BUILD_ARTIFACT_NAME,
            run_dir=run_dir,
        )
        result["build_artifact_dir"] = str(build_artifact["artifact_path"])
        result["build_artifact_zip"] = str(build_artifact["archive_path"])

        if result["modrinth_project_url"]:
            try:
                modrinth_artifact = download_named_artifact(
                    github_repo=github_repo,
                    github_token=github_token,
                    run_id=run_id,
                    artifact_name=REMOTE_PUBLISH_ARTIFACT_NAME,
                    run_dir=run_dir,
                )
                result["modrinth_artifact_dir"] = str(modrinth_artifact["artifact_path"])
                result["modrinth_artifact_zip"] = str(modrinth_artifact["archive_path"])
            except ModCompilerError as error:
                result["warnings"].append(str(error))

        conclusion = result["workflow_conclusion"].lower()
        result["status"] = "success" if conclusion == "success" else "failed"
        write_run_outputs(run_dir=run_dir, result=result)

        print(f"Run folder: {run_dir}")
        print(f"GitHub run: {result['github_run_url'] or result['github_run_id']}")
        print(f"Build artifact: {result['build_artifact_dir']}")
        if result["build_artifact_zip"]:
            print(f"Build artifact zip: {result['build_artifact_zip']}")
        if result["modrinth_artifact_dir"]:
            print(f"Modrinth artifact: {result['modrinth_artifact_dir']}")
        if result["modrinth_artifact_zip"]:
            print(f"Modrinth artifact zip: {result['modrinth_artifact_zip']}")

        return 0 if result["status"] == "success" else 1
    except Exception as error:
        result["status"] = "failed"
        result["error"] = f"{type(error).__name__}: {error}"
        write_run_outputs(run_dir=run_dir, result=result)
        print(f"Run folder: {run_dir}")
        if result["github_run_url"]:
            print(f"GitHub run: {result['github_run_url']}")
        if isinstance(error, ModCompilerError):
            raise
        raise ModCompilerError(f"Unhandled Mod Compile error: {type(error).__name__}: {error}") from error


def resolve_existing_zip_path(raw_path: str) -> Path:
    path = Path(raw_path)
    if not path.is_absolute():
        path = Path.cwd() / path
    path = path.resolve()
    if not path.exists():
        raise ModCompilerError(f"Zip path does not exist: {path}")
    if not path.is_file():
        raise ModCompilerError(f"Zip path is not a file: {path}")
    if path.suffix.lower() != ".zip":
        raise ModCompilerError(f"Zip path must end in .zip: {path}")
    return path


def resolve_output_dir(raw_path: str) -> Path:
    path = Path(raw_path)
    if not path.is_absolute():
        path = Path.cwd() / path
    return path.resolve()


def normalize_max_parallel(raw_value: str) -> str:
    value = str(raw_value or "").strip().lower()
    if not value or value in {"all", "unlimited", "max"}:
        return "all"
    if not value.isdigit():
        raise ModCompilerError("max_parallel must be 'all' or a positive integer")
    normalized = int(value)
    if normalized < 1:
        raise ModCompilerError("max_parallel must be >= 1")
    return str(normalized)


def build_session_id() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8]


def now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def sanitize_remote_zip_filename(name: str) -> str:
    source_name = Path(name or "").name
    stem = re.sub(r"[^A-Za-z0-9._-]+", "-", Path(source_name).stem).strip("._-")
    if not stem:
        stem = "bundle"
    return f"{stem}.zip"


def prepare_remote_build_input(
    *,
    zip_path: Path,
    github_token: str,
    remote_input_prefix: str,
    session_id: str,
) -> tuple[str, str]:
    remote_zip_name = sanitize_remote_zip_filename(zip_path.name)
    dispatch_branch = f"mod-compile-{session_id}"
    remote_zip_path = (Path(remote_input_prefix) / session_id / remote_zip_name).as_posix()

    with tempfile.TemporaryDirectory(prefix="mod-compile-remote-branch-") as temp_dir:
        worktree_dir = Path(temp_dir) / "worktree"
        run_subprocess(["git", "worktree", "add", "--detach", str(worktree_dir), "HEAD"])
        try:
            run_subprocess(["git", "checkout", "-b", dispatch_branch], cwd=worktree_dir)
            destination = worktree_dir / remote_zip_path
            destination.parent.mkdir(parents=True, exist_ok=True)
            copy_file(zip_path, destination)

            run_subprocess(["git", "add", "--", remote_zip_path], cwd=worktree_dir)
            staged_output = run_subprocess(
                ["git", "diff", "--cached", "--name-only", "--", remote_zip_path],
                cwd=worktree_dir,
            )
            if not staged_output.strip():
                raise ModCompilerError("Failed to stage the zip bundle for remote compilation.")

            run_subprocess(
                [
                    "git",
                    "-c",
                    "user.name=Codex Mod Compile",
                    "-c",
                    "user.email=codex-mod-compile@example.com",
                    "commit",
                    "-m",
                    f"Add Mod Compile input ({session_id})",
                    "--",
                    remote_zip_path,
                ],
                cwd=worktree_dir,
            )
            push_branch_with_token(branch=dispatch_branch, github_token=github_token, cwd=worktree_dir)
        finally:
            try:
                run_subprocess(["git", "worktree", "remove", "--force", str(worktree_dir)])
            except ModCompilerError:
                pass

    return dispatch_branch, remote_zip_path


def dispatch_remote_build_run(
    *,
    github_repo: str,
    github_branch: str,
    github_token: str,
    remote_zip_path: str,
    max_parallel: str,
    modrinth_project_url: str,
) -> int:
    return dispatch_workflow_run(
        github_repo=github_repo,
        github_branch=github_branch,
        github_token=github_token,
        workflow_id=REMOTE_BUILD_WORKFLOW_ID,
        fields={
            "zip_path": remote_zip_path,
            "max_parallel": max_parallel,
            "modrinth_project_url": modrinth_project_url,
        },
        not_found_message=(
            f"GitHub accepted the Build Mods dispatch for {remote_zip_path}, "
            "but no new workflow run could be located."
        ),
    )


def discover_github_token() -> str:
    for env_name in ("GH_TOKEN", "GITHUB_TOKEN"):
        token = os.environ.get(env_name, "").strip()
        if token:
            return token

    parent = Path.cwd().parent
    if parent.exists():
        matches = sorted(
            path
            for path in parent.iterdir()
            if path.is_dir() and path.name.startswith("github_pat_")
        )
        if matches:
            return matches[0].name

    raise ModCompilerError(
        "Could not find a GitHub token. Set GH_TOKEN/GITHUB_TOKEN or create a sibling "
        "directory whose name starts with github_pat_."
    )


def discover_github_repo() -> str:
    remote_url = run_subprocess(["git", "remote", "get-url", "origin"]).strip()
    repo = parse_github_repo_from_remote(remote_url)
    if not repo:
        raise ModCompilerError(f"Could not determine owner/repo from origin remote: {remote_url}")
    return repo


def parse_github_repo_from_remote(remote_url: str) -> str:
    raw = str(remote_url or "").strip()
    if raw.startswith("git@github.com:"):
        tail = raw.split(":", 1)[1]
    elif raw.startswith("https://github.com/"):
        tail = raw.split("https://github.com/", 1)[1]
    elif raw.startswith("http://github.com/"):
        tail = raw.split("http://github.com/", 1)[1]
    else:
        return ""
    tail = tail.strip("/")
    if tail.endswith(".git"):
        tail = tail[:-4]
    parts = [part for part in tail.split("/") if part]
    if len(parts) < 2:
        return ""
    return f"{parts[0]}/{parts[1]}"


def dispatch_workflow_run(
    *,
    github_repo: str,
    github_branch: str,
    github_token: str,
    workflow_id: str,
    fields: dict[str, str],
    not_found_message: str,
) -> int:
    before_ids = {
        int(item["databaseId"])
        for item in list_workflow_runs(
            github_repo=github_repo,
            github_branch=github_branch,
            github_token=github_token,
            workflow_id=workflow_id,
        )
    }

    command = [
        "gh",
        "workflow",
        "run",
        workflow_id,
        "-R",
        github_repo,
        "--ref",
        github_branch,
    ]
    for key, value in fields.items():
        command.extend(["-f", f"{key}={value}"])
    run_github_cli(command, github_token=github_token)

    deadline = time.time() + 180
    while time.time() < deadline:
        for item in list_workflow_runs(
            github_repo=github_repo,
            github_branch=github_branch,
            github_token=github_token,
            workflow_id=workflow_id,
        ):
            run_id = int(item["databaseId"])
            if run_id in before_ids or run_id in CLAIMED_REMOTE_RUN_IDS:
                continue
            CLAIMED_REMOTE_RUN_IDS.add(run_id)
            return run_id
        time.sleep(5)

    raise ModCompilerError(not_found_message)


def list_workflow_runs(
    *,
    github_repo: str,
    github_branch: str,
    github_token: str,
    workflow_id: str,
) -> list[dict[str, Any]]:
    try:
        output = run_github_cli(
            [
                "gh",
                "run",
                "list",
                "-R",
                github_repo,
                "-w",
                workflow_id,
                "-b",
                github_branch,
                "-e",
                "workflow_dispatch",
                "--json",
                "databaseId,status,conclusion,createdAt,url,headBranch,workflowName,displayTitle",
                "-L",
                "20",
            ],
            github_token=github_token,
        )
    except ModCompilerError as error:
        text = str(error)
        if "not found on the default branch" in text:
            raise ModCompilerError(
                f"GitHub cannot dispatch `{workflow_id}` yet because that workflow file is not on "
                "the repository default branch.\n"
                f"Commit and push `.github/workflows/{workflow_id}` to the default branch first, "
                "then run Mod Compile again."
            ) from None
        raise
    parsed = json.loads(output or "[]")
    return parsed if isinstance(parsed, list) else []


def wait_for_remote_run_completion(
    *,
    github_repo: str,
    github_token: str,
    run_id: int,
    timeout_seconds: int,
) -> dict[str, Any]:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        output = run_github_cli(
            [
                "gh",
                "run",
                "view",
                str(run_id),
                "-R",
                github_repo,
                "--json",
                "status,conclusion,url,workflowName",
            ],
            github_token=github_token,
        )
        parsed = json.loads(output or "{}")
        if isinstance(parsed, dict) and parsed.get("status") == "completed":
            return parsed
        time.sleep(10)
    raise ModCompilerError(f"Timed out waiting for GitHub run {run_id} to finish.")


def download_named_artifact(
    *,
    github_repo: str,
    github_token: str,
    run_id: int,
    artifact_name: str,
    run_dir: Path,
) -> dict[str, Path]:
    download_root = run_dir / "downloads" / artifact_name
    download_run_artifact(
        github_repo=github_repo,
        github_token=github_token,
        run_id=run_id,
        artifact_name=artifact_name,
        download_root=download_root,
    )
    artifact_path = select_downloaded_artifact_path(download_root, artifact_name)
    archive_path = run_dir / f"{artifact_name}.zip"
    create_zip_archive(artifact_path, archive_path)
    return {
        "download_root": download_root,
        "artifact_path": artifact_path,
        "archive_path": archive_path,
    }


def download_run_artifact(
    *,
    github_repo: str,
    github_token: str,
    run_id: int,
    artifact_name: str,
    download_root: Path,
) -> Path:
    safe_rmtree(download_root)
    download_root.mkdir(parents=True, exist_ok=True)

    last_error = ""
    for _attempt in range(5):
        try:
            run_github_cli(
                [
                    "gh",
                    "run",
                    "download",
                    str(run_id),
                    "-R",
                    github_repo,
                    "-n",
                    artifact_name,
                    "-D",
                    str(download_root),
                ],
                github_token=github_token,
            )
            return download_root
        except ModCompilerError as error:
            last_error = str(error)
            time.sleep(3)

    raise ModCompilerError(
        f"Failed to download the {artifact_name} artifact for run {run_id}. {last_error}"
    )


def select_downloaded_artifact_path(download_root: Path, artifact_name: str) -> Path:
    named_dir = download_root / artifact_name
    if named_dir.exists():
        return named_dir

    children = sorted(download_root.iterdir())
    if len(children) == 1:
        return children[0]
    return download_root


def create_zip_archive(source_path: Path, output_zip: Path) -> None:
    output_zip.parent.mkdir(parents=True, exist_ok=True)
    if output_zip.exists():
        output_zip.unlink()

    with zipfile.ZipFile(output_zip, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        if source_path.is_file():
            archive.write(source_path, arcname=source_path.name)
            return
        for path in sorted(source_path.rglob("*")):
            if path.is_file():
                archive.write(path, arcname=str(path.relative_to(source_path.parent)))


def ensure_command_available(command_name: str) -> None:
    try:
        run_subprocess([command_name, "--version"])
    except ModCompilerError as error:
        raise ModCompilerError(f"Required command '{command_name}' is not available: {error}") from None


def github_cli_env(github_token: str) -> dict[str, str]:
    env = os.environ.copy()
    env["GH_TOKEN"] = github_token
    env["GITHUB_TOKEN"] = github_token
    return env


def is_transient_github_cli_error(text: str) -> bool:
    lowered = str(text or "").lower()
    transient_markers = (
        "connection reset by peer",
        "tls handshake timeout",
        "i/o timeout",
        "timeout awaiting headers",
        "connection timed out",
        "unexpected eof",
        "http2: client connection lost",
        "use of closed network connection",
        "server closed idle connection",
        "temporary failure in name resolution",
        "no such host",
        "connection refused",
        "net/http: timeout awaiting response headers",
    )
    return any(marker in lowered for marker in transient_markers)


def run_github_cli(
    args: list[str],
    *,
    github_token: str,
    cwd: Path | None = None,
    retries: int = GITHUB_CLI_MAX_RETRIES,
    retry_delay_seconds: float = GITHUB_CLI_RETRY_DELAY_SECONDS,
) -> str:
    last_error: ModCompilerError | None = None
    attempts = max(1, int(retries))
    for attempt in range(1, attempts + 1):
        try:
            return run_subprocess(args, cwd=cwd, env=github_cli_env(github_token))
        except ModCompilerError as error:
            last_error = error
            if attempt >= attempts or not is_transient_github_cli_error(str(error)):
                raise
            time.sleep(retry_delay_seconds * attempt)
    if last_error is not None:
        raise last_error
    raise ModCompilerError("GitHub CLI command failed for an unknown reason.")


def push_branch_with_token(*, branch: str, github_token: str, cwd: Path | None = None) -> None:
    basic_value = base64.b64encode(f"x-access-token:{github_token}".encode("utf-8")).decode("ascii")
    try:
        run_subprocess(
            [
                "git",
                "-c",
                f"http.https://github.com/.extraheader=AUTHORIZATION: basic {basic_value}",
                "push",
                "origin",
                f"HEAD:refs/heads/{branch}",
            ],
            cwd=cwd,
        )
    except ModCompilerError as error:
        text = str(error)
        if "Permission to" in text and "403" in text:
            raise ModCompilerError(
                "GitHub rejected the push with HTTP 403. Your token does not have enough "
                "repository write access.\n"
                "If this is a fine-grained PAT, grant this repo:\n"
                "- Contents: Read and write\n"
                "- Actions: Read and write\n"
                "If this is a classic PAT, it usually needs:\n"
                "- repo\n"
                "- workflow\n"
                f"\nOriginal error:\n{text}"
            ) from None
        raise


def run_subprocess(
    args: list[str],
    *,
    cwd: Path | None = None,
    env: dict[str, str] | None = None,
) -> str:
    try:
        completed = subprocess.run(
            args,
            cwd=str(cwd) if cwd else None,
            env=env,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
    except subprocess.CalledProcessError as error:
        stderr = (error.stderr or "").strip()
        stdout = (error.stdout or "").strip()
        detail = stderr or stdout or f"exit code {error.returncode}"
        command_text = sanitize_sensitive_subprocess_text(" ".join(args))
        detail_text = sanitize_sensitive_subprocess_text(detail)
        raise ModCompilerError(f"Command failed: {command_text}\n{detail_text}") from None
    return completed.stdout


def sanitize_sensitive_subprocess_text(text: str) -> str:
    sanitized = str(text or "")
    sanitized = re.sub(
        r"(http\.https://github\.com/\.extraheader=AUTHORIZATION:\s+basic\s+)\S+",
        r"\1<redacted>",
        sanitized,
        flags=re.IGNORECASE,
    )
    sanitized = re.sub(r"(x-access-token:)[^\s@]+", r"\1<redacted>", sanitized, flags=re.IGNORECASE)
    sanitized = re.sub(r"github_pat_[A-Za-z0-9_]+", "github_pat_<redacted>", sanitized)
    return sanitized


def write_run_outputs(*, run_dir: Path, result: dict[str, Any]) -> None:
    write_json(run_dir / "result.json", result)
    (run_dir / "SUMMARY.md").write_text(render_summary_markdown(result), encoding="utf-8")


def render_summary_markdown(result: dict[str, Any]) -> str:
    lines = [
        "# Mod Compile",
        "",
        f"- Status: {result.get('status', 'unknown')}",
        f"- Workflow conclusion: {result.get('workflow_conclusion', '') or 'unknown'}",
        f"- Local zip: {result.get('local_zip_path', '') or '-'}",
        f"- GitHub repo: {result.get('github_repo', '') or '-'}",
        f"- Temporary branch: {result.get('dispatch_branch', '') or '-'}",
        f"- Remote zip path: {result.get('remote_zip_path', '') or '-'}",
        f"- GitHub run: {result.get('github_run_url', '') or result.get('github_run_id', '-')}",
        f"- Build artifact folder: {result.get('build_artifact_dir', '') or '-'}",
        f"- Build artifact zip: {result.get('build_artifact_zip', '') or '-'}",
    ]
    if result.get("modrinth_project_url"):
        lines.append(f"- Modrinth project: {result.get('modrinth_project_url', '') or '-'}")
        lines.append(f"- Modrinth artifact folder: {result.get('modrinth_artifact_dir', '') or '-'}")
        lines.append(f"- Modrinth artifact zip: {result.get('modrinth_artifact_zip', '') or '-'}")
    if result.get("warnings"):
        lines.append("")
        lines.append("## Warnings")
        lines.append("")
        for warning in result["warnings"]:
            lines.append(f"- {warning}")
    if result.get("error"):
        lines.append("")
        lines.append("## Error")
        lines.append("")
        lines.append(f"- {result['error']}")
    lines.extend(
        [
            "",
            "## Notes",
            "",
            "- This run used the committed repository HEAD plus the uploaded zip bundle.",
            "- Uncommitted local repository changes were not sent to GitHub by this script.",
        ]
    )
    return "\n".join(lines).rstrip() + "\n"
