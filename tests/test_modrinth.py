from __future__ import annotations

import json
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from modcompiler.modrinth import (
    build_modrinth_version_payload,
    command_publish_modrinth,
    normalize_modrinth_project_ref,
    select_primary_jar,
)


class ModrinthTests(unittest.TestCase):
    def test_normalize_modrinth_project_ref_accepts_slug_and_urls(self) -> None:
        self.assertEqual(normalize_modrinth_project_ref("toggle-sprint"), "toggle-sprint")
        self.assertEqual(
            normalize_modrinth_project_ref("https://modrinth.com/mod/toggle-sprint"),
            "toggle-sprint",
        )
        self.assertEqual(
            normalize_modrinth_project_ref("https://modrinth.com/mod/toggle-sprint/versions"),
            "toggle-sprint",
        )
        self.assertEqual(
            normalize_modrinth_project_ref("https://api.modrinth.com/v2/project/AABBCCDD"),
            "AABBCCDD",
        )

    def test_select_primary_jar_prefers_main_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            jar_root = Path(temp_dir)
            (jar_root / "demo-1.0.0-sources.jar").write_bytes(b"")
            (jar_root / "demo-1.0.0.jar").write_bytes(b"")
            selected = select_primary_jar(jar_root)
            self.assertEqual(selected.name, "demo-1.0.0.jar")

    def test_build_modrinth_version_payload_uses_exact_target_metadata(self) -> None:
        payload = build_modrinth_version_payload(
            project_id="abc123",
            mod={
                "loader": "fabric",
                "minecraft_version": "1.21.11",
                "metadata": {
                    "name": "Toggle Sprint",
                    "mod_version": "1.0.0",
                },
            },
            jar_name="togglesprint-1.0.0.jar",
        )
        self.assertEqual(payload["project_id"], "abc123")
        self.assertEqual(payload["version_number"], "1.0.0")
        self.assertEqual(payload["game_versions"], ["1.21.11"])
        self.assertEqual(payload["loaders"], ["fabric"])
        self.assertEqual(payload["file_parts"], ["file"])

    def test_command_publish_modrinth_writes_failure_artifacts_when_token_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            artifacts_root = root / "combined"
            artifacts_root.mkdir(parents=True, exist_ok=True)
            (artifacts_root / "run-summary.json").write_text(json.dumps({"mods": []}), encoding="utf-8")
            artifact_dir = root / "publish-artifacts"
            old_token = os.environ.pop("MODRINTH_TOKEN", None)
            try:
                exit_code = command_publish_modrinth(
                    SimpleNamespace(
                        artifacts_root=str(artifacts_root),
                        project="https://modrinth.com/mod/toggle-sprint",
                        artifact_dir=str(artifact_dir),
                    )
                )
            finally:
                if old_token is not None:
                    os.environ["MODRINTH_TOKEN"] = old_token
            self.assertEqual(exit_code, 1)
            self.assertTrue((artifact_dir / "SUMMARY.md").exists())
            result = json.loads((artifact_dir / "result.json").read_text(encoding="utf-8"))
            self.assertEqual(result["status"], "failed")
            self.assertIn("MODRINTH_TOKEN", " ".join(result["warnings"]))


if __name__ == "__main__":
    unittest.main()
