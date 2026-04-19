#!/usr/bin/env python3
"""
Fix write_forge_1_21_11 and write_fabric_1_21_11 to apply patch_server_core_data
to ServerCoreData.java from the 1.21.11 source.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
gen = ROOT / "scripts" / "generate_servercore_bundle.py"
content = gen.read_text()

# Fix write_forge_1_21_11 to apply patch_server_core_data to ServerCoreData.java
old = '''        if src_path.exists():
            src = src_path.read_text(encoding="utf-8")
        else:
            # Fall back to 1.21.0 source with patches
            src = read_forge_file(fname)
            if fname == "ServerCoreData.java":
                src = patch_server_core_data(src)
            src = patch_forge_1215_plus(src)
        write(base / "src" / "main" / "java" / FORGE_PKG / fname, src)'''

new = '''        if src_path.exists():
            src = src_path.read_text(encoding="utf-8")
        else:
            # Fall back to 1.21.0 source with patches
            src = read_forge_file(fname)
            src = patch_forge_1215_plus(src)
        # Always apply varargs fix to ServerCoreData.java
        if fname == "ServerCoreData.java":
            src = patch_server_core_data(src)
        write(base / "src" / "main" / "java" / FORGE_PKG / fname, src)'''

if old in content:
    content = content.replace(old, new)
    print("Fixed write_forge_1_21_11 ServerCoreData patch")
else:
    print("ERROR: could not find old block in write_forge_1_21_11")

# Fix write_fabric_1_21_11 similarly
old2 = '''        if src_path.exists():
            src = src_path.read_text(encoding="utf-8")
        else:
            src = read_forge_file(fname)
            if fname == "ServerCoreData.java":
                src = patch_server_core_data(src)
        # Rename package from forge to fabric'''

new2 = '''        if src_path.exists():
            src = src_path.read_text(encoding="utf-8")
        else:
            src = read_forge_file(fname)
        # Always apply varargs fix to ServerCoreData.java
        if fname == "ServerCoreData.java":
            src = patch_server_core_data(src)
        # Rename package from forge to fabric'''

if old2 in content:
    content = content.replace(old2, new2)
    print("Fixed write_fabric_1_21_11 ServerCoreData patch")
else:
    print("ERROR: could not find old block in write_fabric_1_21_11")

gen.write_text(content)
print("Done")
