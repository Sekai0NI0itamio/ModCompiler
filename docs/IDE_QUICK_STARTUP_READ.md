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

## How a Typical "Update to All Versions" Session Goes

```
User: "Update this mod to all versions: https://modrinth.com/mod/my-mod"

Agent:
  1. Run fetch_modrinth_project.py → see what's already published
  2. Check scripts/ for existing generator → use it if found
  3. Search dif/ for known issues with this mod type
  4. Generate bundle with only missing targets
  5. git add + git commit + git push (one command)
  6. python3 scripts/run_build.py ... (one command, blocking)
  7. Read results → fix only failed targets
  8. Repeat steps 4-7 until all targets pass
  9. Verify final state on Modrinth
  10. Add DIF entries + example doc + update this file + commit
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

---

## Full Documentation Index

For deeper reading (in order of usefulness):

1. `docs/IDE_AGENT_INSTRUCTION_SHEET.txt` — complete operating manual
2. `dif/README.md` — DIF system overview and full entry list
3. `docs/examples/OPTIMIZED_VEIN_MINER_ALL_VERSIONS.md` — most recent port, all issues documented
4. `docs/examples/TPA_TELEPORT_ALL_VERSIONS.md` — server-side command mod, 7 runs, 68 versions
5. `docs/examples/SEED_PROTECT_ALL_VERSIONS.md` — mixin-based mod, yarn mapping pitfalls
6. `docs/SYSTEM_MANUAL.md` — how the build pipeline works
7. `docs/MODRINTH_PUBLISHING_GUIDE.md` — publishing workflow

---

*Last updated: April 2026 — based on TPA Teleport all-versions port session*
