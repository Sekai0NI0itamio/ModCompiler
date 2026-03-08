from __future__ import annotations

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from modcompiler import cli
from modcompiler.common import (
    MOD_OPTIONAL_KEYS,
    MOD_REQUIRED_KEYS,
    ModCompilerError,
    VERSION_KEYS,
    build_prepare_plan,
    load_json,
    parse_key_value_file,
    render_summary_markdown,
    resolve_range,
)


REPO_ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = REPO_ROOT / "version-manifest.json"


class CommonTests(unittest.TestCase):
    def test_parse_key_value_file_accepts_comments(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            config = Path(temp_dir) / "mod.txt"
            config.write_text(
                "# comment\nmod_id=testmod\nname=Test Mod\nmod_version=1.0.0\ngroup=com.example\n"
                "entrypoint_class=com.example.TestMod\ndescription=desc\nauthors=Dev\nlicense=MIT\n",
                encoding="utf-8",
            )
            parsed = parse_key_value_file(config, MOD_REQUIRED_KEYS, MOD_OPTIONAL_KEYS)
            self.assertEqual(parsed["mod_id"], "testmod")
            self.assertEqual(parsed["authors"], "Dev")

    def test_parse_key_value_file_rejects_unknown_keys(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            config = Path(temp_dir) / "version.txt"
            config.write_text("minecraft_version=1.16.5\nloader=forge\nextra=value\n", encoding="utf-8")
            with self.assertRaises(ModCompilerError):
                parse_key_value_file(config, VERSION_KEYS, set())

    def test_resolve_range_uses_inclusive_bounds(self) -> None:
        manifest = load_json(MANIFEST_PATH)
        self.assertEqual(resolve_range(manifest, "1.12")["folder"], "1.12-1.12.2")
        self.assertEqual(resolve_range(manifest, "1.21")["folder"], "1.21-1.21.8")
        self.assertEqual(resolve_range(manifest, "1.21.8")["folder"], "1.21-1.21.8")
        self.assertEqual(resolve_range(manifest, "1.21.11")["folder"], "1.21.9-1.21.11")

    def test_build_prepare_plan_validates_archive_and_generates_warning(self) -> None:
        manifest = load_json(MANIFEST_PATH)
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            zip_path = root / "mods.zip"
            with zipfile.ZipFile(zip_path, "w") as archive:
                archive.writestr("Demo/src/main/java/com/example/DemoMod.java", "class DemoMod {}")
                archive.writestr("Demo/mod.txt", "\n".join([
                    "mod_id=demo",
                    "name=Demo",
                    "mod_version=1.0.0",
                    "group=com.example.demo",
                    "entrypoint_class=com.example.demo.DemoMod",
                    "description=Demo",
                    "authors=Dev",
                    "license=MIT",
                ]))
                archive.writestr("Demo/version.txt", "minecraft_version=1.12\nloader=forge\n")
            plan = build_prepare_plan(zip_path, root / "prepared", manifest)
            self.assertEqual(len(plan["mods"]), 1)
            self.assertEqual(plan["mods"][0]["range_folder"], "1.12-1.12.2")
            self.assertTrue(plan["mods"][0]["warnings"])

    def test_build_prepare_plan_resolves_new_minor_family_range(self) -> None:
        manifest = load_json(MANIFEST_PATH)
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            zip_path = root / "mods.zip"
            with zipfile.ZipFile(zip_path, "w") as archive:
                archive.writestr("Demo/src/main/java/com/example/DemoMod.java", "class DemoMod {}")
                archive.writestr("Demo/mod.txt", "\n".join([
                    "mod_id=demo",
                    "name=Demo",
                    "mod_version=1.0.0",
                    "group=com.example.demo",
                    "entrypoint_class=com.example.demo.DemoMod",
                    "description=Demo",
                    "authors=Dev",
                    "license=MIT",
                ]))
                archive.writestr("Demo/version.txt", "minecraft_version=1.18.2\nloader=forge\n")
            plan = build_prepare_plan(zip_path, root / "prepared", manifest)
            self.assertEqual(plan["mods"][0]["range_folder"], "1.18-1.18.2")

    def test_build_prepare_plan_rejects_unsupported_loader(self) -> None:
        manifest = load_json(MANIFEST_PATH)
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            zip_path = root / "mods.zip"
            with zipfile.ZipFile(zip_path, "w") as archive:
                archive.writestr("Demo/src/main/java/com/example/DemoMod.java", "class DemoMod {}")
                archive.writestr("Demo/mod.txt", "\n".join([
                    "mod_id=demo",
                    "name=Demo",
                    "mod_version=1.0.0",
                    "group=com.example.demo",
                    "entrypoint_class=com.example.demo.DemoMod",
                    "description=Demo",
                    "authors=Dev",
                    "license=MIT",
                ]))
                archive.writestr("Demo/version.txt", "minecraft_version=1.12.2\nloader=fabric\n")
            with self.assertRaises(ModCompilerError):
                build_prepare_plan(zip_path, root / "prepared", manifest)

    def test_render_summary_markdown_contains_status_table(self) -> None:
        markdown = render_summary_markdown([
            {
                "metadata": {"mod_id": "demo", "name": "Demo"},
                "minecraft_version": "1.16.5",
                "range_folder": "1.16.5",
                "loader": "forge",
                "status": "success",
                "jar_names": ["demo.jar"],
                "log_relpath": "build.log",
            }
        ])
        self.assertIn("| demo | Demo | 1.16.5 | 1.16.5 | forge | success | demo.jar | build.log |", markdown)

    def test_bundle_generates_summary_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            artifact_root = root / "artifacts" / "mod-demo"
            artifact_root.mkdir(parents=True, exist_ok=True)
            (artifact_root / "build.log").write_text("log", encoding="utf-8")
            (artifact_root / "result.json").write_text(
                json.dumps(
                    {
                        "slug": "demo-forge-1-16-5",
                        "loader": "forge",
                        "minecraft_version": "1.16.5",
                        "range_folder": "1.16.5",
                        "metadata": {"mod_id": "demo", "name": "Demo"},
                        "java_version": 8,
                        "warnings": [],
                        "status": "success",
                        "jar_names": ["demo.jar"],
                        "log_relpath": "build.log",
                    }
                ),
                encoding="utf-8",
            )
            exit_code = cli.main([
                "bundle",
                "--artifacts-root",
                str(root / "artifacts"),
                "--output-dir",
                str(root / "combined"),
            ])
            self.assertEqual(exit_code, 0)
            summary = json.loads((root / "combined" / "run-summary.json").read_text(encoding="utf-8"))
            self.assertEqual(summary["mods"][0]["slug"], "demo-forge-1-16-5")


        if __name__ == "__main__":
            unittest.main()
