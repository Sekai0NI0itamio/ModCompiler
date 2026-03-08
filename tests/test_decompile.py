from __future__ import annotations

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from modcompiler.decompile import (
    detect_forge_entrypoint,
    infer_range_folders,
    inspect_mod_jar,
    render_mod_info,
)
from modcompiler.common import load_json


REPO_ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = REPO_ROOT / "version-manifest.json"


class DecompileTests(unittest.TestCase):
    def test_inspect_mod_jar_reads_fabric_metadata(self) -> None:
        manifest = load_json(MANIFEST_PATH)
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
