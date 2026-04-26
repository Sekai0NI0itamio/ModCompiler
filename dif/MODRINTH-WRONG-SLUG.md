---
id: MODRINTH-WRONG-SLUG
title: Modrinth publish fails — "project could not be found" due to wrong slug
tags: [modrinth, publish, slug, configuration-error]
versions: []
loaders: []
symbols: []
error_patterns: ["The Modrinth project could not be found", "project could not be found"]
---

## Issue

The Modrinth publish step fails with "The Modrinth project could not be found" and no versions are uploaded.

## Error

From `artifacts/modrinth-publish/result.json`:
```json
{
  "status": "failed",
  "warnings": ["The Modrinth project could not be found."]
}
```

## Root Cause

The generator script has the wrong Modrinth slug hardcoded. The slug appears in multiple places:
1. The docstring comment at the top of the generator
2. `MOD_TXT_BASE` — the `homepage=` field written into every `mod.txt`
3. `fabric_mod_json_presplit()` and `fabric_mod_json_split()` — the `"homepage"` field in `fabric.mod.json`
4. The `--modrinth` flag passed to `run_build.py`

The Modrinth project page URL (`/mod/<slug>`) is the canonical slug — not the internal project title or any other name.

## Fix

1. **Find the correct slug** by querying the Modrinth API:
   ```bash
   curl https://api.modrinth.com/v2/project/<slug-or-id> | python3 -m json.tool | grep slug
   ```
   Or open the project page in a browser and read the URL path.

2. **Update ALL occurrences** in the generator script:
   - The docstring
   - `MOD_TXT_BASE` (the `homepage=` line)
   - Any `fabric.mod.json` template functions

3. **Regenerate the bundle** so all `mod.txt` and `fabric.mod.json` files get the correct URL:
   ```bash
   python3 scripts/generate_<mod>_bundle.py
   ```

4. **Re-run with the correct slug**:
   ```bash
   python3 scripts/run_build.py incoming/<bundle>.zip \
       --modrinth "https://modrinth.com/mod/<correct-slug>"
   ```

## Example

Wrong: `https://modrinth.com/mod/time-counter`
Correct: `https://modrinth.com/mod/optimized-day-counter`

The project title was "Time Counter" but the Modrinth slug was `optimized-day-counter`.

## Verified

Confirmed in Day Counter port (run-20260426-022214). After fixing the slug, all 7 new versions uploaded successfully.
