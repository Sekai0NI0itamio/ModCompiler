"""
mod_wizard/prompt_composer.py — Build the AI mod-creation prompt.

Assembles a compact prompt that tells the AI exactly how to
create a 1.12.2 Forge mod, with a link to decompiled source for reference.
"""

import re

DECOMPILED_SOURCE_URL = (
    "https://github.com/Sekai0NI0itamio/ModCompiler/tree/main/"
    "DecompiledMinecraftSourceCode/1.12.2-forge"
)


_SYSTEM_PROMPT = r"""You are an expert Minecraft mod developer. Create a compilable Forge 1.12.2 mod.

## Project
Workspace: Mod Development/1.12.2-forge/
Forge 1.12.2-14.23.5.2847, ForgeGradle 2.3, MCP stable_39, Java 8.

## Rules
- Package: asd.itamio.<modid> | Author: "Itamio" | Main class: <Name>Mod
- Use TextComponentString + TextFormatting (NOT Component.literal)
- @Mod.EventHandler for lifecycle, @SubscribeEvent for event handlers
- Commands: extend CommandBase (getName/getUsage/execute/getRequiredPermissionLevel)
- Player: EntityPlayerMP | World: getEntityWorld()
- Messages: player.sendMessage(new TextComponentString(TextFormatting.GREEN + "text"))
- DO NOT output build.gradle or gradle files

## Decompiled Forge source (verify API signatures):
{DECOMPILED_SOURCE_URL}
Key packages: fml/common/event/ fml/common/registry/ event/world/ event/entity/ common/config/

## mcmod.info template:
```json
[{{"modid":"MODID","name":"Name","description":"Desc","version":"${{version}}","mcversion":"${{mcversion}}","authorList":["Itamio"],"url":"","updateUrl":"","credits":"","logoFile":"","screenshots":[],"dependencies":[]}}]
```
"""

_OUTPUT_FORMAT = r"""
## STRICT OUTPUT FORMAT — use this EXACTLY:

---FILE: src/main/java/asd/itamio/<modid>/<ClassName>.java---
```java
package asd.itamio.<modid>;
// complete Java code with ALL imports
```

---FILE: src/main/resources/mcmod.info---
```json
[ ... ]
```

---METADATA---
group: asd.itamio.<modid>
archivesBaseName: <Name>
---

---SUMMARY---
What was created or changed:
- (list each file and what it does)

How to test in-game:
- (step-by-step instructions to verify the mod works)

Suggested improvements:
- (ideas for next features or refinements)
---

RULES:
- File path MUST start with "src/main/"
- Use ---FILE: path--- as delimiter (3 dashes, FILE, colon, space, path, 3 dashes)
- Content inside ```java``` or ```json``` fences
- End with ---METADATA--- block (group + archivesBaseName)
- After ---METADATA---, include a ---SUMMARY--- block with what you created/changed, how to test it, and suggested next improvements
- NO build.gradle or gradle files
"""


def suggest_mod_id(name: str) -> str:
    mod_id = re.sub(r'[^a-zA-Z0-9\s]', '', name)
    mod_id = mod_id.lower().strip()
    return re.sub(r'\s+', '_', mod_id)


def build(name: str, description: str) -> str:
    mod_id = suggest_mod_id(name)
    class_hint = name.replace(" ", "").replace("'", "")

    return f"""{_SYSTEM_PROMPT}

## MOD SPECIFICATION

### Mod Name: {name}
### Target: Minecraft 1.12.2, Forge

### Description:
{description}

### Suggested identifiers:
- mod_id: {mod_id}
- group: asd.itamio.{mod_id}
- archivesBaseName: {name.replace(" ", "-")}
- main class: {class_hint}Mod
- author: Itamio

{_OUTPUT_FORMAT}

## CHECKLIST
1. Package "asd.itamio.{mod_id}" in every Java file?
2. @Mod annotation present with modid, name, version?
3. All imports included?
4. TextComponentString used (not Component.literal)?
5. ---FILE: format followed exactly?
6. ---METADATA--- block at the end?
7. ---SUMMARY--- block after ---METADATA--- with changes, testing steps, and next improvements?
8. Check decompiled source at {DECOMPILED_SOURCE_URL} for API signatures?

Generate the complete mod source code now:"""


def build_fix_prompt(
    original_name: str,
    original_description: str,
    build_log: str,
    error_summary: str,
) -> str:
    return f"""## FIX REQUEST — Build Errors

The mod "{original_name}" failed to compile. Fix the issues below.
Return ONLY the files that need changes (not unchanged files).

### Original description:
{original_description}

### Build errors:
{error_summary}

### Build log (last 8000 chars):
```
{build_log[-8000:]}
```

## Instructions
1. Analyze errors, fix only what's broken
2. Return ONLY changed files using ---FILE: path--- format
3. Include ---METADATA--- block with group + archivesBaseName
4. After ---METADATA---, include a ---SUMMARY--- block:
   - What was changed and why
   - How to test the fix in-game
   - Any further improvements suggested
5. Verify API signatures: {DECOMPILED_SOURCE_URL}

Return corrected files now:"""

def build_refinement_prompt(
    original_name: str,
    original_description: str,
    user_feedback: str,
) -> str:
    """Build a prompt asking the AI to refine the mod based on user feedback."""
    return f"""## REFINEMENT REQUEST

The mod "{original_name}" has been compiled and tested. The user wants changes.

### Original description:
{original_description}

### User feedback / requested changes:
{user_feedback}

## Instructions
1. Apply the changes described above
2. Return ALL files that need modification using ---FILE: path--- format
3. For files that do not change, do NOT re-output them
4. Include the ---METADATA--- block with group and archivesBaseName
5. After ---METADATA---, include a ---SUMMARY--- block:
   - What was changed or added and why
   - How to test the changes in-game
   - Any further improvements suggested
6. Return full, complete files with all imports
7. Verify API signatures: {DECOMPILED_SOURCE_URL}

Return modified files now:"""
