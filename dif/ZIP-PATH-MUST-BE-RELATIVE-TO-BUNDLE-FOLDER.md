---
id: ZIP-PATH-MUST-BE-RELATIVE-TO-BUNDLE-FOLDER
title: Zip bundle must have mod folders at top level — run zip from inside the bundle folder, not workspace root
tags: [zip, prepare, bundle, workflow, bad-layout]
versions: []
loaders: []
symbols: [zip, incoming, prepare]
error_patterns: ["bad zip layout", "no mod folders found at top level", "expected folders only at top level"]
---

## Issue

The build prepare step rejects the zip with a layout error when the zip is
created by running `zip -r` from the workspace root pointing at a subfolder
of `incoming/`. This includes the parent path inside the zip.

## Error

The prepare step logs something like:

```
Error: bad zip layout — expected top-level folders only, found: incoming/AllowOfflineToJoinLan26xFabric/...
```

Or the prepare step silently finds 0 mod folders and fails with no targets.

## Root Cause

The zip contract requires mod folders at the **top level** of the zip:

```
allowofflinetojoinlan-26x.zip
  AllowOfflineToJoinLan26xFabric/
    mod.txt
    version.txt
    src/
  AllowOfflineToJoinLan26xForge/
    ...
```

When you run `zip -r incoming/bundle.zip incoming/bundle-dir/` from the
workspace root, the zip stores paths as `incoming/bundle-dir/ModFolder/...`,
which puts `incoming/bundle-dir/` as the top-level entry — not the mod folders.

## Fix

Always run the `zip` command from **inside** the bundle folder, using the
`cwd` parameter (or `cd` equivalent):

```bash
# WRONG — run from workspace root, includes incoming/ prefix
zip -r incoming/allowofflinetojoinlan-26x.zip incoming/allowofflinetojoinlan-26x/

# CORRECT — run from inside the bundle folder
# cwd: incoming/allowofflinetojoinlan-26x
zip -r ../allowofflinetojoinlan-26x.zip AllowOfflineToJoinLan26xFabric/ \
    AllowOfflineToJoinLan26xForge/ AllowOfflineToJoinLan26xNeoForge/
```

Or use Python's `zipfile` module with explicit relative paths:

```python
import zipfile
from pathlib import Path

bundle_dir = Path('incoming/allowofflinetojoinlan-26x')
zip_path = Path('incoming/allowofflinetojoinlan-26x.zip')

with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
    for file in bundle_dir.rglob('*'):
        if file.is_file():
            # arcname is relative to bundle_dir, so mod folders are at top level
            zf.write(file, file.relative_to(bundle_dir))
```

## Verification

After creating the zip, verify the top-level entries:

```bash
python3 -c "
import zipfile
with zipfile.ZipFile('incoming/allowofflinetojoinlan-26x.zip') as z:
    tops = {n.split('/')[0] for n in z.namelist()}
    print('Top-level entries:', sorted(tops))
"
# Should show: ['AllowOfflineToJoinLan26xFabric', 'AllowOfflineToJoinLan26xForge', ...]
# Must NOT show: ['incoming']
```

## Verified

Encountered and fixed during Allow Offline LAN Join 26.x port (May 2026).
The first zip attempt included `incoming/` in all paths. Fixed by using
`cwd=incoming/allowofflinetojoinlan-26x` when running the zip command.
