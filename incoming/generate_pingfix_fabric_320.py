#!/usr/bin/env python3
"""Generate a fresh PingFix fabric zip with mod_version bumped to 3.2.0.

Takes the existing PingFixFabricAll2.zip (which already uses the fixed
pingfix.mixins.json source layout) and repackages it with mod_version=3.2.0
so the build workflow publishes a newer version for every fabric slot.
"""

import re
import zipfile
from pathlib import Path

INPUT = Path(__file__).with_name("PingFixFabricAll2.zip")
OUTPUT = Path(__file__).with_name("pingfix-fabric-3.2.0-all.zip")


def bump_mod_version(data: bytes) -> bytes:
    return re.sub(rb"^mod_version=.*$", b"mod_version=3.2.0", data, flags=re.MULTILINE)


def main() -> int:
    if not INPUT.exists():
        raise SystemExit(f"Input zip not found: {INPUT}")

    with zipfile.ZipFile(INPUT, "r") as zin, zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            if info.filename.endswith("/mod.txt"):
                data = bump_mod_version(data)
            zout.writestr(info, data)

    print(f"Wrote {OUTPUT} ({OUTPUT.stat().st_size / 1024:.1f} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
