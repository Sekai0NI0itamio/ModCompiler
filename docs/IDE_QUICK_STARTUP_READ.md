# IDE Quick Startup Read

**Read this first. Every time. Before doing anything.**

This is a condensed guide for AI IDE agents (Kiro, Copilot, etc.) working in
this repository. It captures what the user wants, how this repo works, and every
mistake a previous agent made so you don't repeat them.

---

## What This Repository Is

A GitHub-based system for building and publishing Minecraft mods across every
supported version (1.8.9 through 26.x) and loader (Forge, Fabric, NeoForge).

The user co-builds mods with the IDE agent, approves them locally, then uses
this system to compile and publish them to Modrinth across all versions.

**You do not compile locally for multi-version builds. Ever.**
All cross-version compilation runs through GitHub Actions via `run_build.py`.

---

## What the User Will Ask You to Do

In rough order of frequency:

1. **"Update this mod to all versions"** — given a Modrinth link, port the mod
   to every supported MC version and loader, build via GitHub Actions, publish.

2. **"Create a new mod"** — build the first version locally in
   `Mod Developement/1.12.2-forge/`, test it, then port to all versions.

3. **"Fix the failing builds"** — read build logs, fix compile errors, rerun
   only the failed targets.

4. **"Publish this mod to Modrinth"** — prepare the Modrinth draft bundle and
   upload it.

5. **"Add documentation"** — write DIF entries, example docs, update the IDE
   manual after completing a port.

---

## The Single Most Important Rule

**Run the diagnosis before building anything.**

```bash
python3 scripts/fetch_modrinth_project.py \
    --project https://modrinth.com/mod/<slug> \
    --output-dir /tmp/diagnosis
cat /tmp/diagnosis/summary.txt
```

This tells you exactly which versions are already published. Build ONLY the
missing ones. The previous agent wasted 5+ build runs rebuilding already-published
versions because it skipped this step.

**Then immediately run the manifest comparison to find ALL missing targets:**

```python
# scripts/_check_missing.py — run this after every fetch_modrinth_project.py
import json

with open('version-manifest.json') as f:
    manifest = json.load(f)

all_targets = set()
for r in manifest['ranges']:
    for loader, cfg in r['loaders'].items():
        versions = cfg.get('supported_versions', [r.get('min_version')])
        for v in versions:
            all_targets.add((v, loader))

# Build published set from fetch_modrinth_project.py output
# e.g. published = {('1.20.1', 'forge'), ('1.20.1', 'fabric'), ...}
published = set()  # fill from diagnosis

missing = sorted(all_targets - published)
print(f'MISSING ({len(missing)} targets):')
for t in missing:
    print(f'  {t[0]:12} {t[1]}')
```

**The manifest covers ALL loaders (Forge, Fabric, NeoForge) for ALL version ranges.**
A mod that was originally Forge-only still needs Fabric and NeoForge ports.
Never assume a mod only needs one loader — always check the manifest.

---

## Mistakes the Previous Agent Made (Don't Repeat These)

These are real mistakes made during actual ports, corrected by the user.
Learn from them.

### Mistake 1: Skipped the diagnosis step entirely

**What happened**: Jumped straight into writing source code for all 28 version
targets without checking what was already on Modrinth. 54 versions were already
published.

**User correction**: "You are stupid, what the heck are you doing. First run the
diagnoser that diagnosis a project link and finds all the missing versions."

**Rule**: ALWAYS run `fetch_modrinth_project.py` first. Build only missing targets.

---

### Mistake 14: Declared port complete without checking version-manifest.json (Stackable Totems port)

**What happened**: After building Forge and NeoForge targets, the agent ran
`fetch_modrinth_project.py`, saw 35 versions published, and declared the port
complete. The user had to point out that Fabric versions and Forge 1.12.2 were
still missing — 22 more targets.

**User correction**: "Are you sure you updated it to all versions? Remember my
repository supports versions from 1.8.9 to the newest, with fabric versions as well."

**Root cause**: The agent computed missing targets by comparing against a manually-
written list. That list only included loaders the mod was originally published on
(Forge only). The agent never cross-referenced against `version-manifest.json`.

**Rule**: After `fetch_modrinth_project.py`, ALWAYS run the manifest comparison:

```python
import json

with open('version-manifest.json') as f:
    manifest = json.load(f)

all_targets = set()
for r in manifest['ranges']:
    for loader, cfg in r['loaders'].items():
        versions = cfg.get('supported_versions', [r.get('min_version')])
        for v in versions:
            all_targets.add((v, loader))

# published = set of (version, loader) tuples from fetch_modrinth_project.py output
missing = sorted(all_targets - published)
print(f'MISSING ({len(missing)}):')
for t in missing:
    print(f'  {t[0]:12} {t[1]}')
```

Only declare done when `missing` is empty. The manifest covers ALL loaders
(Forge, Fabric, NeoForge) for ALL supported version ranges. A mod that was
originally Forge-only still needs Fabric and NeoForge ports.

---

### Mistake 2: Ignored the existing generator script

**What happened**: Hand-wrote all source files from scratch instead of using
`scripts/generate_veinminer_bundle.py` which already existed with correct,
tested source for every version.

**Rule**: Before writing any source code, check `scripts/` for an existing
generator. If `scripts/generate_<modname>_bundle.py` exists, use it.

```bash
ls scripts/generate_*_bundle.py
python3 scripts/generate_veinminer_bundle.py --failed-only
```

---

### Mistake 3: Rebuilt already-published targets on every retry

**What happened**: Used `--failed-only` from the generator but didn't filter
out targets that were already published on Modrinth. The generator's failure
list included targets from earlier runs that had since been published.

**User correction**: "only fucking build the missing ones"

**Rule**: After generating the bundle, always filter the zip manually:

```python
import zipfile
already_published = {'VeinMiner-1.21-1.21.1-forge', ...}  # from diagnosis
with zipfile.ZipFile('incoming/veinminer-all-versions.zip') as src:
    with zipfile.ZipFile('incoming/veinminer-missing-only.zip', 'w', zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            top = item.filename.split('/')[0]
            if top not in already_published:
                dst.writestr(item, src.read(item.filename))
```

Update `already_published` after each successful run before the next retry.

---

### Mistake 4: Issued multiple commands in one turn

**What happened**: Chained multiple long-running commands together in one
terminal call, causing the IDE to hang or time out.

**User correction**: "NOTE: This command took 4 days to run, it seems like
your confusing the IDE. Please stop issuing multiple command runs. Issue one
command run at a time."

**Rule**: One command per turn. Never chain `&&` for long-running operations.
Run `git push` and `python3 scripts/run_build.py` as separate steps.

---

### Mistake 5: Guessed API names instead of looking them up

**What happened**: Used `KeyMapping.Category.lookup()` and
`KeyMapping.Category.create()` — methods that don't exist. Used
`net.minecraft.util.registry.Registry` for Fabric 1.19 when the actual
yarn path was different.

**Rule**: NEVER guess API names. Always check:
1. `DecompiledMinecraftSourceCode/<version>-<loader>/` — instant, no workflow needed
2. `dif/` — search for known issues first: `python3 scripts/dif_search.py "<error>"`
3. The AI Source Search workflow — for versions not in the decompiled sources

```bash
# Check before writing code
grep -n "class KeyMapping" DecompiledMinecraftSourceCode/1.21.9-forge/net/minecraft/client/KeyMapping.java
python3 scripts/dif_search.py "KeyMapping Category String"
```

---

### Mistake 6: Trusted decompiled sources for anchor_only versions

**What happened**: The decompiled sources for `1.19-fabric` showed
`net.minecraft.registry.Registries` (generated with 1.19.4 yarn). Used that
path for 1.19, 1.19.1, 1.19.2 — but those versions use the old
`net.minecraft.util.registry.Registry` path because Loom downloads their
actual yarn, not the anchor's.

**Rule**: When a range uses `exact_dependency_mode: anchor_only`, the decompiled
sources reflect the ANCHOR's yarn. Non-anchor versions may use different package
paths. Add `dependency_overrides` in `version-manifest.json` to pin their yarn.
See DIF entry `ANCHOR-ONLY-YARN-PITFALL`.

**Extended rule from TPA Teleport port**: This also applies to method names, not
just package paths. The `1.16.5/fabric/template` uses the 1.17 branch as its
base. The decompiled sources show `getServer()` but the actual 1.16.5 yarn
build.10 uses `getMinecraftServer()`. Never trust decompiled sources for
anchor_only templates — check the `dependency_overrides` in `version-manifest.json`
to find the actual yarn version, then verify against that.

---

### Mistake 7: Didn't document issues after finishing

**What happened**: Completed the port without adding DIF entries or example docs.
The user had to explicitly ask for documentation.

**Rule**: After every successful port, always:
1. Add DIF entries for every compile error encountered
2. Write `docs/examples/<MOD>_ALL_VERSIONS.md`
3. Update `docs/IDE_AGENT_INSTRUCTION_SHEET.txt`
4. Update `docs/IDE_QUICK_STARTUP_READ.md` with new mistakes and DIF entries
5. Commit everything

---

### Mistake 8: Used fsWrite tool with large content (TPA Teleport port)

**What happened**: The `fsWrite` tool silently failed when given large file
content. The file was never created and no error was shown until the next
operation tried to use it.

**Rule**: For large files (generator scripts, example docs), write them using
bash heredoc (`cat > file << 'EOF'`) in multiple chunks. Verify the file exists
and has correct syntax after writing:

```bash
python3 -c "import ast; ast.parse(open('scripts/generate_tpa_bundle.py').read()); print('Syntax OK')"
```

---

### Mistake 9: Applied string replacements to the wrong source in the derivation chain (TPA Teleport port)

**What happened**: When building source strings via `.replace()` chains, a fix
applied to `SRC_1165_FABRIC` (e.g. `getMinecraftServer()`) propagated to all
derived sources (`SRC_117_FABRIC = SRC_1165_FABRIC`, `SRC_118_FABRIC`, etc.)
because they inherit from it. The fix was correct for 1.16.5 but wrong for 1.17+.

**Rule**: When deriving source strings from a base via `.replace()`, always
verify that the fix is correct for ALL versions that inherit from that base.
If a fix is version-specific, apply it only to that version's string and then
revert it in the derived version:

```python
SRC_117_FABRIC = SRC_1165_FABRIC.replace(
    "src.getMinecraftServer().getPlayerManager()",
    "src.getServer().getPlayerManager()"
)  # 1.17+ uses getServer(), only 1.16.5 uses getMinecraftServer()
```

---

### Mistake 10: Lambda replacement introduced a non-final variable capture (TPA Teleport port)

**What happened**: In 1.20+, `sendSuccess()` takes a `Supplier<Component>` lambda.
When deriving 1.20 source from 1.19 via `.replace("sendSuccess(Component.literal(",
"sendSuccess(() -> Component.literal(")`, any `count` variable incremented in a
loop above the call became a non-final lambda capture — causing a compile error.

**Rule**: After introducing a supplier lambda via string replacement, check
whether any variables captured in that lambda are modified before the lambda.
If so, add `final int finalCount = count;` immediately before the lambda:

```python
SRC_120_FORGE = SRC_119_FORGE.replace(
    "src.sendSuccess(Component.literal(",
    "src.sendSuccess(() -> Component.literal("
).replace(
    'src.sendSuccess(() -> Component.literal("Accepted "+count+',
    'final int finalCount=count; src.sendSuccess(() -> Component.literal("Accepted "+finalCount+'
)
```

See DIF entry `LAMBDA-COUNT-CAPTURE`.

---

### Mistake 11: Stripped UUID arg incorrectly when replacing sendMessage (TPA Teleport port)

**What happened**: When replacing `sendMessage(Component.literal(` with
`sendSystemMessage(Component.literal(` to fix the 1.19+ API change, only the
opening of the call was replaced. The trailing `, player.getUUID())` remained,
causing a different compile error.

**Rule**: When replacing `sendMessage` with `sendSystemMessage`, also strip the
UUID argument. The replacement must handle the full call:

```python
# Step 1: replace the method name
src = src.replace(".sendMessage(Component.literal(", ".sendSystemMessage(Component.literal(")
# Step 2: strip the trailing UUID arg (closing paren of literal + UUID arg)
src = src.replace("), req.getUUID());", "));")
src = src.replace("), to.getUUID());", "));")
src = src.replace("), me.getUUID());", "));")
```

See DIF entry `FORGE-SEND-MESSAGE-UUID-REMOVED`.

---

### Mistake 12: Used wrong version string for 26.x targets (TPA Teleport port)

**What happened**: Used `26.1-26.x` as the `minecraft_version` in `version.txt`
for 26.x targets. The prepare script rejected it immediately with
`Unsupported version format '26.x'`.

**Rule**: The `minecraft_version` in `version.txt` must match an entry in
`supported_versions` in `version-manifest.json`. For the `26.1-26.x` range,
the anchor version is `26.1.2`. Use that, not the folder name.

```
minecraft_version=26.1.2
loader=forge
```

See DIF entry `VERSION-STRING-26X-ANCHOR`.

---

### Mistake 13: Assumed NeoForge supports all 1.20.x versions (TPA Teleport port)

**What happened**: Generated NeoForge targets for `1.20-1.20.6` as a range.
The build failed with "does not support exact Minecraft 1.20" because NeoForge
didn't exist until 1.20.2.

**Rule**: Always check `version-manifest.json` for the exact `supported_versions`
list before generating targets. NeoForge for the 1.20 range only supports
`1.20.2`, `1.20.4`, `1.20.5`, `1.20.6`. Similarly, the 1.17 template only
supports `1.17.1`, not `1.17`.

See DIF entries `NEOFORGE-120-SUPPORTED-VERSIONS` and
`FORGE-117-TEMPLATE-SUPPORTS-1171-ONLY`.

---

### Mistake 15: Generator source string missing f-prefix — literal `{MOD_ID}` and `{{` in Java output (Account Switcher port)

**What happened**: A source string for Forge 26.1.2 was defined as `"""\..."""` instead
of `f"""\..."""`. Python did not substitute `{MOD_ID}` or convert `{{`/`}}` to `{`/`}`.
The Java compiler saw `@Mod("{MOD_ID}")` and `public class AccountSwitcherMod {{` and
failed with `illegal start of expression`.

**User correction**: Build log showed `illegal start of expression` at the `{{` line.

**Rule**: Every generator source string that contains `{VAR}` placeholders or `{{`/`}}`
for literal Java braces MUST use the `f"""..."""` prefix. After generating, spot-check
at least one output file before committing:

```bash
python3 scripts/generate_<mod>_bundle.py
cat incoming/<mod>-all-versions/<slug>/src/main/java/.../MyMod.java | head -5
# Must show: @Mod("mymod") not @Mod("{MOD_ID}")
# Must show: public class MyMod { not public class MyMod {{
```

See DIF entry `FORGE-FSTRING-ESCAPING-IN-GENERATOR`.

---

### Mistake 16: Assumed Fabric uses same `supported_versions` as Forge for the same range folder (Account Switcher port)

**What happened**: After 41 targets were published, the manifest comparison showed
`1.17 fabric` and `1.21 fabric` still missing. The generator was targeting `1.17.1`
and `1.21.1` for Fabric (matching Forge), but the manifest's `supported_versions`
for Fabric in those ranges is `["1.17"]` and `["1.21"]` respectively.

**User correction**: Manifest comparison after run 2 revealed 2 still-missing targets.

**Rule**: Always read `supported_versions` per-loader from `version-manifest.json`.
Never assume Fabric and Forge share the same version list for a range folder.

```python
for r in manifest['ranges']:
    for loader, cfg in r['loaders'].items():
        versions = cfg.get('supported_versions', [r.get('min_version')])
        # versions is DIFFERENT per loader — do not share between loaders
```

Key differences:
- `1.17-1.17.1/fabric`: `["1.17"]` — Forge has `["1.17.1"]`
- `1.21-1.21.1/fabric`: `["1.21"]` — Forge/NeoForge have `["1.21", "1.21.1"]`

See DIF entry `FABRIC-MANIFEST-VERSION-VS-PUBLISHED-VERSION`.

---

### Mistake 17: Created zip from workspace root, including `incoming/` prefix in all paths (Allow Offline LAN Join port)

**What happened**: Ran `zip -r incoming/bundle.zip incoming/bundle-dir/` from the
workspace root. The zip stored all paths as `incoming/bundle-dir/ModFolder/...`,
putting `incoming/bundle-dir/` as the top-level entry instead of the mod folders.
The prepare step would have rejected it with a bad zip layout error.

**Rule**: Always run the `zip` command from **inside** the bundle folder using the
`cwd` parameter, so mod folders appear at the top level of the zip:

```bash
# WRONG — run from workspace root
zip -r incoming/bundle.zip incoming/bundle-dir/

# CORRECT — cwd into the bundle folder first
# cwd: incoming/bundle-dir
zip -r ../bundle.zip ModFolder1/ ModFolder2/ ModFolder3/
```

Verify the zip structure before committing:
```bash
python3 -c "
import zipfile
with zipfile.ZipFile('incoming/bundle.zip') as z:
    tops = {n.split('/')[0] for n in z.namelist()}
    print('Top-level entries:', sorted(tops))
# Must NOT show 'incoming' as a top-level entry
"
```

See DIF entry `ZIP-PATH-MUST-BE-RELATIVE-TO-BUNDLE-FOLDER`.

---

### Mistake 17: Used wrong TickEvent package for Forge 1.16.5+ (Keep Inventory port)

**What happened**: Used `net.minecraftforge.fml.common.gameevent.TickEvent` for all
Forge versions. This package only exists in 1.8.9–1.12.2. Forge 1.16.5+ moved
`TickEvent` to `net.minecraftforge.event.TickEvent`.

**Rule**: Always use the correct TickEvent package per era:
- 1.8.9–1.12.2: `net.minecraftforge.fml.common.gameevent.TickEvent`
- 1.16.5+: `net.minecraftforge.event.TickEvent`

See DIF entry `FORGE-TICKEVENT-PACKAGE-HISTORY`.

---

### Mistake 18: Assumed WorldEvent → LevelEvent rename happened in 1.18 (Keep Inventory port)

**What happened**: Used `LevelEvent.Load` and `TickEvent.LevelTickEvent` for Forge 1.18.x.
The rename actually happened in **1.19**, not 1.18. Forge 1.18.x still uses
`net.minecraftforge.event.world.WorldEvent` and `TickEvent.WorldTickEvent`.

**Rule**: The boundary is 1.19:
- 1.12.2–1.18.2: `WorldEvent.Load` + `TickEvent.WorldTickEvent`
- 1.19+: `LevelEvent.Load` + `TickEvent.LevelTickEvent`

See DIF entry `FORGE-WORLDEVENT-VS-LEVELEVENT-BOUNDARY`.

---

### Mistake 19: Called Level.getGameRules() in Forge 1.21.3+ (Keep Inventory port)

**What happened**: `getGameRules()` was removed from `Level` in Forge 1.21.3. The
method only exists on `ServerLevel`. Calling it on a `Level` variable causes a
compile error.

**Rule**: In Forge 1.21.3+, always cast to `ServerLevel` before calling `getGameRules()`.
Use `instanceof ServerLevel` check on the `LevelAccessor` from `LevelEvent.getLevel()`.

See DIF entry `FORGE-LEVEL-GETGAMERULES-REMOVED-1213`.

---

### Mistake 20: Used net.minecraft.world.level.GameRules in 1.21.9+ (Keep Inventory port)

**What happened**: `GameRules` moved to `net.minecraft.world.level.gamerules.GameRules`
in Minecraft 1.21.9. The old package no longer exists. The API also changed from
`BooleanValue` with `getRule(key).set(value, server)` to `GameRule<Boolean>` with
`set(key, value, server)` and `get(key)`.

This affects Forge 1.21.9+, NeoForge 1.21.9+, and Fabric 26.1+.

**Rule**: Check the GameRules package boundary:
- 1.19–1.21.8: `net.minecraft.world.level.GameRules` with `RULE_KEEPINVENTORY`
- 1.21.9+: `net.minecraft.world.level.gamerules.GameRules` with `KEEP_INVENTORY`

See DIF entry `FORGE-GAMERULES-PACKAGE-MOVED-1219`.

---

### Mistake 21: Trusted decompiled sources for NeoForge 1.20.x tick events (Keep Inventory port)

**What happened**: The decompiled sources in `DecompiledMinecraftSourceCode/1.20.2-neoforge/`
show `net.neoforged.neoforge.event.tick.ServerTickEvent` — but those sources were
generated with a newer NeoForge build than what the template actually resolves to
(NeoForge 20.2.93). The package doesn't exist in 20.2.93.

**Rule**: For NeoForge 1.20.2–1.20.6, avoid `ServerTickEvent`. Use `ServerStartingEvent`
only. The decompiled sources for NeoForge 1.20.x may reflect a newer build than the
template uses. Always test with a minimal build first.

See DIF entry `NEOFORGE-SERVERTICK-NOT-IN-EARLY-20X`.

---

Agent:
  1. Run fetch_modrinth_project.py → see what's already published
  2. Run manifest comparison → find ALL missing targets across ALL loaders
  3. Check scripts/ for existing generator → use it if found
  4. Search dif/ for known issues with this mod type
  5. Generate bundle with only missing targets
  6. git add + git commit + git push (one command)
  7. python3 scripts/run_build.py ... (one command, blocking)
  8. Read results → fix only failed targets
  9. Repeat steps 5-8 until all targets pass
  10. Run manifest comparison again → confirm 0 missing
  11. Verify final state on Modrinth
  12. Add DIF entries + example doc + update this file + commit
```

Total build runs for a typical mod: 2–4. If you're on run 6+, stop and
re-read the DIF entries — you're probably guessing APIs.

---

## Key Files to Know

| File | Purpose |
|------|---------|
| `version-manifest.json` | Source of truth for all supported versions and loaders |
| `scripts/run_build.py` | Trigger GitHub Actions build + wait + download results |
| `scripts/fetch_modrinth_project.py` | Fetch what's already published on Modrinth |
| `scripts/generate_<mod>_bundle.py` | Generate the build zip for a specific mod |
| `scripts/dif_search.py` | Search the DIF knowledge base for known errors |
| `dif/` | DIF entries — searchable issue/fix database |
| `docs/examples/` | Real-world port examples with full build histories |
| `DecompiledMinecraftSourceCode/` | Pre-committed MC API sources for all 64 version+loader combos |
| `incoming/` | Where build zips go before triggering GitHub Actions |
| `ModCompileRuns/` | Where build results land after `run_build.py` completes |

---

## Key Commands

```bash
# 1. Diagnose what's already published
python3 scripts/fetch_modrinth_project.py --project <url> --output-dir /tmp/diag

# 2. Search for known issues
python3 scripts/dif_search.py "error text or API name"

# 3. Look up an API in decompiled sources (instant, no workflow)
grep -rn "ClassName" DecompiledMinecraftSourceCode/1.21.9-forge/

# 4. Generate bundle (only failed targets)
python3 scripts/generate_<mod>_bundle.py --failed-only

# 5. Commit and push
git add incoming/ && git commit -m "..." && git push

# 6. Build and wait (blocking — do not background this)
python3 scripts/run_build.py incoming/<bundle>.zip \
    --modrinth https://modrinth.com/mod/<slug> \
    --max-parallel all

# 7. Check run results
cat ModCompileRuns/run-<timestamp>/SUMMARY.md
```

---

## What the User Expects From You

Based on this conversation, the user:

- **Wants efficiency** — minimal build runs, no wasted CI minutes
- **Wants correctness** — look up APIs, don't guess
- **Wants you to use existing tools** — check for generator scripts before writing code
- **Wants documentation** — DIF entries and example docs after every port
- **Will correct you directly** — if you make a mistake, the user will say so bluntly. Take the correction, don't repeat the mistake.
- **Does not want explanations of what you're about to do** — just do it
- **Does not want multiple commands chained** — one command per turn
- **Does not want weird shell commands** — use the right tool for the job. Don't pipe to grep when you can use the search tools. Don't use `cat` when you can use `readFile`.

---

## Version-Specific API Quick Reference

This is a condensed cheat sheet. For full details, see the DIF entries.

### Forge command registration

| MC Version | Source class | Register commands |
|------------|-------------|-------------------|
| 1.8.9 | `CommandBase` | `FMLServerStartingEvent.registerServerCommand()` |
| 1.12.2 | `CommandBase` | `FMLServerStartingEvent.registerServerCommand()` |
| 1.16.5 | `CommandSource` | `@SubscribeEvent RegisterCommandsEvent` (2-arg dispatcher) |
| 1.17–1.21.5 | `CommandSourceStack` | `@SubscribeEvent RegisterCommandsEvent` |
| 1.21.6–26.x | `CommandSourceStack` | `RegisterCommandsEvent.BUS.addListener()` (EventBus 7) |

### Fabric command registration

| MC Version | Package | Callback signature |
|------------|---------|-------------------|
| 1.16.5–1.18.x | `command.v1` | `(dispatcher, dedicated)` |
| 1.19–26.x | `command.v2` | `(dispatcher, registryAccess, dedicated)` |

### Sending messages to players

| MC Version | Loader | Method |
|------------|--------|--------|
| 1.8.9 | Forge | `player.addChatMessage(new ChatComponentText("..."))` |
| 1.12.2 | Forge | `player.sendMessage(new TextComponentString("..."))` |
| 1.16.5 | Forge | `player.sendMessage(new StringTextComponent("..."), uuid)` |
| 1.17–1.18.x | Forge | `player.sendMessage(new TextComponent("..."), uuid)` |
| 1.19+ | Forge/NeoForge | `player.sendSystemMessage(Component.literal("..."))` |
| 1.16.5–1.18.x | Fabric | `player.sendMessage(new LiteralText("..."), false)` |
| 1.19–1.20.x | Fabric | `player.sendMessage(Text.literal("..."), false)` |
| 1.21+ | Fabric | `player.sendSystemMessage(Text.literal("..."))` |

### sendSuccess / sendFeedback

| MC Version | Loader | Signature |
|------------|--------|-----------|
| 1.16.5 | Forge | `src.sendSuccess(new StringTextComponent("..."), false)` |
| 1.17–1.19.4 | Forge | `src.sendSuccess(Component.literal("..."), false)` |
| 1.20+ | Forge/NeoForge | `src.sendSuccess(() -> Component.literal("..."), false)` |
| 1.16.5–1.19.4 | Fabric | `src.sendFeedback(new LiteralText("...") / Text.literal("..."), false)` |
| 1.20+ | Fabric | `src.sendFeedback(() -> Text.literal("..."), false)` |

### Getting the server from a command source

| MC Version | Loader | Method |
|------------|--------|--------|
| 1.16.5 | Fabric | `src.getMinecraftServer()` |
| 1.17+ | Fabric | `src.getServer()` |
| All | Forge/NeoForge | `src.getServer()` |

### Getting a player from a command source

| MC Version | Loader | Method |
|------------|--------|--------|
| 1.16.5 | Forge | `(ServerPlayerEntity) src.getEntity()` (manual cast + null check) |
| 1.17+ | Forge/NeoForge | `src.getPlayerOrException()` |
| All | Fabric | `src.getPlayer()` |

---

## DIF Quick Search for Common Errors

| Error text | DIF entry to read |
|------------|------------------|
| `RegisterKeyMappingsEvent` not found (1.17) | `FORGE-117-NO-REGISTER-KEY-MAPPINGS-EVENT` |
| `net.minecraft.util.registry does not exist` | `FABRIC-119-REGISTRY-PATH-SPLIT` |
| `net.minecraft.registry does not exist` | `FABRIC-119-REGISTRY-PATH-SPLIT` |
| `String cannot be converted to Category` | `KEYMAPPING-CATEGORY-REQUIRED-1219` |
| `no suitable constructor found for KeyMapping` | `KEYMAPPING-CATEGORY-REQUIRED-1219` |
| `ModContainer` missing in NeoForge 1.21.9+ | `NEOFORGE-1219-MODCONTAINER-REQUIRED` |
| `event.world` package not found (1.18+) | `FORGE-17-EVENT-WORLD-VS-LEVEL` |
| `event.level` package not found (1.17) | `FORGE-17-EVENT-WORLD-VS-LEVEL` |
| `Could not find fabric-api:<version>` | `ANCHOR-ONLY-YARN-PITFALL` |
| `EventBus7` / `addListener` pattern (1.21.6+) | `FORGE-EB7-EVENTBUS7-PATTERN` |
| Fabric class name wrong (FarmBlock vs FarmlandBlock) | `FABRIC-YARN-VS-MOJANG-MAPPINGS` |
| HTTP 502/503 downloading Gradle | `GRADLE-TRANSIENT-NETWORK-FAILURE` |
| `package net.fabricmc.fabric.api.command.v2 does not exist` | `FABRIC-COMMAND-API-V1-VS-V2` |
| `package net.fabricmc.fabric.api.command.v1 does not exist` | `FABRIC-COMMAND-API-V1-VS-V2` |
| `UUID cannot be converted to boolean` (Fabric sendMessage) | `FABRIC-SEND-MESSAGE-SIGNATURE` |
| `UUID cannot be converted to ResourceKey` (Forge sendMessage) | `FORGE-SEND-MESSAGE-UUID-REMOVED` |
| `cannot find symbol: method asPlayer()` (Forge 1.16.5) | `FORGE-COMMAND-SOURCE-1165` |
| `cannot find symbol: method getServer()` (Fabric 1.16.5) | `FABRIC-GET-SERVER-VS-GET-MINECRAFT-SERVER` |
| `underscores in literals are not supported in -source 1.6` | `JAVA6-COMPAT-189-FORGE` |
| `diamond operator is not supported in -source 1.6` | `JAVA6-COMPAT-189-FORGE` |
| `lambda expressions are not supported in -source 1.6` | `JAVA6-COMPAT-189-FORGE` |
| `local variables referenced from a lambda expression must be final` | `LAMBDA-COUNT-CAPTURE` |
| `Unsupported version format '26.x'` | `VERSION-STRING-26X-ANCHOR` |
| `does not support exact Minecraft 1.20` (NeoForge) | `NEOFORGE-120-SUPPORTED-VERSIONS` |
| `does not support exact Minecraft 1.17` | `FORGE-117-TEMPLATE-SUPPORTS-1171-ONLY` |
| `package net.minecraft.item does not exist` (Forge 1.17+) | `FORGE-ITEM-PACKAGE-PRE-117` |
| `cannot find symbol.*MinecraftClient` (Fabric 1.21+) | `FABRIC-121-MOJANG-MAPPINGS-SWITCH` |
| `package net.minecraft.item does not exist` (Fabric 1.21+) | `FABRIC-121-MOJANG-MAPPINGS-SWITCH` |
| `cannot find symbol.*TickEvent` (NeoForge 1.21.2+) | `NEOFORGE-TICKEVENT-CLIENT-PACKAGE` |
| `cannot find symbol.*variable dist.*FMLEnvironment` | `NEOFORGE-FMLENVIRONMENT-GETDIST` |
| `package net.minecraftforge.fml.loading does not exist` (NeoForge 26.1) | `NEOFORGE-FMLENVIRONMENT-GETDIST` |
| `method interactItem.*required.*PlayerEntity.*World.*Hand` (Fabric 1.18) | `FABRIC-118-INTERACTITEM-WORLD-ARG` |
| `no suitable method found for addListener.*ClientTickEvent` (Forge 1.21.6+) | `FORGE-EB7-POST-BUS-ADDLISTENER` |
| `cannot find symbol.*class LivingUseTotemEvent` (Forge 1.19–1.19.2) | `FORGE-LIVINGUSETOTEM-NOT-IN-41X` |
| `cannot find symbol.*method getModEventBus.*FMLJavaModLoadingContext` | `FORGE-EB7-FMLCOMMONSETUPEVENT-GETBUS` |
| Port declared complete but user says versions are missing | `ALWAYS-CHECK-FULL-MANIFEST-NOT-JUST-PUBLISHED` |
| Generator produces `{MOD_ID}` or `{{` literally in Java output | `FORGE-FSTRING-ESCAPING-IN-GENERATOR` |
| Fabric 1.17 or 1.21 missing after publishing 1.17.1 / 1.21.1 | `FABRIC-MANIFEST-VERSION-VS-PUBLISHED-VERSION` |
| `cannot find symbol.*DrawScreenEvent` or `Render.Post` or `getPoseStack` | `FORGE-SCREEN-EVENT-RENDER-SUBCLASS-HISTORY` |
| Fabric TitleScreen mixin fails to inject or wrong render signature | `FABRIC-TITLESCREEN-MIXIN-RENDER-SIGNATURE-HISTORY` |
| `package net.minecraftforge.fml.common.gameevent does not exist` (1.16.5+) | `FORGE-TICKEVENT-PACKAGE-HISTORY` |
| `package net.minecraftforge.event.level does not exist` (1.18.x) | `FORGE-WORLDEVENT-VS-LEVELEVENT-BOUNDARY` |
| `package net.minecraftforge.event.world does not exist` (1.19+) | `FORGE-WORLDEVENT-VS-LEVELEVENT-BOUNDARY` |
| `incompatible types.*IWorld cannot be converted to.*World` | `FORGE-WORLDEVENT-GETWORLD-RETURNS-IWORLD` |
| `cannot find symbol.*getGameRules.*location.*Level` (1.21.3+) | `FORGE-LEVEL-GETGAMERULES-REMOVED-1213` |
| `cannot find symbol.*class GameRules.*net.minecraft.world.level` (1.21.9+) | `FORGE-GAMERULES-PACKAGE-MOVED-1219` |
| `package net.neoforged.neoforge.event.tick.ServerTickEvent does not exist` (1.20.x) | `NEOFORGE-SERVERTICK-NOT-IN-EARLY-20X` |
| `cannot find symbol.*class GameRules.*net.minecraft.world.level` (Fabric 26.1) | `FABRIC-26-GAMERULES-NEW-API` |
| Fabric 26.1.x server-side mod — is `ServerLifecycleEvents` still available? | `FABRIC-26-SERVER-LIFECYCLE-EVENTS-UNCHANGED` |
| `package net.neoforged.fml.javafmlmod does not exist` (NeoForge 26.1+) | `NEOFORGE-26-FMLJAVAMODLOADINGCONTEXT-REMOVED` |
| `cannot find symbol.*FMLJavaModLoadingContext` (NeoForge 26.1+) | `NEOFORGE-26-FMLJAVAMODLOADINGCONTEXT-REMOVED` |
| Prepare step fails with bad zip layout or 0 mod folders found | `ZIP-PATH-MUST-BE-RELATIVE-TO-BUNDLE-FOLDER` |

---

## Full Documentation Index

For deeper reading (in order of usefulness):

1. `docs/IDE_AGENT_INSTRUCTION_SHEET.txt` — complete operating manual
2. `dif/README.md` — DIF system overview and full entry list
3. `docs/examples/ACCOUNT_SWITCHER_ALL_VERSIONS.md` — client-side overlay mod, Fabric mixin + Forge ScreenEvent, all eras documented
4. `docs/examples/OPTIMIZED_VEIN_MINER_ALL_VERSIONS.md` — event-based client+server mod, all issues documented
5. `docs/examples/TPA_TELEPORT_ALL_VERSIONS.md` — server-side command mod, 7 runs, 68 versions
6. `docs/examples/SEED_PROTECT_ALL_VERSIONS.md` — mixin-based mod, yarn mapping pitfalls
7. `docs/examples/ALLOW_OFFLINE_LAN_JOIN_ALL_VERSIONS.md` — server-side reflection mod, 26.x patterns, 1-run success
8. `docs/examples/KEEP_INVENTORY_ALL_VERSIONS.md` — server-side gamerule mod, TickEvent/WorldEvent/GameRules API history across all eras
9. `docs/SYSTEM_MANUAL.md` — how the build pipeline works
10. `docs/MODRINTH_PUBLISHING_GUIDE.md` — publishing workflow

---

*Last updated: May 2026 — based on Keep Inventory all-versions port session*
