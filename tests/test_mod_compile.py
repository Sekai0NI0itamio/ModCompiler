from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest import mock

from modcompiler.common import ModCompilerError
from modcompiler.mod_compile import (
    is_transient_github_cli_error,
    normalize_max_parallel,
    parse_github_repo_from_remote,
    run_github_cli,
    sanitize_remote_zip_filename,
    select_downloaded_artifact_path,
)


class ModCompileTests(unittest.TestCase):
    def test_parse_github_repo_from_remote_accepts_ssh_and_https(self) -> None:
        self.assertEqual(
            parse_github_repo_from_remote("git@github.com:owner/repo.git"),
            "owner/repo",
        )
        self.assertEqual(
            parse_github_repo_from_remote("https://github.com/owner/repo"),
            "owner/repo",
        )

    def test_normalize_max_parallel_accepts_all_aliases_and_positive_integer(self) -> None:
        self.assertEqual(normalize_max_parallel("all"), "all")
        self.assertEqual(normalize_max_parallel("MAX"), "all")
        self.assertEqual(normalize_max_parallel("3"), "3")

    def test_normalize_max_parallel_rejects_invalid_values(self) -> None:
        with self.assertRaises(ModCompilerError):
            normalize_max_parallel("0")
        with self.assertRaises(ModCompilerError):
            normalize_max_parallel("abc")

    def test_sanitize_remote_zip_filename_keeps_zip_extension_and_safe_name(self) -> None:
        self.assertEqual(sanitize_remote_zip_filename("My Cool Bundle!.zip"), "My-Cool-Bundle.zip")
        self.assertEqual(sanitize_remote_zip_filename(""), "bundle.zip")

    def test_select_downloaded_artifact_path_prefers_named_dir_then_single_child(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            named = root / "all-mod-builds"
            named.mkdir()
            self.assertEqual(select_downloaded_artifact_path(root, "all-mod-builds"), named)

        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            lone = root / "artifact"
            lone.mkdir()
            self.assertEqual(select_downloaded_artifact_path(root, "missing"), lone)

        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "SUMMARY.md").write_text("summary", encoding="utf-8")
            (root / "run-summary.json").write_text("{}", encoding="utf-8")
            self.assertEqual(select_downloaded_artifact_path(root, "missing"), root)

    def test_is_transient_github_cli_error_matches_connection_reset(self) -> None:
        self.assertTrue(is_transient_github_cli_error("read: connection reset by peer"))
        self.assertFalse(is_transient_github_cli_error("HTTP 403 forbidden"))

    def test_run_github_cli_retries_transient_failure(self) -> None:
        side_effects = [
            ModCompilerError("Command failed: gh run view\nread: connection reset by peer"),
            "ok\n",
        ]

        with mock.patch("modcompiler.mod_compile.run_subprocess", side_effect=side_effects) as runner:
            with mock.patch("modcompiler.mod_compile.time.sleep"):
                output = run_github_cli(["gh", "run", "view"], github_token="token", retries=2)

        self.assertEqual(output, "ok\n")
        self.assertEqual(runner.call_count, 2)


if __name__ == "__main__":
    unittest.main()
