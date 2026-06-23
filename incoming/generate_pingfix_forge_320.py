#!/usr/bin/env python3
"""Generate a fresh PingFix Forge zip with mod_version bumped to 3.2.0.

Extracts the Forge directories from the combined fabric+forge source zip and
repackages them with mod_version=3.2.0 so the build workflow publishes a newer
version for every Forge slot.
"""

import re
import zipfile
from pathlib import Path

HERE = Path(__file__).parent
OUTPUT = HERE / "pingfix-forge-3.2.0-all.zip"
SOURCE_ZIP = HERE / "pingfix-1.12.2-1.21.11-fabric-forge.zip"


def bump_mod_version(data: bytes) -> bytes:
    return re.sub(rb"^mod_version=.*$", b"mod_version=3.2.0", data, flags=re.MULTILINE)


def main() -> int:
    if not SOURCE_ZIP.exists():
        raise SystemExit(f"Source zip not found: {SOURCE_ZIP}")

    with zipfile.ZipFile(SOURCE_ZIP, "r") as zin:
        forge_infos = [
            info for info in zin.infolist()
            if info.filename.split("/")[0].endswith("Forge")
        ]
        if not forge_infos:
            raise SystemExit("No Forge directories found in source zip")

        with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as zout:
            for info in forge_infos:
                data = zin.read(info.filename)
                if info.filename.endswith("/mod.txt"):
                    data = bump_mod_version(data)
                zout.writestr(info, data)

    print(f"Wrote {OUTPUT} ({OUTPUT.stat().st_size / 1024:.1f} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
