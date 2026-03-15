from __future__ import annotations

import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from types import SimpleNamespace

from modcompiler.decompile import (
    command_decompile_jar,
    detect_forge_entrypoint,
    infer_range_folders,
    inspect_mod_jar,
    resolve_input_jar_path,
    render_mod_info,
)
from modcompiler.common import load_json


REPO_ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = REPO_ROOT / "version-manifest.json"


class DecompileTests(unittest.TestCase):
    def test_inspect_mod_jar_reads_fabric_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            jar_path = Path(temp_dir) / "fabric-test.jar"
            with zipfile.ZipFile(jar_path, "w") as archive:
                archive.writestr(
                    "fabric.mod.json",
                    json.dumps(
                        {
                            "id": "fabrictest",
                            "version": "1.0.0",
                            "name": "Fabric Test",
                            "description": "Example",
                            "authors": ["Dev"],
                            "license": "MIT",
                            "contact": {"homepage": "https://example.com"},
                            "entrypoints": {"main": ["com.example.FabricTestMod"]},
                            "depends": {"minecraft": "1.20.x", "fabricloader": ">=0.15.0"},
                        }
                    ),
                )
            metadata = inspect_mod_jar(jar_path, manifest)
            self.assertEqual(metadata["loader"], "fabric")
            self.assertEqual(metadata["metadata"]["mod_id"], "fabrictest")
            self.assertEqual(metadata["resolved_range_folders"], ["1.20-1.20.6"])
            self.assertEqual(metadata["metadata"]["entrypoint_class"], "com.example.FabricTestMod")

    def test_inspect_mod_jar_reads_mods_toml(self) -> None:
        manifest = load_json(MANIFEST_PATH)
        with tempfile.TemporaryDirectory() as temp_dir:
            jar_path = Path(temp_dir) / "forge-test.jar"
            with zipfile.ZipFile(jar_path, "w") as archive:
                archive.writestr(
                    "META-INF/mods.toml",
                    "\n".join(
                        [
                            'modLoader="javafml"',
                            'loaderVersion="[40,)"',
                            'license="MIT"',
                            "",
                            "[[mods]]",
                            'modId="forgetest"',
                            'version="1.0.0"',
                            'displayName="Forge Test"',
                            'authors="Dev"',
                            'description="""Example"""',
                            "",
                            "[[dependencies.forgetest]]",
                            'modId="minecraft"',
                            "mandatory=true",
                            'versionRange="[1.18,1.19)"',
                            'ordering="NONE"',
                            'side="BOTH"',
                        ]
                    ),
                )
            metadata = inspect_mod_jar(jar_path, manifest)
            self.assertEqual(metadata["loader"], "forge")
            self.assertEqual(metadata["metadata"]["name"], "Forge Test")
            self.assertEqual(metadata["resolved_range_folders"], ["1.18-1.18.2"])

    def test_inspect_mod_jar_reads_mcmod_info(self) -> None:
        manifest = load_json(MANIFEST_PATH)
        with tempfile.TemporaryDirectory() as temp_dir:
            jar_path = Path(temp_dir) / "legacy-forge.jar"
            with zipfile.ZipFile(jar_path, "w") as archive:
                archive.writestr(
                    "mcmod.info",
                    json.dumps(
                        [
                            {
                                "modid": "legacytest",
                                "name": "Legacy Test",
                                "version": "1.0.0",
                                "description": "Example",
                                "mcversion": "1.12.2",
                                "authorList": ["Dev"],
                            }
                        ]
                    ),
                )
            metadata = inspect_mod_jar(jar_path, manifest)
            self.assertEqual(metadata["loader"], "forge")
            self.assertEqual(metadata["resolved_range_folders"], ["1.12-1.12.2"])

    def test_resolve_input_jar_path_accepts_bare_filename_in_default_folder(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            jar_path = root / "To Be Decompiled" / "example.jar"
            jar_path.parent.mkdir(parents=True, exist_ok=True)
            jar_path.write_bytes(b"jar")
            old_cwd = Path.cwd()
            os.chdir(root)
            try:
                resolved = resolve_input_jar_path("example.jar")
            finally:
                os.chdir(old_cwd)
            self.assertEqual(resolved.resolve(), jar_path.resolve())

    def test_command_decompile_jar_writes_failure_artifacts_for_missing_jar(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            artifact_dir = Path(temp_dir) / "artifacts"
            result = command_decompile_jar(
                SimpleNamespace(
                    jar_path="missing.jar",
                    manifest=str(MANIFEST_PATH),
                    decompiler_jar="missing-decompiler.jar",
                    artifact_dir=str(artifact_dir),
                )
            )
            self.assertEqual(result, 1)
            self.assertTrue((artifact_dir / "decompile.log").exists())
            self.assertTrue((artifact_dir / "SUMMARY.md").exists())
            result_json = json.loads((artifact_dir / "result.json").read_text(encoding="utf-8"))
            self.assertEqual(result_json["status"], "failed")
            self.assertIn("Jar path does not exist.", " ".join(result_json["warnings"]))

    def test_command_decompile_jar_skips_when_no_class_files(self) -> None:
        manifest = load_json(MANIFEST_PATH)
        with tempfile.TemporaryDirectory() as temp_dir:
            jar_path = Path(temp_dir) / "empty.jar"
            with zipfile.ZipFile(jar_path, "w") as archive:
                archive.writestr(
                    "META-INF/mods.toml",
                    "\n".join(
                        [
                            'modLoader="javafml"',
                            'loaderVersion="*"',
                            'license="MIT"',
                            "",
                            "[[mods]]",
                            'modId="emptytest"',
                            'version="1.0.0"',
                            'displayName="Empty Test"',
                        ]
                    ),
                )
            artifact_dir = Path(temp_dir) / "artifacts"
            result = command_decompile_jar(
                SimpleNamespace(
                    jar_path=str(jar_path),
                    manifest=str(MANIFEST_PATH),
                    decompiler_jar=str(jar_path),
                    artifact_dir=str(artifact_dir),
                )
            )
            self.assertEqual(result, 0)
            result_json = json.loads((artifact_dir / "result.json").read_text(encoding="utf-8"))
            self.assertEqual(result_json["status"], "skipped")
            self.assertTrue(any("no .class files" in warning.lower() for warning in result_json["warnings"]))

    def test_detect_forge_entrypoint_finds_mod_annotation(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "src" / "main" / "java" / "com" / "example" / "DemoMod.java"
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text('@Mod("demo")\nclass DemoMod {}\n', encoding="utf-8")
            self.assertEqual(detect_forge_entrypoint(root, "demo"), "com.example.DemoMod")

    def test_render_mod_info_contains_machine_readable_fields(self) -> None:
        rendered = render_mod_info(
            {
                "jar_name": "demo.jar",
                "requested_jar_path": "demo.jar",
                "resolved_jar_path": "To Be Decompiled/demo.jar",
                "loader": "forge",
                "loader_detail": "javafml",
                "metadata_source": "META-INF/mods.toml",
                "supported_minecraft": "[1.20,1.21)",
                "loader_version": "[49,)",
                "resolved_range_folders": ["1.20-1.20.6"],
                "detected_mod_ids": ["demo"],
                "warnings": [],
                "metadata": {
                    "mod_id": "demo",
                    "name": "Demo",
                    "mod_version": "1.0.0",
                    "description": "Example",
                    "authors": ["Dev"],
                    "license": "MIT",
                    "homepage": "",
                    "sources": "",
                    "issues": "",
                    "entrypoint_class": "com.example.DemoMod",
                    "group": "com.example",
                },
            }
        )
        self.assertIn("loader=forge", rendered)
        self.assertIn("resolved_range_folders=1.20-1.20.6", rendered)
        self.assertIn("requested_jar_path=demo.jar", rendered)

    def test_infer_range_folders_handles_interval_and_wildcard(self) -> None:
        manifest = load_json(MANIFEST_PATH)
        self.assertEqual(
            infer_range_folders(manifest, "forge", "[1.18,1.19)"),
            ["1.18-1.18.2"],
        )
        self.assertEqual(
            infer_range_folders(manifest, "fabric", "1.20.x"),
            ["1.20-1.20.6"],
        )


if __name__ == "__main__":
    unittest.main()
