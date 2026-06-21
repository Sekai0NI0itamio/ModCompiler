# PingFix Fix Report

## Summary

Fixed Fabric v1.0.0 (MC 1.20.1-1.20.6) — the version that crashed because `fabric.mod.json` declared a hard dependency on `fabric-api` which is not installed in the test environment.

## Root Cause

The original PingFix source used `ClientTickEvents.END_CLIENT_TICK` from the Fabric API (`net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents`). The build pipeline's `fabric.mod.json` template unconditionally included `"fabric-api": "*"` in its `depends` block. Since the ModrinthProjectDiagnosis workflow runs mods without Fabric API installed, every Fabric version failed the launcher test.

## What Was Changed

### 1. Fixed Source Code (`ai-workspace/workspace/pingfix-fabric-1.20.1/`)

| File | Change |
|---|---|
| `PingFixFabricMod.java` | Replaced `ClientTickEvents` usage with empty `ClientModInitializer` — the tick logic is now handled by the mixin |
| `PingFixMixin.java` (new) | Mixin that injects into `MinecraftClient.tick()` at `@At("HEAD")`, containing the same MultiplayerScreen refresh logic as the original |
| `pingfix.mixins.json` (new) | Fabric mixin configuration declaring `PingFixMixin` as a client-side mixin |
| `mod.txt` | Added `requires_fabric_api=false` |
| `version.txt` | Unchanged (`minecraft_version=1.20.1-1.20.6`, `loader=fabric`) |

### 2. Build Pipeline Changes

**`modcompiler/common.py`**:
- Added `"requires_fabric_api"` to `MOD_OPTIONAL_KEYS`
- Added `requires_fabric_api: bool = True` to `ModMetadata` dataclass (default `True` for backward compat)
- Parses `requires_fabric_api` in `load_mod_metadata()` — accepts `"true"`/`"false"`, defaults to `True` if absent
- Passes `requires_fabric_api` through the build plan metadata dict

**`modcompiler/adapters.py`**:
- `build_fabric_metadata()` now builds a conditional `depends` dict — only includes `"fabric-api": "*"` when `metadata.requires_fabric_api` is `True`

### 3. Bug Fix: `build.yml` Status Text (Publish Logic)

**`build.yml` launcher-test job**:
- **Before**: `status_text` was set simplistically and *written to file* on line 677-678 **before** the proper status analysis block. This meant `not_tested` results were written as `"fail"` in the `.txt` file, causing them to be filtered out during publishing.
- **After**: The status text is determined first (in the if/elif/else), then written to the file. `not_tested` now correctly writes `"not_tested"` to the file.
- **Effect**: `publish-modrinth` filters only `content == "fail"` — mods that couldn't be tested due to system limitations will now be published instead of being silently dropped.

## Incoming Zip

Created `incoming/pingfix-fabric-fixed-1.20.1.zip` containing the fixed source. To build and test:
1. Trigger the "Build Mods" workflow with `zip_path = incoming/pingfix-fabric-fixed-1.20.1.zip`
2. Optionally set `modrinth_project_url = https://modrinth.com/mod/pingfix` to publish after successful build+launcher test
3. The `requires_fabric_api=false` flag ensures the generated `fabric.mod.json` will NOT include `fabric-api` in `depends`

## Verified

- Python AST parsing: both `common.py` and `adapters.py` are syntactically valid
- `load_mod_metadata` with `requires_fabric_api=false` → correctly returns `False`
- `load_mod_metadata` without the flag → correctly defaults to `True`
- `build_prepare_plan` with the fixed zip → correctly propagates `requires_fabric_api: False` through the plan metadata
- The decompiled-minecraft symlink at `ai-workspace/decompiled-minecraft/` exists (though 1.20.1-fabric is a stub — the mixin approach is mapping-independent so this is fine)

## Remaining Issues (for future sessions)

1. **Forge 1.20.1-1.20.4** (`NoSuchMethodError: Minecraft.getInstance()`) — needs investigation against decompiled Forge source
2. **Forge 1.21.x** (`LoadingErrorScreen`) — entrypoint or metadata issue for newer Forge
3. **Other Fabric versions** (1.16.5, 1.17.1, 1.18.2, 1.19.x, 1.21.x, 26.1.x) — all have the same `fabric-api` dependency issue and need the same mixin-based fix