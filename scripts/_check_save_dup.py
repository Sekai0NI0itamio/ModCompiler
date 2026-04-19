#!/usr/bin/env python3
"""Debug the duplicate save() removal."""
import re
from pathlib import Path

f = Path("incoming/common-server-core-all-versions/SC1171Forge/src/main/java/com/itamio/servercore/forge/ServerCoreData.java")
content = f.read_text()

# Find the duplicate
idx = content.rfind("public CompoundTag save(CompoundTag tag)")
print(f"Last save() at index {idx}")
print(repr(content[idx-5:idx+80]))

# Try the regex
pattern = r"\n   public CompoundTag save\(CompoundTag tag\) \{\n      return this\.save\(tag, null\);\n   \}\n"
matches = list(re.finditer(pattern, content))
print(f"Regex matches: {len(matches)}")
for m in matches:
    print(f"  at {m.start()}: {repr(m.group())}")
