#!/usr/bin/env python3
"""Generate a fresh PingFix fabric zip with mod_version bumped to 3.2.0.

Combines the existing per-range fabric source zips (which already use the
mapping-appropriate source for each Minecraft range) and repackages them
with mod_version=3.2.0 so the build workflow publishes a newer version for
every fabric slot.
"""

import re
import zipfile
from pathlib import Path

HERE = Path(__file__).parent
OUTPUT = HERE / "pingfix-fabric-3.2.0-all.zip"

SOURCE_ZIPS = [
    "pingfix-fabric-1.16-to-1.17.zip",
    "pingfix-fabric-1.18-to-1.19.zip",
    "pingfix-fabric-1.20.zip",
    "pingfix-fabric-1.21-to-1.21.8.zip",
    "pingfix-fabric-1.21.9-to-26.x.zip",
]


def bump_mod_version(data: bytes) -> bytes:
    return re.sub(rb"^mod_version=.*$", b"mod_version=3.2.0", data, flags=re.MULTILINE)


def main() -> int:
    with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as zout:
        for zip_name in SOURCE_ZIPS:
            zip_path = HERE / zip_name
            if not zip_path.exists():
                raise SystemExit(f"Input zip not found: {zip_path}")
            with zipfile.ZipFile(zip_path, "r") as zin:
                for info in zin.infolist():
                    data = zin.read(info.filename)
                    if info.filename.endswith("/mod.txt"):
                        data = bump_mod_version(data)
                    zout.writestr(info, data)

    print(f"Wrote {OUTPUT} ({OUTPUT.stat().st_size / 1024:.1f} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
