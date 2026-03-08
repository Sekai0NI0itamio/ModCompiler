from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from modcompiler.adapters import prepare_workspace
from modcompiler.common import ModMetadata, load_json


REPO_ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = REPO_ROOT / "version-manifest.json"


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


class AdapterTests(unittest.TestCase):
    def test_fabric_adapter_writes_metadata_and_patches_java(self) -> None:
        manifest = load_json(MANIFEST_PATH)
        metadata = ModMetadata(
            mod_id="demo",
            name="Demo",
            mod_version="1.0.0",
            group="com.example.demo",
            entrypoint_class="com.example.demo.DemoMod",
            description="Demo mod",
            authors=["Dev"],
            license="MIT",
            homepage=None,
            sources=None,
            issues=None,
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            workspace = root / "workspace"
            source = root / "source"
            write(
                workspace / "gradle.properties",
                "minecraft_version=1.20.6\nmod_version=old\nmaven_group=old\narchives_base_name=old\n",
            )
            write(
                workspace / "build.gradle",
                "tasks.withType(JavaCompile).configureEach {\n"
                "    it.options.release = 21\n"
                "}\n"
                "java {\n"
                "    sourceCompatibility = JavaVersion.VERSION_21\n"
                "    targetCompatibility = JavaVersion.VERSION_21\n"
                "}\n",
            )
            write(source / "src/main/java/com/example/demo/DemoMod.java", "package com.example.demo;\nclass DemoMod {}\n")
            write(source / "src/main/resources/demo.mixins.json", "{}\n")
            prepare_workspace(
                manifest=manifest,
                range_folder="1.17-1.17.1",
                loader="fabric",
                source_dir=source / "src",
                workspace=workspace,
                minecraft_version="1.17",
                metadata=metadata,
            )
            fabric_metadata = json.loads((workspace / "src/main/resources/fabric.mod.json").read_text(encoding="utf-8"))
            self.assertEqual(fabric_metadata["id"], "demo")
            self.assertEqual(fabric_metadata["entrypoints"]["main"], ["com.example.demo.DemoMod"])
            self.assertIn("demo.mixins.json", fabric_metadata["mixins"])
            build_gradle = (workspace / "build.gradle").read_text(encoding="utf-8")
            self.assertIn("it.options.release = 16", build_gradle)
            self.assertIn("JavaVersion.VERSION_16", build_gradle)

    def test_forge_adapter_writes_mods_toml_and_pack_mcmeta(self) -> None:
        manifest = load_json(MANIFEST_PATH)
        metadata = ModMetadata(
            mod_id="demo",
            name="Demo",
            mod_version="1.0.0",
            group="com.example.demo",
            entrypoint_class="com.example.demo.DemoMod",
            description="Demo mod",
            authors=["Dev"],
            license="MIT",
            homepage=None,
            sources=None,
            issues=None,
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            workspace = root / "workspace"
            source = root / "source"
            write(
                workspace / "gradle.properties",
                "minecraft_version=1.21.11\nminecraft_version_range=[1.21.11,1.22)\n"
                "mapping_version=1.21.11\nmod_id=examplemod\nmod_name=Example Mod\n"
                "mod_license=All Rights Reserved\nmod_version=1.0.0\nmod_group_id=com.example.examplemod\n"
                "mod_authors=YourNameHere\nmod_description=Example\n",
            )
            write(
                workspace / "build.gradle",
                "java.toolchain.languageVersion = JavaLanguageVersion.of(21)\n",
            )
            write(
                source / "src/main/java/com/example/demo/DemoMod.java",
                'package com.example.demo;\n@Mod("examplemod")\nclass DemoMod {}\n',
            )
            prepare_workspace(
                manifest=manifest,
                range_folder="1.21-1.21.11",
                loader="forge",
                source_dir=source / "src",
                workspace=workspace,
                minecraft_version="1.21.11",
                metadata=metadata,
            )
            mods_toml = (workspace / "src/main/resources/META-INF/mods.toml").read_text(encoding="utf-8")
            pack_mcmeta = json.loads((workspace / "src/main/resources/pack.mcmeta").read_text(encoding="utf-8"))
            self.assertIn('modId="demo"', mods_toml)
            self.assertIn('displayName="Demo"', mods_toml)
            self.assertEqual(pack_mcmeta["pack"]["max_format"], 94)
            self.assertEqual(pack_mcmeta["pack"]["min_format"], [94, 1])


if __name__ == "__main__":
    unittest.main()
