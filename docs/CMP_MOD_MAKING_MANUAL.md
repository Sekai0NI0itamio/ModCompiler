# Mod Making Manual — The Definitive Reference for CMP Mod Listings

> This manual is the authoritative guide for creating compelling, professional-quality Minecraft mod listings for Modrinth. It is designed so that an AI agent (or human author) can take any mod concept and produce a complete, polished `manifest.json` with a name, summary, description, and category selection that matches the standards of the most popular mods on Modrinth.

> **CRITICAL RULE: AI agents must NEVER generate, create, or synthesize any images (icons, screenshots, gallery images, or description images).** All visual assets must be created by the human user themselves. The AI should leave image fields empty and instruct the user to add their own images using the CMP app's Icon Editor or by uploading files. AI-generated images look generic, unprofessional, and undermine the mod's unique identity. Screenshots must show actual in-game content captured by the user.

---

## Table of Contents

1. [Universal Rules](#1-universal-rules)
2. [Category-Specific Manuals](#2-category-specific-manuals)
3. [Fill-in-the-Blank Templates](#3-fill-in-the-blank-templates)
4. [Real Examples (Anonymized Patterns)](#4-real-examples-anonymized-patterns)

---

## 1. Universal Rules

These rules apply to **every** mod listing regardless of category. Violating them will make the listing look amateurish or reduce its discoverability.

### 1.1 Name Conventions

| Rule | Guideline | Rationale |
|------|-----------|-----------|
| Length | 1–2 words, 3 maximum | Short names are memorable, fit in UI elements, and look professional in search results |
| Capitalization | Title Case for each word (e.g., "Farmer's Delight", not "farmer's delight") | Modrinth displays names as-is; Title Case reads as a proper product name |
| Punctuation | Apostrophes for possessives are fine (e.g., "YUNG's Better Dungeons"). Avoid hyphens, underscores, numbers, or special characters | Hyphens/underscores look like slugs, not names. Numbers suggest a sequel, not an original |
| Uniqueness | Search Modrinth before naming. Do not use a name that already exists | Duplicate names cause confusion and slug collisions |
| Avoid generic words | Do not name a mod "Better Minecraft" or "More Stuff" | Generic names are unmemorable and don't convey what the mod does |
| Avoid version numbers | Never include version numbers in the name (e.g., "Sodium 2.0") | Version belongs in `version_info.mod_version`, not the project name |
| Avoid loader names | Never include "Fabric", "Forge", or "NeoForge" in the name | Loader info belongs in `version_info.loaders` |

**Name → Slug conversion** (handled automatically by CMP):
```
slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
```

### 1.2 Summary Writing Rules

The summary is the single most important piece of text. It appears in search results, card previews, and is the first thing users read.

| Rule | Guideline |
|------|-----------|
| Length | 60–120 characters. Modrinth truncates at ~160 but the first 80 are what users actually scan |
| Tone | Factual and confident. No exclamation marks, no "please try", no "this mod adds" |
| Structure | Lead with the primary benefit or feature, then optionally add a qualifier |
| What to include | The core value proposition — what the mod does or enables |
| What to exclude | Version numbers, loader names, "for Minecraft", subjective claims ("the best", "amazing"), developer commentary |
| No markdown | Summaries are plain text. No bold, links, or formatting |
| No emoji | Never use emoji in summaries |

**Good summaries:**
- "General-purpose optimization mod that improves various systems" (Sodium-style)
- "Adds immersive underwater dungeons with unique loot and bosses" (Adventure-style)
- "A pipe-based item transport system with filtering and routing" (Technology-style)

**Bad summaries:**
- "This mod adds a lot of cool new features!!" (vague, exclamatory)
- "Fabric 1.20.1 mod for sorting your inventory" (loader/version in summary)
- "The best optimization mod ever made" (subjective, unverifiable)

### 1.3 Description Body Structure

The description body (`description.body`) is a Markdown document. Every description should follow this universal skeleton, with category-specific sections added as needed.

**Universal skeleton:**

```markdown
# [Mod Name]

[1–2 sentence hook that restates and expands on the summary]

## Features

- [Feature 1 — start with a verb]
- [Feature 2]
- [Feature 3]
- ...

{{image:0}}

## [Category-Specific Section]

[Details relevant to the category]

## Configuration

[How to configure the mod — config file path, in-game UI, commands]

## Compatibility

- Vanilla-compatible: [yes/no]
- Server-side only: [yes/no]
- Modpack permission: [granted/see license]
- Works with: [notable compatible mods]

## Credits

[Attribution if applicable]
```

**Formatting rules:**

| Rule | Guideline |
|------|-----------|
| Headers | Use `#` for the mod name, `##` for major sections, `###` for subsections |
| Lists | Use `-` for feature lists. Keep each item to one line |
| Images | Use `{{image:N}}` placeholders (CMP-specific). Never use `![alt](url)` for images that need uploading. **AI must NOT generate images — only the user creates and provides them** |
| Code | Use inline `` `code` `` for commands, file paths, and config keys |
| Bold | Use `**bold**` sparingly — only for key terms or warnings |
| Links | Use `[text](url)` for external links. Never link to local files |
| No HTML | Do not use raw HTML tags in the description body |
| Length | Aim for 200–800 words. Shorter for utility mods, longer for adventure/technology mods |

### 1.4 Universal Trust Signals

Include these in every description to build credibility:

| Signal | How to express it | Where to place it |
|--------|-------------------|-------------------|
| Vanilla-compatible | "Works seamlessly with vanilla Minecraft" or "Vanilla-compatible — no new blocks required on the client" | Compatibility section |
| Modpack permission | "Modpacks are welcome!" or "Feel free to include in modpacks" or reference the license | Compatibility section |
| Server/client clarity | State clearly whether it's client-side, server-side, or both | Compatibility section |
| Open source | The GitHub source link (auto-populated by CMP) serves as a trust signal | Links section (automatic) |
| Active maintenance | Keep the changelog updated; a recent changelog implies active development | `version_info.changelog` |

### 1.5 Image Policy — AI Must NOT Generate Images

This is a **non-negotiable rule** for all CMP bundle creation:

| Asset Type | Who Creates It | How It Gets Into the Bundle |
|------------|---------------|----------------------------|
| Project icon | **User only** | CMP app's Icon Editor (upload, crop, resize, save) or manual file copy |
| Gallery screenshots | **User only** | User captures in-game screenshots via "Launch Client" button, then uploads through CMP app |
| Description images | **User only** | User uploads through CMP app or copies files to `description_images/` |

**What the AI must do instead:**
1. Leave `"icon": ""` empty in the manifest
2. Leave `"gallery": []` empty in the manifest
3. Leave `"description.images": []` empty in the manifest
4. Include `{{image:N}}` placeholders in the description body where images *should* go
5. Tell the user: "Please add your own icon and screenshots using the CMP app. You can use the Compile & Launch Client workflow to test the mod in-game and capture screenshots."

**Why this rule exists:**
- AI-generated images look generic and unprofessional
- Mod icons must reflect the mod's unique visual identity — only the creator knows what looks right
- Screenshots must show actual in-game content, not AI fabrications
- Modrinth users can spot AI-generated images and it damages credibility
- The CMP app provides a built-in Icon Editor and Launch Client workflow specifically for users to create and add their own images

---

## 2. Category-Specific Manuals

Each category below provides naming patterns, summary templates, description structure, key selling points, and common mistakes. All examples are derived from patterns observed in the most popular mods on Modrinth.

---

### 2.1 Optimization

**What it is:** Mods that improve Minecraft's performance — FPS, chunk loading, memory usage, tick rate, or startup time.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Scientific/chemical | Single word from chemistry or physics | Sodium, Lithium, FerriteCore |
| Technical compound | [Technical term] + [Suffix like "Core", "Fix", "Tweaks"] | Starlight, ModernFix, Clumps, FerriteCore |
| Minimalist verb | Single verb implying improvement | Smooth Boot, Enhanced Block Entities |

**Name generator formula:** Pick a chemical element, physics concept, or single technical term. If taken, append "Core" or "Fix".

#### Summary Templates

1. `[Performance area] optimization mod that [specific improvement]`
   - Example: "Rendering optimization mod that dramatically improves frame rates"
   - Example: "Memory optimization mod that reduces RAM usage during chunk loading"
   - Example: "Lighting engine optimization mod that eliminates lag from light updates"

2. `Improves [system] by [method], resulting in [measurable benefit]`
   - Example: "Improves chunk loading by optimizing data structures, resulting in faster world generation"
   - Example: "Improves entity rendering by batching draw calls, resulting in higher FPS in crowded areas"
   - Example: "Improves startup time by deferring non-essential tasks, resulting in faster game launch"

3. `A [scope] optimization mod focusing on [specific area]`
   - Example: "A general-purpose optimization mod focusing on rendering and simulation"
   - Example: "A client-side optimization mod focusing on memory footprint reduction"
   - Example: "A server-side optimization mod focusing on tick scheduling efficiency"

#### Description Template

```markdown
# [Mod Name]

[One-sentence performance claim]. [Mod Name] optimizes [specific system] without changing vanilla behavior.

## What It Does

- [Specific optimization 1 — e.g., "Replaces the rendering engine with a more efficient pipeline"]
- [Specific optimization 2 — e.g., "Reduces memory allocations during chunk generation"]
- [Specific optimization 3 — e.g., "Caches frequently accessed data to avoid redundant computation"]

## Performance

| Metric | Before | After |
|--------|--------|-------|
| [FPS / RAM / Startup time] | [Baseline] | [Improved] |

{{image:0}}

## Compatibility

- **Vanilla-compatible**: Yes — no behavior changes, only performance improvements
- **Server-side**: [Client-only / Server-only / Both]
- **Modpacks**: Always welcome
- **Compatible with**: [List known compatible optimization mods, e.g., "Sodium, Lithium, Starlight"]
- **Incompatible with**: [List any known incompatibilities]

## FAQ

**Does this change vanilla behavior?**
No. [Mod Name] only improves performance; all game mechanics remain identical.

**Can I use this with [other optimization mod]?**
Yes, [Mod Name] is designed to be compatible with other optimization mods.

**Is this server-side or client-side?**
[Answer with specifics]
```

#### Key Selling Points

- Measurable performance improvement (FPS numbers, RAM reduction, startup time)
- Zero behavior changes — vanilla parity is critical for optimization mods
- Compatibility with other optimization mods (users run multiple)
- Open source (users want to verify no cheating or malware)

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| Claiming unrealistic FPS gains | Users will test and leave negative reviews | Use conservative, honest numbers or say "varies by system" |
| Not listing incompatibilities | Crashes on launch = instant uninstall | Test with popular modpacks and list results |
| Changing vanilla behavior silently | Optimization mods must be transparent | State explicitly "no behavior changes" |
| Naming with "Optimization" in the name | Sounds generic and forgettable | Use the scientific/chemical naming pattern |

---

### 2.2 Adventure

**What it is:** Mods that add new structures, dungeons, biomes, mobs, or exploration content.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Possessive/creator-branded | [Creator]'s [Adjective] [Noun] | YUNG's Better Dungeons, YUNG's Better Strongholds, Alex's Caves |
| Evocative noun phrase | [Atmospheric adjective] [Noun] | When Dungeons Arise, Repurposed Structures |
| Mythological/thematic | [Mythological or fantasy term] | Twilight Forest, The Aether, Underworld |

**Name generator formula:** Use "[Creator]'s [Adjective] [Noun]" for personal branding, or pick an evocative two-word phrase from mythology/fantasy.

#### Summary Templates

1. `Adds [feature type] with [distinctive quality], bringing [atmosphere] to your world`
   - Example: "Adds sprawling dungeon complexes with unique traps and loot, bringing danger and reward to your world"
   - Example: "Adds mysterious underwater temples with ancient guardians, bringing deep-sea exploration to your world"
   - Example: "Adds haunted ruins with spectral enemies and cursed treasure, bringing gothic atmosphere to your world"

2. `[Feature type] mod that introduces [content count] new [content type] to discover`
   - Example: "Structure mod that introduces 15 new dungeon variants to discover across all dimensions"
   - Example: "Exploration mod that introduces 8 new cave biomes to discover underground"
   - Example: "Adventure mod that introduces 20 new boss encounters to discover in generated structures"

3. `Explore [thematic content] — [feature highlight] and [feature highlight]`
   - Example: "Explore forgotten catacombs — procedural room generation and unique boss encounters"
   - Example: "Explore crystal caverns — new bioluminescent mobs and rare mineral deposits"
   - Example: "Explore sky islands — floating ruins with elytra challenges and wind-themed puzzles"

#### Description Template

```markdown
# [Mod Name]

[Atmospheric hook — 2–3 sentences setting the mood and describing the core experience]

## What You'll Find

{{image:0}}

### [Structure/Biome/Mob Type 1]

- [Description of content]
- [Unique mechanics or loot]
- [Where it generates]

### [Structure/Biome/Mob Type 2]

- [Description of content]
- [Unique mechanics or loot]
- [Where it generates]

## Exploration Hooks

- [Reason to explore 1 — e.g., "Rare enchanted items found only in the deepest chambers"]
- [Reason to explore 2 — e.g., "Hidden rooms with lore tablets that tell the story of the ruins"]
- [Reason to explore 3 — e.g., "Boss encounters with unique attack patterns and valuable drops"]

## Loot

[Description of the loot system — custom items, vanilla+ loot tables, etc.]

{{image:1}}

## Configuration

- [Config option 1 — e.g., "Structure spawn rate: adjustable from 1/1000 to 1/100 chunks"]
- [Config option 2 — e.g., "Loot table difficulty: normal or hard mode"]
- [Config option 3 — e.g., "Dimension allowlist: control which dimensions generate structures"]

## Compatibility

- **Vanilla-compatible**: Yes — structures generate naturally in existing worlds
- **Modpacks**: Welcome — configure spawn rates to fit your pack
- **Works with**: [Biome mods, other structure mods, etc.]
- **Note**: [Any important compatibility notes, e.g., "New chunks required for structures to generate"]
```

#### Key Selling Points

- Visual showcase — screenshots are the #1 conversion factor for adventure mods
- Exploration motivation — give players a reason to seek out the content
- Loot/reward system — players want to know what they'll gain
- Configurability — modpack authors need to control spawn rates and dimensions

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| No screenshots | Adventure mods live or die by visuals | User must capture at least 3–5 in-game screenshots (use Launch Client) and add to gallery/description |
| Vague feature descriptions | "Adds cool dungeons" tells the user nothing | Be specific: "Adds 12 dungeon variants with 4 unique boss types" |
| Not mentioning worldgen requirements | Users install in existing worlds and don't find anything | State clearly: "New chunks must be generated for structures to appear" |
| Overpowered loot | Breaks modpack balance and gets the mod banned from packs | Provide configurable loot tables and default to vanilla+ power levels |

---

### 2.3 Technology

**What it is:** Mods that add automation, processing, transport, or engineering systems.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Descriptive compound | [Creator's] [Adjective] [System] | Tom's Simple Storage, Mekanism, Thermal Expansion |
| Single evocative word | [Verb or noun implying creation/engineering] | Create, Immersive Engineering, Industrial Craft |
| Technical descriptor | [What it does] + [System/Network/Manager] | Applied Energistics, Refined Storage, Pipez |

**Name generator formula:** Use a single evocative word (verb/noun) for bold branding, or "[Creator]'s [Adjective] [System]" for descriptive clarity.

#### Summary Templates

1. `Adds [system type] for [purpose], enabling [automation capability]`
   - Example: "Adds a pipe-based transport system for items and fluids, enabling automated factory layouts"
   - Example: "Adds a programmable robot system for task automation, enabling hands-free resource processing"
   - Example: "Adds a wireless redstone network for signal transmission, enabling complex contraptions without wiring"

2. `A [scope] tech mod centered around [core mechanic]`
   - Example: "A mid-game tech mod centered around steam-powered machinery and automation"
   - Example: "A late-game tech mod centered around quantum processing and digital storage"
   - Example: "An early-game tech mod centered around simple mechanical devices and water power"

3. `[Core mechanic] — build [end result] with [key feature]`
   - Example: "Rotational power — build mechanical contraptions with kinetic energy and cogwheels"
   - Example: "Digital networks — build automated storage systems with crafting-on-demand"
   - Example: "Fluid processing — build chemical refineries with multi-stage reactions"

#### Description Template

```markdown
# [Mod Name]

[One-sentence description of the core mechanic]. [Mod Name] introduces [system] that lets you [end result].

## Core Mechanics

### [Mechanic 1 — e.g., "Rotational Power"]

[Explanation of how the mechanic works]

{{image:0}}

### [Mechanic 2 — e.g., "Processing Chains"]

[Explanation of multi-step processing]

## Machines & Devices

| Device | Function | Power Requirement |
|--------|----------|-------------------|
| [Device 1] | [What it does] | [Power/tier] |
| [Device 2] | [What it does] | [Power/tier] |
| [Device 3] | [What it does] | [Power/tier] |

## Crafting & Progression

[Describe the tech tree or progression path]

1. [Tier 1 — e.g., "Start with basic generators and simple machines"]
2. [Tier 2 — e.g., "Upgrade to alloy smelters and advanced processing"]
3. [Tier 3 — e.g., "Build automated assembly lines with robotic arms"]

{{image:1}}

## Automation

[Describe automation capabilities — what can be automated, how systems connect]

## Configuration

- [Config option 1]
- [Config option 2]

## Compatibility

- **Vanilla-compatible**: Yes
- **Modpacks**: Welcome
- **Works with**: [Power systems, item pipes, etc.]
- **API**: [If the mod provides an API for addon developers]
```

#### Key Selling Points

- Clear progression path — players want to know the journey from early to late game
- Automation showcase — show what's possible, not just individual machines
- Integration potential — tech mods are almost always used in modpacks, so API and compatibility matter
- Crafting recipes — players need to understand the entry point

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| No progression explanation | Players don't know where to start | Include a "Getting Started" or "Progression" section |
| Ignoring power system compatibility | Players run multiple tech mods | State which power systems are supported (RF, FE, etc.) |
| Overwhelming feature lists | A wall of 50 machines is unreadable | Group machines by tier or function with tables |
| No automation examples | Tech mods are about automation; showing only individual machines misses the point | Include at least one "build this system" example with a screenshot |

---

### 2.4 Magic

**What it is:** Mods that add spell systems, rituals, enchanted items, mystical biomes, or arcane mechanics.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Evocative/thematic | [Mystical adjective] + [Noun] | Aquamirae, Botania, Blood Magic |
| Charm/artifact name | [Name of a magical item or concept] | Charm of Undying, Enchanting Infuser, Apotheosis |
| Arcane compound | [Mystical prefix] + [Suffix] | Ars Nouveau, Arcanus, Witchery |

**Name generator formula:** Pick a word from Latin, mythology, or nature that evokes mystery. If it's too short, pair it with a thematic noun.

#### Summary Templates

1. `A [atmosphere] magic mod featuring [core mechanic] and [secondary feature]`
   - Example: "A nature-themed magic mod featuring mana-powered flowers and automated enchanting"
   - Example: "A dark magic mod featuring blood rituals and soul-binding enchantments"
   - Example: "A celestial magic mod featuring star-powered spells and constellation-based progression"

2. `Harness [power source] to [primary ability] — [feature] and [feature]`
   - Example: "Harness lunar energy to cast protective spells — moonfire wards and shadow stepping"
   - Example: "Harness life essence to brew powerful elixirs — regeneration tonics and strength flasks"
   - Example: "Harness ancient runes to enchant equipment beyond vanilla limits — runic shields and elemental imbuements"

3. `[Thematic description] with [mechanic] and [mechanic]`
   - Example: "Elemental spell crafting with rune inscription and mana channeling"
   - Example: "Druidic nature magic with shapeshifting and plant growth rituals"
   - Example: "Necromantic arts with undead summoning and soul harvesting"

#### Description Template

```markdown
# [Mod Name]

[Atmospheric opening — 2–3 sentences that set the mystical tone and introduce the core concept]

## The [Power Source]

[Explanation of the magic system's energy source — mana, souls, runes, etc.]

{{image:0}}

## Spells & Abilities

### [Spell Category 1 — e.g., "Elemental"]

| Spell | Effect | Cost |
|-------|--------|------|
| [Spell 1] | [What it does] | [Mana/material cost] |
| [Spell 2] | [What it does] | [Mana/material cost] |

### [Spell Category 2 — e.g., "Defensive"]

| Spell | Effect | Cost |
|-------|--------|------|
| [Spell 3] | [What it does] | [Mana/material cost] |
| [Spell 4] | [What it does] | [Mana/material cost] |

## Rituals & Crafting

[Describe the ritual system or spell crafting mechanics]

1. [Step 1 — e.g., "Gather reagents from mystical biomes"]
2. [Step 2 — e.g., "Inscribe runes on the casting altar"]
3. [Step 3 — e.g., "Channel mana to complete the ritual"]

{{image:1}}

## Items & Artifacts

- [Item 1 — e.g., "Staff of the Eclipse: Channels shadow energy for offensive spells"]
- [Item 2 — e.g., "Amulet of Warding: Provides passive resistance to magical damage"]
- [Item 3 — e.g., "Grimoire of Rites: Unlocks advanced rituals when held"]

## Configuration

- [Config option 1 — e.g., "Mana regeneration rate"]
- [Config option 2 — e.g., "Ritual difficulty scaling"]

## Compatibility

- **Vanilla-compatible**: Yes
- **Modpacks**: Welcome
- **Works with**: [Other magic mods, enchantment mods, etc.]
```

#### Key Selling Points

- Atmosphere and lore — magic mods sell on immersion, not just mechanics
- Spell/ability variety — players want many options, not just 3 spells
- Visual effects — magic needs to look magical; screenshots of spell effects are essential
- Progression system — unlocking more powerful magic over time keeps players engaged

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| No lore or atmosphere | Magic without mysticism is just a tech mod with particles | Write 2–3 sentences of flavor text for each major feature |
| Overpowered starting spells | No progression = no engagement | Gate powerful spells behind rituals or resource requirements |
| Ignoring enchantment compatibility | Conflicts with vanilla enchanting frustrates players | State how the magic system interacts with vanilla enchantments |
| No visual showcase | Spell effects are the primary appeal | Include screenshots of every major spell effect |

---

### 2.5 Utility

**What it is:** Mods that improve quality-of-life, add UI enhancements, or provide small but impactful gameplay improvements.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Literal/descriptive | [What it modifies] + [Improvement type] | Mouse Tweaks, Inventory Tweaks, CraftTweaker |
| Named after the feature | [The thing it adds/fixes] | Mod Menu, AppleSkin, Jade, WTHIT |
| Action phrase | [Verb] + [Target] | Sort It, Inventory Sorter, Item Scroller |

**Name generator formula:** Name it after the exact thing it does or the feature it adds. Utility mod names should be instantly understandable.

#### Summary Templates

1. `[Action verb] [target] — [specific improvement]`
   - Example: "Sorts inventory contents — click a button to auto-stack and arrange items"
   - Example: "Shows food saturation — displays hidden hunger mechanics on the HUD"
   - Example: "Tweaks mouse controls — drag items quickly with scroll-wheel shortcuts"

2. `Adds [feature] for [purpose]`
   - Example: "Adds an in-game mod list for checking installed mods and their versions"
   - Example: "Adds tooltip enhancements for viewing item durability and enchantment details"
   - Example: "Adds a minimap for navigation with waypoint support and entity radar"

3. `[Problem it solves] by [method]`
   - Example: "Fixes inventory clutter by auto-merging stacks on container close"
   - Example: "Eliminates recipe confusion by showing all crafting uses for any item"
   - Example: "Reduces accidental item drops by requiring shift-click to throw"

#### Description Template

```markdown
# [Mod Name]

[One-sentence description of what it does and why it's useful]. [Optional: why existing solutions are insufficient].

## Features

- [Feature 1 — e.g., "Middle-click to sort inventory by item type"]
- [Feature 2 — e.g., "Shift-click to move all matching items between containers"]
- [Feature 3 — e.g., "Scroll-wheel to quickly move items one at a time"]
- [Feature 4 — e.g., "Configurable sort rules: by name, by item type, by mod"]

{{image:0}}

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| [Option 1] | [Default value] | [What it controls] |
| [Option 2] | [Default value] | [What it controls] |
| [Option 3] | [Default value] | [What it controls] |

## Commands

- `/[command1]` — [What it does]
- `/[command2]` — [What it does]

## Compatibility

- **Client-side only**: [Yes/No]
- **Server-side**: [Required/Optional/Not needed]
- **Modpacks**: Always welcome
- **Works with**: [Notable compatible mods]
```

#### Key Selling Points

- Immediate, tangible benefit — utility mods should solve a problem the user has right now
- Lightweight — utility mods should be small, fast, and conflict-free
- Configurable — different players want different behaviors
- Client-side only — if possible, this is a huge advantage (works on any server)

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| Over-explaining simple features | A 500-word description for a sorting mod is excessive | Keep it concise: features list + config table + compatibility |
| Not stating client/server side | Users need to know if it works on servers without installation | Always explicitly state client/server requirements |
| Changing default behavior without config | Users expect vanilla behavior by default | Make improvements opt-in via config, or clearly document changes |
| Feature creep | Adding too many features makes the mod bloated and buggy | Stay focused on the core utility; split into separate mods if needed |

---

### 2.6 Food

**What it is:** Mods that add crops, cooking systems, meals, or food-related mechanics.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Thematic/cozy | [Theme] + [Delight/Farm/Kitchen] | Farmer's Delight, Brewer's Delight, Let's Do Vinery |
| Cozy compound | [Food/drink concept] + [Evocative suffix] | Spice of Life, Vanilla Food Pantry, Pam's HarvestCraft |
| "Let's Do" pattern | Let's Do [Theme] | Let's Do Vinery, Let's Do Bakery, Let's Do Farming |

**Name generator formula:** Use "[Theme]'s Delight" for the Farmer's Delight ecosystem, or "Let's Do [Theme]" for the Let's Do series. Otherwise, pick a cozy food-related compound.

#### Summary Templates

1. `A [atmosphere] food mod adding [content count] [content type] and [mechanic]`
   - Example: "A cozy cooking mod adding 40 new meals and a multi-step cooking station"
   - Example: "A rustic farming mod adding 25 new crops and seasonal growth mechanics"
   - Example: "A bakery-themed mod adding 30 baked goods and an oven crafting system"

2. `[Warm description] — grow [crop types] and cook [food types]`
   - Example: "Warm your home with fresh bread — grow wheat varieties and bake artisan loaves"
   - Example: "Sip and savor — grow grapes and ferment wines in oak barrels"
   - Example: "Farm to table — grow heritage vegetables and cook hearty stews"

3. `Adds [cooking mechanic] with [feature] and [feature]`
   - Example: "Adds a cooking pot system with recipe combinations and nourishment bonuses"
   - Example: "Adds a fermentation system with aging mechanics and brewable potions"
   - Example: "Adds a grilling system with temperature control and smoky flavor effects"

#### Description Template

```markdown
# [Mod Name]

[Cozy, warm opening — 2 sentences about the culinary experience the mod provides]

## Crops & Ingredients

| Crop | Season | Yield | Used In |
|------|--------|-------|---------|
| [Crop 1] | [Spring/Summer/etc.] | [Yield] | [Recipes] |
| [Crop 2] | [Season] | [Yield] | [Recipes] |

## Cooking Mechanics

### [Station 1 — e.g., "Cooking Pot"]

[How it works — input ingredients, output meal]

{{image:0}}

### [Station 2 — e.g., "Oven"]

[How it works]

## Meals & Effects

| Meal | Ingredients | Effect | Duration |
|------|-------------|--------|----------|
| [Meal 1] | [Ingredients] | [Effect] | [Duration] |
| [Meal 2] | [Ingredients] | [Effect] | [Duration] |

## Farming

[Describe any unique farming mechanics — crop growth, seasons, soil types]

{{image:1}}

## Configuration

- [Config option 1 — e.g., "Crop growth speed multiplier"]
- [Config option 2 — e.g., "Meal effect duration multiplier"]

## Compatibility

- **Vanilla-compatible**: Yes
- **Modpacks**: Welcome
- **Works with**: [Farmer's Delight, other food mods, etc.]
```

#### Key Selling Points

- Cozy atmosphere — food mods are about comfort and creativity
- Recipe variety — more recipes = more content = more downloads
- Visual appeal — food items and cooking stations should look appetizing in screenshots
- Compatibility with Farmer's Delight ecosystem — the largest food mod community

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| Overpowered food effects | A steak that gives Resistance V for 10 minutes breaks game balance | Keep effects modest; use vanilla food effect power levels as reference |
| No cooking mechanic | Just adding items with no crafting system is boring | Add at least one unique cooking station or mechanic |
| Ignoring Farmer's Delight compatibility | FD is the standard for food mods; not working with it limits your audience | Test with FD and mention compatibility |
| No screenshots of food items | Food mods are visual — users want to see the meals | User must capture close-up screenshots of crafted meals and cooking stations |

---

### 2.7 Decoration

**What it is:** Mods that add decorative blocks, furniture, building materials, or visual enhancements.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Descriptive compound | [Creator's] [Block type] | Macaw's Bridges, Macaw's Doors, Macaw's Fences |
| Supplementary | [Word implying addition] | Supplementaries, Adorn, Decorative Blocks |
| Thematic descriptor | [Style/theme] + [Blocks/Decor] | Chipped, Create Deco, Stonezone |

**Name generator formula:** Use "[Creator]'s [Block Type]" for series branding, or a single word implying addition/supplement.

#### Summary Templates

1. `Adds [content count] new [block type] for [building style]`
   - Example: "Adds 80 new decorative blocks for medieval and rustic building styles"
   - Example: "Adds 50 new furniture pieces for interior decoration and home design"
   - Example: "Adds 30 new door and gate variants for architectural variety"

2. `[Visual description] — [block categories] and [block categories]`
   - Example: "Expand your building palette — new stone variants and wooden furniture"
   - Example: "Furnish your world — chairs, tables, shelves, and lamps in every wood type"
   - Example: "Bridge the gap — drawbridges, rope bridges, and covered walkways"

3. `A [scope] decoration mod with [feature] and [feature]`
   - Example: "A vanilla-style decoration mod with functional furniture and redstone-interactable blocks"
   - Example: "A medieval decoration mod with weapon racks and candle holders"
   - Example: "A modern decoration mod with kitchen appliances and electronic displays"

#### Description Template

```markdown
# [Mod Name]

[One-sentence description of the visual variety the mod adds]. [Optional: building style focus].

## Block Categories

### [Category 1 — e.g., "Furniture"]

{{image:0}}

- [Item 1]
- [Item 2]
- [Item 3]

### [Category 2 — e.g., "Lighting"]

- [Item 1]
- [Item 2]
- [Item 3]

### [Category 3 — e.g., "Outdoor"]

{{image:1}}

- [Item 1]
- [Item 2]
- [Item 3]

## Wood Type Support

[If applicable, list which wood types are supported for variants]

## Functional Blocks

[If any decorative blocks have functionality — e.g., chairs you can sit on, shelves that display items]

## Configuration

- [Config option 1 — e.g., "Enable/disable specific block categories"]
- [Config option 2 — e.g., "Block render distance for large models"]

## Compatibility

- **Vanilla-compatible**: Yes
- **Modpacks**: Welcome
- **Works with**: [Other decoration mods, building mods]
```

#### Key Selling Points

- Visual variety — screenshots showing many blocks in one scene are the most effective
- Wood type variants — supporting all vanilla (and modded) wood types is a huge plus
- Functional decoration — blocks that look good AND do something are the best
- Building style focus — players building in a specific style will seek out matching mods

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| No screenshots | Decoration mods are 100% visual — no screenshots = no downloads | User must capture at least 5 in-game screenshots showing blocks in built scenes |
| Listing every block individually | A wall of 200 block names is unreadable | Group by category and show representative examples |
| Non-vanilla art style | Blocks that clash with Minecraft's aesthetic get rejected | Stick to vanilla-consistent textures (16x, pixel art style) |
| No crafting recipes | Players need to know how to obtain the blocks | Include a "Getting Started" section or link to a wiki |

---

### 2.8 Storage

**What it is:** Mods that add storage systems, container enhancements, or inventory management.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Descriptive | [Creator's] [Storage type] | Tom's Simple Storage, Storage Drawers, Iron Chests |
| Problem-solution | [What it solves] | Shulker Box Tooltip, Inventory HUD+, Backpack |
| Technical compound | [System name] | Applied Energistics, Refined Storage, Ender Storage |

**Name generator formula:** Name it after the storage solution it provides. Use "[Creator]'s [Type]" for personal branding, or a compound describing the system.

#### Summary Templates

1. `Adds [storage type] with [key feature] for [use case]`
   - Example: "Adds a multiblock storage system with visual item displays for large-scale organization"
   - Example: "Adds a digital storage network with autocrafting for automated item management"
   - Example: "Adds a simple backpack system with upgradeable slots for exploration"

2. `[Problem it solves] — [how it solves it]`
   - Example: "Never lose items again — view shulker box contents without placing them down"
   - Example: "Chests too small — upgrade to iron, gold, and diamond chests with expanded capacity"
   - Example: "Too many chests — consolidate into a single network with searchable terminals"

3. `A [scope] storage mod with [feature] and [feature]`
   - Example: "A simple storage mod with drawer-based organization and compacting upgrades"
   - Example: "An advanced storage mod with autocrafting and ME network integration"
   - Example: "A portable storage mod with tiered backpacks and auto-pickup features"

#### Description Template

```markdown
# [Mod Name]

[One-sentence description of the storage problem it solves and how]. [Optional: scope — simple vs advanced].

## Storage Systems

### [System 1 — e.g., "Storage Drawers"]

[How it works — visual storage, compacting, etc.]

{{image:0}}

### [System 2 — e.g., "Digital Network"]

[How it works — terminals, autocrafting, etc.]

## Upgrade Paths

| Tier | Capacity | Crafting |
|------|----------|----------|
| [Tier 1] | [Capacity] | [Recipe] |
| [Tier 2] | [Capacity] | [Recipe] |
| [Tier 3] | [Capacity] | [Recipe] |

## Features

- [Feature 1 — e.g., "Search bar with fuzzy matching"]
- [Feature 2 — e.g., "Autocrafting with recursive recipe resolution"]
- [Feature 3 — e.g., "Visual item display on drawer fronts"]
- [Feature 4 — e.g., "Storage snapshot for backup/restore"]

{{image:1}}

## Configuration

- [Config option 1]
- [Config option 2]

## Compatibility

- **Vanilla-compatible**: Yes
- **Modpacks**: Welcome
- **Works with**: [Item pipes, autocrafting systems, etc.]
```

#### Key Selling Points

- Capacity numbers — players want to know exactly how much storage they're getting
- Upgrade progression — a clear path from early to late game storage
- Search and organization — the #1 reason people install storage mods
- Autocrafting — if present, it's the killer feature; highlight it

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| Not comparing to vanilla | "500 slots" means nothing without context | Compare to vanilla chest (27 slots) or double chest (54 slots) |
| No upgrade path | Players don't know where to start | Show a clear progression from basic to advanced storage |
| Ignoring item pipe compatibility | Storage mods are used with tech mods | Test with popular pipe/transport mods |
| Overcomplicating simple storage | A "simple" storage mod with 50 config options isn't simple | Match complexity to the mod's scope |

---

### 2.9 Economy

**What it is:** Mods that add currency, trading, shops, or economic systems.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Descriptive | [Currency/Trade concept] | CobbleDollars, Vault, Shop |
| Compound | [System type] + [Economy/Trade/Shop] | Trade Cycle, Market Craft, Economy Plus |

**Name generator formula:** Name it after the currency, the shop concept, or the economic system it implements.

#### Summary Templates

1. `Adds [economic system] with [features] for [use case]`
   - Example: "Adds a player-run economy with buy/sell shops and currency for multiplayer servers"
   - Example: "Adds a villager trading overhaul with dynamic pricing and supply/demand mechanics"
   - Example: "Adds a banking system with interest, loans, and player-to-player transfers"

2. `[Currency/trade feature] — [how it works]`
   - Example: "CobbleDollars — earn currency from mining and spend it at server shops"
   - Example: "Dynamic market — prices adjust based on player trading activity"
   - Example: "Player shops — set up your own store with custom pricing and stock limits"

3. `A [scope] economy mod featuring [core mechanic] and [secondary feature]`
   - Example: "A server economy mod featuring GUI-based shops and recipe-based pricing"
   - Example: "A lightweight economy mod featuring command-based transactions and balance tracking"
   - Example: "A full economy mod featuring auction houses, player shops, and admin controls"

#### Description Template

```markdown
# [Mod Name]

[One-sentence description of the economic system]. [Optional: what server type it's designed for].

## Currency

[How players earn and spend currency — starting balance, earning methods, etc.]

## Commands

- `/[command1]` — [What it does]
- `/[command2]` — [What it does]
- `/[command3]` — [What it does]

{{image:0}}

## Shop System

### Buying

[How the buy interface works — GUI, categories, pricing]

### Selling

[How selling works — sell hand, sell GUI, automatic pricing]

## Pricing Engine

[How prices are calculated — recipe-based, flat rate, dynamic, etc.]

- Base materials: [Price]
- Crafted items: [Price formula]
- Uncraftable items: [Price]

## Player-to-Player Trading

[If applicable — /pay, trade windows, etc.]

{{image:1}}

## Configuration

- [Config option 1 — e.g., "Starting balance"]
- [Config option 2 — e.g., "Buy/sell price multiplier"]
- [Config option 3 — e.g., "Enable/disable specific commands"]

## Compatibility

- **Server-side**: [Required/Optional]
- **Client-side**: [Required/Optional]
- **Modpacks**: Welcome
- **Works with**: [Other economy mods, permission mods, etc.]
```

#### Key Selling Points

- Clear pricing system — players and admins need to understand how prices work
- Admin controls — server admins need to configure and manage the economy
- GUI-based interaction — command-only economies are hard to use
- Multiplayer focus — economy mods are almost always for servers

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| No admin controls | Server admins can't manage the economy | Include admin commands for setting balances, prices, and permissions |
| Inflation-prone economy | Unlimited money generation breaks the system | Add money sinks or configurable earning limits |
| No GUI | Command-only trading is tedious | Provide GUI-based shop interfaces |
| Not stating server/client requirements | Players install client-side when it's server-only | Clearly state which side is required |

---

### 2.10 Worldgen

**What it is:** Mods that add biomes, terrain features, ore generation, or world-type customization.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Creator-branded | [Creator]'s [Adjective] [Feature] | YUNG's Better Dungeons, YUNG's Better Mineshafts |
| Descriptive compound | [What it generates] | Biomes O' Plenty, Terralith, Geophilic |
| Evocative name | [Atmospheric term] | Tectonic, Incendium, Nullscape |

**Name generator formula:** Use "[Creator]'s Better [Feature]" for the YUNG's series pattern, or an evocative single word related to earth/terrain/nature.

#### Summary Templates

1. `Adds [content count] new [biome/structure type] with [distinctive quality]`
   - Example: "Adds 80 new biomes with realistic terrain generation and custom vegetation"
   - Example: "Adds 15 new cave biomes with unique mineral deposits and ambient effects"
   - Example: "Adds 25 new structure variants with improved layout and loot distribution"

2. `[Generation description] — [feature] and [feature]`
   - Example: "Overhauled terrain generation — realistic mountains and deep ocean trenches"
   - Example: "Nether biome expansion — volcanic wastes and crystalline caverns"
   - Example: "End dimension overhaul — chorus forests and void islands"

3. `A [scope] worldgen mod that [transformation description]`
   - Example: "A comprehensive worldgen mod that transforms terrain into realistic geological formations"
   - Example: "A nether-focused worldgen mod that adds diverse biomes to the Nether dimension"
   - Example: "A cave-focused worldgen mod that generates sprawling underground ecosystems"

#### Description Template

```markdown
# [Mod Name]

[One-sentence description of the world transformation]. [Optional: design philosophy].

## Biomes

### [Category 1 — e.g., "Forest Biomes"]

| Biome | Climate | Features |
|-------|---------|----------|
| [Biome 1] | [Temperature] | [Key features] |
| [Biome 2] | [Temperature] | [Key features] |

{{image:0}}

### [Category 2 — e.g., "Mountain Biomes"]

| Biome | Climate | Features |
|-------|---------|----------|
| [Biome 3] | [Temperature] | [Key features] |
| [Biome 4] | [Temperature] | [Key features] |

## Terrain Generation

[Describe how terrain is different from vanilla — height, noise, features]

## Structures

[If the mod adds structures alongside biomes]

## Ore Distribution

[If the mod changes ore generation]

{{image:1}}

## Configuration

- [Config option 1 — e.g., "Biome spawn weight multiplier"]
- [Config option 2 — e.g., "Enable/disable specific biomes"]
- [Config option 3 — e.g., "Terrain height scale"]

## Compatibility

- **Vanilla-compatible**: [Yes/No — some worldgen mods require new worlds]
- **New world required**: [Yes/No]
- **Modpacks**: Welcome — configure biome weights for balance
- **Works with**: [Other worldgen mods, structure mods, etc.]
```

#### Key Selling Points

- Biome variety — the #1 reason people install worldgen mods
- Visual showcase — biome screenshots are the primary conversion factor
- New world requirement — be honest about whether existing worlds work
- Configurability — modpack authors need to control biome weights and generation

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| Not stating "new world required" | Players install in existing worlds and see no changes | State clearly in the summary AND description |
| No biome screenshots | Worldgen mods are visual — no screenshots = no downloads | User must capture at least 5 in-game biome screenshots |
| Overwhelming biome count | "Adds 200 biomes" sounds impressive but is unmanageable | Group biomes by category and highlight the best ones |
| Ignoring datapack compatibility | Many servers use datapacks for worldgen | Test with common datapacks and state compatibility |

---

### 2.11 Library

**What it is:** Mods that provide APIs, frameworks, or shared code for other mods to depend on.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Technical/acronym | [Abbreviation or technical term] | Fabric API, Geckolib, YACL, Cloth Config |
| [Framework] API | [Name] API | Architectury API, Forge API, Puzzles Lib |
| Descriptive technical | [What it provides] | AutoConfig, ModMenu (borderline), MixinSquared |

**Name generator formula:** Use an acronym or short technical term. Append "API" or "Lib" if the base name is too generic.

#### Summary Templates

1. `A [scope] library providing [capability] for mod developers`
   - Example: "A rendering library providing animated model support for mod developers"
   - Example: "A configuration library providing GUI-based config screens for mod developers"
   - Example: "A cross-loader library providing abstraction for mod developers targeting Fabric and Forge"

2. `[Framework name] — [what it enables]`
   - Example: "Geckolib — animated entity and armor models using GeckoFX format"
   - Example: "Architectury — write once, run on Fabric and Forge"
   - Example: "YACL — Yet Another Config Lib with dynamic GUI generation"

3. `Provides [technical capability] for [number] mods`
   - Example: "Provides keybinding API and input handling for 50+ mods"
   - Example: "Provides data-driven entity generation for 30+ mods"
   - Example: "Provides networking abstraction for cross-platform mod development"

#### Description Template

```markdown
# [Mod Name]

[One-sentence description of what the library provides]. This is a **library mod** — it does nothing on its own but is required by other mods.

## What This Provides

- [Capability 1 — e.g., "Animated entity rendering using GeckoFX model format"]
- [Capability 2 — e.g., "Armor model animation support"]
- [Capability 3 — e.g., "Block entity animation support"]

## For Developers

### Setup

```groovy
// build.gradle
dependencies {
    implementation "[dependency string]"
}
```

### Usage

[Code example showing how to use the library]

[Additional API documentation sections]

{{image:0}}

## Mods Using This Library

[If possible, list or link to mods that depend on this library]

## Compatibility

- **Required by**: [List of mods that depend on this]
- **Loaders**: [Fabric, Forge, NeoForge, etc.]
- **Minecraft versions**: [Supported versions]
```

#### Key Selling Points

- Developer documentation — the #1 thing developers look for
- Dependency list — showing which mods require it builds trust
- Cross-loader support — if applicable, this is a major selling point
- Stability — library mods must be stable; breaking changes affect all dependent mods

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| No developer documentation | Developers can't use the library without docs | Include setup instructions and API examples |
| Not explaining it's a library | Users install it expecting gameplay features | State clearly in the summary AND first line of description |
| Breaking API changes without notice | Breaks all dependent mods | Use semantic versioning and document breaking changes |
| No dependency listing | Users don't know why they need it | List or link to mods that depend on this library |

---

### 2.12 Equipment

**What it is:** Mods that add weapons, armor, tools, or combat mechanics.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Descriptive/thematic | [Combat/weapon concept] | Better Combat, Spartan Weaponry, Epic Fight |
| Material-based | [Material type] + [Equipment type] | Netherite Plus, Diamond Heart, Emerald Tools |
| Combat descriptor | [What it improves] | First Person Model, Weapon Swing, Combat Roll |

**Name generator formula:** Name it after the combat improvement or weapon system it adds. Use "[Adjective] [Weaponry/Combat/Equipment]" for broad scope.

#### Summary Templates

1. `Adds [equipment type] with [mechanic] and [mechanic]`
   - Example: "Adds 20 new weapon types with unique attack combos and reach mechanics"
   - Example: "Adds armor sets with set bonuses and passive abilities"
   - Example: "Adds tool tiers with special abilities and durability mechanics"

2. `[Combat improvement] — [feature] and [feature]`
   - Example: "Overhauled combat — weapon combos and dodge mechanics"
   - Example: "Expanded weaponry — spears, rapiers, and greatswords with unique attack patterns"
   - Example: "Tactical combat — parrying, shield bashing, and stamina management"

3. `A [scope] equipment mod featuring [core feature]`
   - Example: "A comprehensive weapon mod featuring 15 weapon types with distinct attack styles"
   - Example: "A combat overhaul mod featuring animation-driven attacks and combo chains"
   - Example: "A tool expansion mod featuring tiered upgrades beyond diamond"

#### Description Template

```markdown
# [Mod Name]

[One-sentence description of the equipment or combat system]. [Optional: design philosophy].

## Weapons

| Weapon | Damage | Speed | Special |
|--------|--------|-------|---------|
| [Weapon 1] | [Damage] | [Speed] | [Special ability] |
| [Weapon 2] | [Damage] | [Speed] | [Special ability] |

{{image:0}}

## Armor

| Set | Defense | Toughness | Set Bonus |
|-----|---------|-----------|-----------|
| [Set 1] | [Defense] | [Toughness] | [Bonus] |
| [Set 2] | [Defense] | [Toughness] | [Bonus] |

## Combat Mechanics

[Describe unique combat mechanics — combos, parrying, dodge rolls, etc.]

## Crafting

[How to craft the equipment — materials, stations, etc.]

{{image:1}}

## Configuration

- [Config option 1 — e.g., "Weapon damage multiplier"]
- [Config option 2 — e.g., "Enable/disable combo system"]

## Compatibility

- **Vanilla-compatible**: [Yes/No]
- **Modpacks**: Welcome
- **Works with**: [Other combat mods, armor mods, etc.]
```

#### Key Selling Points

- Stat transparency — players want to see exact damage/speed numbers
- Unique mechanics — just adding "more swords" isn't enough; show what's different
- Visual showcase — weapon models and combat animations sell the mod
- Balance — overpowered weapons get the mod banned from modpacks

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| No damage/speed stats | Players can't compare to vanilla weapons | Include a comparison table |
| Overpowered equipment | Diamond sword does 7 damage; your sword shouldn't do 50 | Keep stats within 1.5x–2x of vanilla equivalents |
| No crafting recipes | Players don't know how to obtain the equipment | Include recipes or link to a wiki |
| Ignoring Better Combat compatibility | Better Combat is the most popular combat mod | Test with it and state compatibility |

---

### 2.13 Transportation

**What it is:** Mods that add vehicles, movement systems, or travel mechanics.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Descriptive | [Vehicle/movement type] | Valkyrien Skies, Immersive Vehicles, Mekanism (has transport) |
| Action compound | [Movement concept] | Waystones, Telepass, Elytra Plus |
| Vehicle descriptor | [Vehicle type] + [System/Mod] | Ships Mod, Airplane, Train Mod |

**Name generator formula:** Name it after the vehicle type or movement system it adds.

#### Summary Templates

1. `Adds [vehicle/movement type] for [travel purpose]`
   - Example: "Adds flyable airships for long-distance travel and aerial combat"
   - Example: "Adds drivable cars and trucks for road-based transportation"
   - Example: "Adds a waypoint teleportation system for instant travel between discovered locations"

2. `[Movement description] — [feature] and [feature]`
   - Example: "Take to the skies — build and pilot custom airships with working cannons"
   - Example: "Hit the road — drive cars with realistic physics and fuel systems"
   - Example: "Rail network — build trains with passenger cars and cargo wagons"

3. `A [scope] transportation mod with [core feature]`
   - Example: "A vehicle mod with custom car construction and multiplayer passenger support"
   - Example: "A movement mod with grappling hooks and wall-running mechanics"
   - Example: "A teleportation mod with networked waystones and discovery requirements"

#### Description Template

```markdown
# [Mod Name]

[One-sentence description of the transportation system]. [Optional: what makes it unique].

## Vehicles / Movement Systems

### [System 1 — e.g., "Airships"]

[How it works — crafting, piloting, fuel, etc.]

{{image:0}}

### [System 2 — e.g., "Cars"]

[How it works]

## Controls

| Action | Key | Description |
|--------|-----|-------------|
| [Action 1] | [Key] | [What it does] |
| [Action 2] | [Key] | [What it does] |

## Fuel & Maintenance

[If applicable — fuel types, repair mechanics, etc.]

## Multiplayer

[If applicable — passenger support, vehicle sharing, etc.]

{{image:1}}

## Configuration

- [Config option 1 — e.g., "Vehicle speed multiplier"]
- [Config option 2 — e.g., "Fuel consumption rate"]

## Compatibility

- **Vanilla-compatible**: [Yes/No]
- **Server-side**: [Requirements]
- **Modpacks**: Welcome
- **Works with**: [Other transportation mods, road mods, etc.]
```

#### Key Selling Points

- Control scheme — players need to know how to operate vehicles before installing
- Multiplayer support — transportation mods are most fun with friends
- Fuel/maintenance — adds depth but must be configurable
- Visual showcase — vehicles in motion screenshots are the best marketing

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| Not listing controls | Players can't figure out how to steer | Include a controls table |
| No multiplayer support | Most players use transportation mods on servers | Test on dedicated servers and state compatibility |
| Overly complex crafting | A vehicle that requires 50 crafting steps is tedious | Provide a reasonable crafting progression |
| Physics bugs | Vehicles clipping through blocks or flying off are instant uninstall triggers | Test thoroughly and state known issues |

---

### 2.14 Social

**What it is:** Mods that add communication, emotes, chat features, or multiplayer interaction.

#### Name Patterns

| Pattern | Formula | Real Examples |
|---------|---------|---------------|
| Descriptive | [Communication feature] | Simple Voice Chat, Emotecraft, Plasmo Voice |
| Action compound | [Social action] | Chat Heads, No Chat Reports, Tab Skin |

**Name generator formula:** Name it after the communication feature it adds. Keep it literal and descriptive.

#### Summary Templates

1. `Adds [communication feature] with [key capability]`
   - Example: "Adds proximity voice chat with positional audio and channel support"
   - Example: "Adds player emotes with custom animation support and keybind triggers"
   - Example: "Adds chat enhancements with player head icons and message formatting"

2. `[Social feature] — [how it works]`
   - Example: "Voice chat — talk to nearby players with distance-based volume falloff"
   - Example: "Emote system — express yourself with 50+ animated emotes"
   - Example: "Chat overlay — see who's talking with a voice activity indicator"

3. `A [scope] social mod for [communication type]`
   - Example: "A voice chat mod for proximity-based communication on multiplayer servers"
   - Example: "An emote mod for player expression with custom animation import support"
   - Example: "A chat mod for enhanced multiplayer communication with formatting and icons"

#### Description Template

```markdown
# [Mod Name]

[One-sentence description of the social feature]. [Optional: server/client requirements].

## Features

- [Feature 1 — e.g., "Proximity-based voice chat with distance falloff"]
- [Feature 2 — e.g., "Group channels for private conversations"]
- [Feature 3 — e.g., "Push-to-talk and voice activation modes"]
- [Feature 4 — e.g., "Mute individual players or adjust their volume"]

{{image:0}}

## Setup

### Client

[Client-side installation and configuration steps]

### Server

[Server-side installation and configuration steps]

## Commands

- `/[command1]` — [What it does]
- `/[command2]` — [What it does]

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| [Option 1] | [Default] | [What it controls] |
| [Option 2] | [Default] | [What it controls] |

{{image:1}}

## Compatibility

- **Client-side**: [Required/Optional]
- **Server-side**: [Required/Optional]
- **Modpacks**: Welcome
- **Works with**: [Other social mods, server mods, etc.]
```

#### Key Selling Points

- Server compatibility — social mods MUST work on dedicated servers
- Easy setup — complex setup = low adoption
- Audio/video quality — for voice chat mods, quality is the #1 factor
- Privacy controls — mute, volume, and opt-out options are essential

#### Common Mistakes

| Mistake | Why it's bad | Fix |
|---------|-------------|-----|
| Not explaining server setup | Server admins can't install it properly | Include step-by-step server setup instructions |
| No privacy controls | Players will refuse to use it without mute/volume options | Include per-player volume, mute, and opt-out |
| Requiring both sides | A mod that requires ALL players to install it limits adoption | Support mixed-client servers where possible |
| No push-to-talk option | Open mic is unacceptable for most players | Always offer push-to-talk as an option |

---

## 3. Fill-in-the-Blank Templates

Ready-to-use templates for each category. Replace `[bracketed]` placeholders with your mod's specific content.

---

### 3.1 Optimization Template

**Name generator:** `[Chemical element or physics term]` → If taken, `[Term]Core` or `[Term]Fix`

**Summary templates:**

1. `[Performance area] optimization mod that [specific improvement]`
2. `Improves [system] by [method], resulting in [measurable benefit]`
3. `A [scope] optimization mod focusing on [specific area]`

**Body template:**

```markdown
# [MOD_NAME]

[Performance claim]. [MOD_NAME] optimizes [specific system] without changing vanilla behavior.

## What It Does

- [optimization_1]
- [optimization_2]
- [optimization_3]

## Performance

| Metric | Before | After |
|--------|--------|-------|
| [metric_1] | [baseline] | [improved] |

{{image:0}}

## Compatibility

- **Vanilla-compatible**: Yes — no behavior changes, only performance improvements
- **Server-side**: [client_only/server_only/both]
- **Modpacks**: Always welcome
- **Compatible with**: [compatible_mods]
- **Incompatible with**: [incompatible_mods_or_none]

## FAQ

**Does this change vanilla behavior?**
No. [MOD_NAME] only improves performance; all game mechanics remain identical.

**Can I use this with [other_optimization_mod]?**
[compatibility_answer]

**Is this server-side or client-side?**
[side_answer]
```

---

### 3.2 Adventure Template

**Name generator:** `[Creator]'s [Adjective] [Noun]` or `[Evocative Noun Phrase]`

**Summary templates:**

1. `Adds [feature_type] with [distinctive_quality], bringing [atmosphere] to your world`
2. `[Feature_type] mod that introduces [content_count] new [content_type] to discover`
3. `Explore [thematic_content] — [feature_highlight] and [feature_highlight]`

**Body template:**

```markdown
# [MOD_NAME]

[Atmospheric hook — 2–3 sentences].

## What You'll Find

{{image:0}}

### [structure_type_1]

- [description_1]
- [unique_mechanic_1]
- [generation_location_1]

### [structure_type_2]

- [description_2]
- [unique_mechanic_2]
- [generation_location_2]

## Exploration Hooks

- [reason_to_explore_1]
- [reason_to_explore_2]
- [reason_to_explore_3]

## Loot

[loot_system_description]

{{image:1}}

## Configuration

- [config_option_1]
- [config_option_2]
- [config_option_3]

## Compatibility

- **Vanilla-compatible**: Yes
- **Modpacks**: Welcome
- **Works with**: [compatible_mods]
- **Note**: [compatibility_note]
```

---

### 3.3 Technology Template

**Name generator:** `[Evocative verb/noun]` or `[Creator]'s [Adjective] [System]`

**Summary templates:**

1. `Adds [system_type] for [purpose], enabling [automation_capability]`
2. `A [scope] tech mod centered around [core_mechanic]`
3. `[Core_mechanic] — build [end_result] with [key_feature]`

**Body template:**

```markdown
# [MOD_NAME]

[One-sentence description]. [MOD_NAME] introduces [system] that lets you [end_result].

## Core Mechanics

### [mechanic_1]

[mechanic_1_explanation]

{{image:0}}

### [mechanic_2]

[mechanic_2_explanation]

## Machines & Devices

| Device | Function | Power Requirement |
|--------|----------|-------------------|
| [device_1] | [function_1] | [power_1] |
| [device_2] | [function_2] | [power_2] |
| [device_3] | [function_3] | [power_3] |

## Crafting & Progression

1. [tier_1_description]
2. [tier_2_description]
3. [tier_3_description]

{{image:1}}

## Automation

[automation_description]

## Configuration

- [config_option_1]
- [config_option_2]

## Compatibility

- **Vanilla-compatible**: Yes
- **Modpacks**: Welcome
- **Works with**: [compatible_mods]
- **API**: [api_availability]
```

---

### 3.4 Magic Template

**Name generator:** `[Latin/mythological term]` or `[Mystical adjective] + [Noun]`

**Summary templates:**

1. `A [atmosphere] magic mod featuring [core_mechanic] and [secondary_feature]`
2. `Harness [power_source] to [primary_ability] — [feature] and [feature]`
3. `[Thematic_description] with [mechanic] and [mechanic]`

**Body template:**

```markdown
# [MOD_NAME]

[Atmospheric opening — 2–3 sentences].

## The [power_source]

[power_source_explanation]

{{image:0}}

## Spells & Abilities

### [spell_category_1]

| Spell | Effect | Cost |
|-------|--------|------|
| [spell_1] | [effect_1] | [cost_1] |
| [spell_2] | [effect_2] | [cost_2] |

### [spell_category_2]

| Spell | Effect | Cost |
|-------|--------|------|
| [spell_3] | [effect_3] | [cost_3] |
| [spell_4] | [effect_4] | [cost_4] |

## Rituals & Crafting

1. [ritual_step_1]
2. [ritual_step_2]
3. [ritual_step_3]

{{image:1}}

## Items & Artifacts

- [artifact_1]: [description_1]
- [artifact_2]: [description_2]
- [artifact_3]: [description_3]

## Configuration

- [config_option_1]
- [config_option_2]

## Compatibility

- **Vanilla-compatible**: Yes
- **Modpacks**: Welcome
- **Works with**: [compatible_mods]
```

---

### 3.5 Utility Template

**Name generator:** `[What it modifies] + [Improvement type]` or `[The feature it adds]`

**Summary templates:**

1. `[Action_verb] [target] — [specific_improvement]`
2. `Adds [feature] for [purpose]`
3. `[Problem_it_solves] by [method]`

**Body template:**

```markdown
# [MOD_NAME]

[One-sentence description]. [Optional: why existing solutions are insufficient].

## Features

- [feature_1]
- [feature_2]
- [feature_3]
- [feature_4]

{{image:0}}

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| [option_1] | [default_1] | [description_1] |
| [option_2] | [default_2] | [description_2] |
| [option_3] | [default_3] | [description_3] |

## Commands

- `/[command_1]` — [command_description_1]
- `/[command_2]` — [command_description_2]

## Compatibility

- **Client-side only**: [yes/no]
- **Server-side**: [required/optional/not_needed]
- **Modpacks**: Always welcome
- **Works with**: [compatible_mods]
```

---

### 3.6 Food Template

**Name generator:** `[Theme]'s Delight` or `Let's Do [Theme]` or `[Cozy compound]`

**Summary templates:**

1. `A [atmosphere] food mod adding [content_count] [content_type] and [mechanic]`
2. `[Warm_description] — grow [crop_types] and cook [food_types]`
3. `Adds [cooking_mechanic] with [feature] and [feature]`

**Body template:**

```markdown
# [MOD_NAME]

[Cozy, warm opening — 2 sentences].

## Crops & Ingredients

| Crop | Season | Yield | Used In |
|------|--------|-------|---------|
| [crop_1] | [season_1] | [yield_1] | [recipes_1] |
| [crop_2] | [season_2] | [yield_2] | [recipes_2] |

## Cooking Mechanics

### [station_1]

[station_1_description]

{{image:0}}

### [station_2]

[station_2_description]

## Meals & Effects

| Meal | Ingredients | Effect | Duration |
|------|-------------|--------|----------|
| [meal_1] | [ingredients_1] | [effect_1] | [duration_1] |
| [meal_2] | [ingredients_2] | [effect_2] | [duration_2] |

## Farming

[farming_mechanics_description]

{{image:1}}

## Configuration

- [config_option_1]
- [config_option_2]

## Compatibility

- **Vanilla-compatible**: Yes
- **Modpacks**: Welcome
- **Works with**: [compatible_mods]
```

---

### 3.7 Decoration Template

**Name generator:** `[Creator]'s [Block_type]` or `[Supplementary word]`

**Summary templates:**

1. `Adds [content_count] new [block_type] for [building_style]`
2. `[Visual_description] — [block_categories] and [block_categories]`
3. `A [scope] decoration mod with [feature] and [feature]`

**Body template:**

```markdown
# [MOD_NAME]

[One-sentence description]. [Optional: building style focus].

## Block Categories

### [category_1]

{{image:0}}

- [item_1]
- [item_2]
- [item_3]

### [category_2]

- [item_4]
- [item_5]
- [item_6]

### [category_3]

{{image:1}}

- [item_7]
- [item_8]
- [item_9]

## Wood Type Support

[wood_type_description]

## Functional Blocks

[functional_blocks_description]

## Configuration

- [config_option_1]
- [config_option_2]

## Compatibility

- **Vanilla-compatible**: Yes
- **Modpacks**: Welcome
- **Works with**: [compatible_mods]
```

---

### 3.8 Storage Template

**Name generator:** `[Creator]'s [Storage_type]` or `[Problem-solution name]`

**Summary templates:**

1. `Adds [storage_type] with [key_feature] for [use_case]`
2. `[Problem_it_solves] — [how_it_solves_it]`
3. `A [scope] storage mod with [feature] and [feature]`

**Body template:**

```markdown
# [MOD_NAME]

[One-sentence description]. [Optional: scope].

## Storage Systems

### [system_1]

[system_1_description]

{{image:0}}

### [system_2]

[system_2_description]

## Upgrade Paths

| Tier | Capacity | Crafting |
|------|----------|----------|
| [tier_1] | [capacity_1] | [recipe_1] |
| [tier_2] | [capacity_2] | [recipe_2] |
| [tier_3] | [capacity_3] | [recipe_3] |

## Features

- [feature_1]
- [feature_2]
- [feature_3]
- [feature_4]

{{image:1}}

## Configuration

- [config_option_1]
- [config_option_2]

## Compatibility

- **Vanilla-compatible**: Yes
- **Modpacks**: Welcome
- **Works with**: [compatible_mods]
```

---

### 3.9 Economy Template

**Name generator:** `[Currency/trade concept]` or `[System type] + [Economy/Trade/Shop]`

**Summary templates:**

1. `Adds [economic_system] with [features] for [use_case]`
2. `[Currency/trade_feature] — [how_it_works]`
3. `A [scope] economy mod featuring [core_mechanic] and [secondary_feature]`

**Body template:**

```markdown
# [MOD_NAME]

[One-sentence description]. [Optional: server type].

## Currency

[currency_description]

## Commands

- `/[command_1]` — [command_description_1]
- `/[command_2]` — [command_description_2]
- `/[command_3]` — [command_description_3]

{{image:0}}

## Shop System

### Buying

[buying_description]

### Selling

[selling_description]

## Pricing Engine

[pricing_description]

- Base materials: [base_price]
- Crafted items: [crafted_formula]
- Uncraftable items: [uncraftable_price]

## Player-to-Player Trading

[trading_description]

{{image:1}}

## Configuration

- [config_option_1]
- [config_option_2]
- [config_option_3]

## Compatibility

- **Server-side**: [required/optional]
- **Client-side**: [required/optional]
- **Modpacks**: Welcome
- **Works with**: [compatible_mods]
```

---

### 3.10 Worldgen Template

**Name generator:** `[Creator]'s Better [Feature]` or `[Evocative earth/terrain term]`

**Summary templates:**

1. `Adds [content_count] new [biome/structure_type] with [distinctive_quality]`
2. `[Generation_description] — [feature] and [feature]`
3. `A [scope] worldgen mod that [transformation_description]`

**Body template:**

```markdown
# [MOD_NAME]

[One-sentence description]. [Optional: design philosophy].

## Biomes

### [category_1]

| Biome | Climate | Features |
|-------|---------|----------|
| [biome_1] | [climate_1] | [features_1] |
| [biome_2] | [climate_2] | [features_2] |

{{image:0}}

### [category_2]

| Biome | Climate | Features |
|-------|---------|----------|
| [biome_3] | [climate_3] | [features_3] |
| [biome_4] | [climate_4] | [features_4] |

## Terrain Generation

[terrain_description]

## Structures

[structures_description]

## Ore Distribution

[ore_description]

{{image:1}}

## Configuration

- [config_option_1]
- [config_option_2]
- [config_option_3]

## Compatibility

- **Vanilla-compatible**: [yes/no]
- **New world required**: [yes/no]
- **Modpacks**: Welcome
- **Works with**: [compatible_mods]
```

---

### 3.11 Library Template

**Name generator:** `[Acronym/technical term]` + optional `API` or `Lib`

**Summary templates:**

1. `A [scope] library providing [capability] for mod developers`
2. `[Framework_name] — [what_it_enables]`
3. `Provides [technical_capability] for [number] mods`

**Body template:**

```markdown
# [MOD_NAME]

[One-sentence description]. This is a **library mod** — it does nothing on its own but is required by other mods.

## What This Provides

- [capability_1]
- [capability_2]
- [capability_3]

## For Developers

### Setup

```groovy
// build.gradle
dependencies {
    implementation "[dependency_string]"
}
```

### Usage

[code_example]

[additional_api_sections]

{{image:0}}

## Mods Using This Library

[dependent_mods_list]

## Compatibility

- **Required by**: [dependent_mods]
- **Loaders**: [supported_loaders]
- **Minecraft versions**: [supported_versions]
```

---

### 3.12 Equipment Template

**Name generator:** `[Adjective] [Weaponry/Combat/Equipment]` or `[Material] + [Equipment type]`

**Summary templates:**

1. `Adds [equipment_type] with [mechanic] and [mechanic]`
2. `[Combat_improvement] — [feature] and [feature]`
3. `A [scope] equipment mod featuring [core_feature]`

**Body template:**

```markdown
# [MOD_NAME]

[One-sentence description]. [Optional: design philosophy].

## Weapons

| Weapon | Damage | Speed | Special |
|--------|--------|-------|---------|
| [weapon_1] | [damage_1] | [speed_1] | [special_1] |
| [weapon_2] | [damage_2] | [speed_2] | [special_2] |

{{image:0}}

## Armor

| Set | Defense | Toughness | Set Bonus |
|-----|---------|-----------|-----------|
| [set_1] | [defense_1] | [toughness_1] | [bonus_1] |
| [set_2] | [defense_2] | [toughness_2] | [bonus_2] |

## Combat Mechanics

[combat_mechanics_description]

## Crafting

[crafting_description]

{{image:1}}

## Configuration

- [config_option_1]
- [config_option_2]

## Compatibility

- **Vanilla-compatible**: [yes/no]
- **Modpacks**: Welcome
- **Works with**: [compatible_mods]
```

---

### 3.13 Transportation Template

**Name generator:** `[Vehicle/movement type]` or `[Movement concept]`

**Summary templates:**

1. `Adds [vehicle/movement_type] for [travel_purpose]`
2. `[Movement_description] — [feature] and [feature]`
3. `A [scope] transportation mod with [core_feature]`

**Body template:**

```markdown
# [MOD_NAME]

[One-sentence description]. [Optional: what makes it unique].

## Vehicles / Movement Systems

### [system_1]

[system_1_description]

{{image:0}}

### [system_2]

[system_2_description]

## Controls

| Action | Key | Description |
|--------|-----|-------------|
| [action_1] | [key_1] | [description_1] |
| [action_2] | [key_2] | [description_2] |

## Fuel & Maintenance

[fuel_description]

## Multiplayer

[multiplayer_description]

{{image:1}}

## Configuration

- [config_option_1]
- [config_option_2]

## Compatibility

- **Vanilla-compatible**: [yes/no]
- **Server-side**: [requirements]
- **Modpacks**: Welcome
- **Works with**: [compatible_mods]
```

---

### 3.14 Social Template

**Name generator:** `[Communication feature]` or `[Social action compound]`

**Summary templates:**

1. `Adds [communication_feature] with [key_capability]`
2. `[Social_feature] — [how_it_works]`
3. `A [scope] social mod for [communication_type]`

**Body template:**

```markdown
# [MOD_NAME]

[One-sentence description]. [Optional: server/client requirements].

## Features

- [feature_1]
- [feature_2]
- [feature_3]
- [feature_4]

{{image:0}}

## Setup

### Client

[client_setup_steps]

### Server

[server_setup_steps]

## Commands

- `/[command_1]` — [command_description_1]
- `/[command_2]` — [command_description_2]

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| [option_1] | [default_1] | [description_1] |
| [option_2] | [default_2] | [description_2] |

{{image:1}}

## Compatibility

- **Client-side**: [required/optional]
- **Server-side**: [required/optional]
- **Modpacks**: Welcome
- **Works with**: [compatible_mods]
```

---

## 4. Real Examples (Anonymized Patterns)

Five complete examples showing how to go from a mod concept to a finished CMP manifest. Each example includes the full `mod_info` and `description` fields as they would appear in `manifest.json`.

---

### 4.1 Optimization Mod — Chunk Loading Improver

**Concept:** A mod that improves chunk loading speed and reduces lag spikes when moving quickly through the world.

**Name selection:** Chemical naming pattern → "Neodymium" (a rare earth element used in high-performance magnets, evoking speed and efficiency). Slug: `neodymium`.

**Summary:** "Chunk loading optimization mod that reduces lag spikes during rapid movement and dimension changes"

**Complete manifest (mod_info + description):**

```json
{
  "cmp_version": 1,
  "mod_info": {
    "name": "Neodymium",
    "slug": "neodymium",
    "summary": "Chunk loading optimization mod that reduces lag spikes during rapid movement and dimension changes",
    "project_type": "mod",
    "categories": ["optimization"],
    "additional_categories": ["utility"],
    "license_id": "MIT",
    "license_url": "",
    "donation_urls": []
  },
  "version_info": {
    "mod_version": "1.0.0",
    "loaders": ["fabric"],
    "client_side": "required",
    "server_side": "optional",
    "minecraft_versions": ["1.20.1", "1.20.4", "1.21.1"],
    "version_type": "release",
    "changelog": "Initial release — chunk loading optimization for Fabric 1.20–1.21",
    "dependencies": [],
    "featured": true
  },
  "description": {
    "body": "# Neodymium\n\nChunk loading optimization mod that reduces lag spikes during rapid movement and dimension changes. Neodymium optimizes the chunk loading pipeline without changing vanilla behavior.\n\n## What It Does\n\n- Defers non-essential chunk processing to spread workload across multiple ticks\n- Pre-generates lighting data for chunks in the load queue before they reach the client\n- Caches chunk biome data to avoid redundant noise calculations during terrain generation\n- Optimizes dimension change transitions by pre-loading the target dimension's spawn chunks\n\n## Performance\n\n| Metric | Before | After |\n|--------|--------|-------|\n| Chunk load spike (ms) | 80–150 | 20–40 |\n| Dimension change freeze | 2–5s | 0.3–0.8s |\n| Elytra flight stutter | Frequent | Rare |\n\n{{image:0}}\n\n## Compatibility\n\n- **Vanilla-compatible**: Yes — no behavior changes, only performance improvements\n- **Server-side**: Optional — works on both client and server\n- **Modpacks**: Always welcome\n- **Compatible with**: Sodium, Lithium, FerriteCore, Starlight\n- **Incompatible with**: None known\n\n## FAQ\n\n**Does this change vanilla behavior?**\nNo. Neodymium only improves chunk loading performance; all game mechanics remain identical.\n\n**Can I use this with Sodium?**\nYes, Neodymium is fully compatible with Sodium and other optimization mods.\n\n**Is this server-side or client-side?**\nBoth. Client-side reduces lag spikes during movement; server-side reduces tick time for chunk generation. You can use either side independently.",
    "images": [
      { "index": 0, "file": "description_images/0.png", "caption": "Performance comparison graph showing chunk load times before and after Neodymium" }
    ]
  },
  "icon": "icon.png",
  "gallery": [
    { "index": 0, "file": "gallery/0.png", "featured": true, "title": "Chunk load comparison", "description": "Side-by-side comparison of chunk loading with and without Neodymium" }
  ],
  "files": {
    "jar": "jar/neodymium-1.0.0.jar",
    "source": "source/"
  },
  "links": {
    "issues_url": "",
    "source_url": "",
    "wiki_url": "",
    "discord_url": ""
  },
  "publishing": {
    "modrinth_project_id": "",
    "github_owner": "",
    "github_repo_name": "neodymium",
    "requested_status": ""
  }
}
```

---

### 4.2 Adventure Mod — Underwater Dungeons

**Concept:** A mod that adds procedurally generated underwater dungeon complexes with unique loot, traps, and boss encounters.

**Name selection:** Possessive/evocative pattern → "Abyssal Strongholds". Slug: `abyssal-strongholds`.

**Summary:** "Adds procedurally generated underwater dungeons with unique traps, loot, and boss encounters in ocean biomes"

**Complete manifest (mod_info + description):**

```json
{
  "cmp_version": 1,
  "mod_info": {
    "name": "Abyssal Strongholds",
    "slug": "abyssal-strongholds",
    "summary": "Adds procedurally generated underwater dungeons with unique traps, loot, and boss encounters in ocean biomes",
    "project_type": "mod",
    "categories": ["adventure"],
    "additional_categories": ["worldgen"],
    "license_id": "MIT",
    "license_url": "",
    "donation_urls": []
  },
  "version_info": {
    "mod_version": "1.0.0",
    "loaders": ["fabric", "forge"],
    "client_side": "required",
    "server_side": "required",
    "minecraft_versions": ["1.20.1", "1.20.4"],
    "version_type": "release",
    "changelog": "Initial release — 5 dungeon variants, 3 boss types, and 12 unique loot items",
    "dependencies": [],
    "featured": true
  },
  "description": {
    "body": "# Abyssal Strongholds\n\nDive into the depths and discover ancient underwater strongholds teeming with danger and treasure. Abyssal Strongholds adds procedurally generated dungeon complexes beneath the ocean floor, each with unique traps, guardians, and powerful bosses.\n\n## What You'll Find\n\n{{image:0}}\n\n### Coral Crypts\n- Small, entry-level dungeons found in warm ocean biomes\n- Coral-themed traps: poison dart dispensers and suffocating kelp chambers\n- Loot: Enchanted tridents and coral-encrusted armor\n- Depth: Y=30 to Y=50\n\n### Drowned Citadels\n- Medium dungeons found in deep ocean biomes\n- Water-flow puzzles and guardian spawner rooms\n- Loot: Heart of the Sea, conduit fragments, and drowned captain's logs\n- Depth: Y=20 to Y=40\n\n### Abyssal Vaults\n- Large, endgame dungeons found in deep frozen ocean biomes\n- Multi-floor layouts with locked doors requiring key fragments\n- Loot: Tidal Crown, Abyssal Trident, and Siren's Compass\n- Depth: Y=-20 to Y=30\n\n## Exploration Hooks\n\n- Rare enchanted items found only in the deepest chambers\n- Lore tablets scattered throughout dungeons that reveal the history of the drowned civilization\n- Boss encounters with unique attack patterns and valuable drops\n- Siren's Compass — a special item that points to the nearest undiscovered stronghold\n\n## Bosses\n\n| Boss | Location | Drops |\n|------|----------|-------|\n| Drowned King | Drowned Citadel throne room | Tidal Crown, Royal Trident Blueprint |\n| Abyssal Leviathan | Abyssal Vault final chamber | Leviathan Scale, Siren's Compass |\n| Coral Matriarch | Coral Crypt hidden room | Matriarch's Blessing, Living Coral Shard |\n\n{{image:1}}\n\n## Configuration\n\n- Structure spawn rate: adjustable from 1/500 to 1/5000 chunks\n- Boss difficulty: normal or hard mode\n- Dimension allowlist: overworld only by default, configurable\n- Loot table override: use custom loot tables via datapack\n\n## Compatibility\n\n- **Vanilla-compatible**: Yes — structures generate naturally in ocean biomes\n- **Modpacks**: Welcome — configure spawn rates to fit your pack\n- **Works with**: Aquamirae, OceanCraft, Biomes O' Plenty\n- **Note**: New ocean chunks must be generated for structures to appear",
    "images": [
      { "index": 0, "file": "description_images/0.png", "caption": "Exterior of a Drowned Citadel entrance on the ocean floor" },
      { "index": 1, "file": "description_images/1.png", "caption": "Boss fight against the Drowned King in the throne room" }
    ]
  },
  "icon": "icon.png",
  "gallery": [
    { "index": 0, "file": "gallery/0.png", "featured": true, "title": "Coral Crypt entrance", "description": "A small Coral Crypt dungeon entrance on the ocean floor" },
    { "index": 1, "file": "gallery/1.png", "featured": false, "title": "Abyssal Vault interior", "description": "Multi-floor layout of an Abyssal Vault" },
    { "index": 2, "file": "gallery/2.png", "featured": false, "title": "Boss encounter", "description": "The Drowned King boss fight" }
  ],
  "files": {
    "jar": "jar/abyssal-strongholds-1.0.0.jar",
    "source": "source/"
  },
  "links": {
    "issues_url": "",
    "source_url": "",
    "wiki_url": "",
    "discord_url": ""
  },
  "publishing": {
    "modrinth_project_id": "",
    "github_owner": "",
    "github_repo_name": "abyssal-strongholds",
    "requested_status": ""
  }
}
```

---

### 4.3 Technology Mod — Pipe Systems

**Concept:** A mod that adds configurable pipe systems for item and fluid transport with filtering and routing.

**Name selection:** Descriptive compound → "Conduit". Slug: `conduit`.

**Summary:** "Adds configurable pipe systems for item and fluid transport with filtering, routing, and speed upgrades"

**Complete manifest (mod_info + description):**

```json
{
  "cmp_version": 1,
  "mod_info": {
    "name": "Conduit",
    "slug": "conduit",
    "summary": "Adds configurable pipe systems for item and fluid transport with filtering, routing, and speed upgrades",
    "project_type": "mod",
    "categories": ["technology"],
    "additional_categories": ["storage", "utility"],
    "license_id": "MIT",
    "license_url": "",
    "donation_urls": []
  },
  "version_info": {
    "mod_version": "1.0.0",
    "loaders": ["fabric"],
    "client_side": "required",
    "server_side": "required",
    "minecraft_versions": ["1.20.1"],
    "version_type": "release",
    "changelog": "Initial release — item pipes, fluid pipes, filter modules, and router modules",
    "dependencies": [],
    "featured": true
  },
  "description": {
    "body": "# Conduit\n\nA pipe-based transport system for items and fluids. Conduit introduces configurable pipes with filtering and routing, enabling automated factory layouts without complex redstone.\n\n## Core Mechanics\n\n### Item Pipes\n\nTransport items between inventories with configurable extraction and insertion. Pipes visually show items flowing through them.\n\n{{image:0}}\n\n### Fluid Pipes\n\nTransport fluids between tanks and processors. Support for vanilla water, lava, and modded fluids. Pipes render with the fluid's color.\n\n## Pipe Types\n\n| Pipe | Speed | Capacity | Upgrade Slots |\n|------|-------|----------|---------------|\n| Basic Item Pipe | 1 item/tick | 8 items | 1 |\n| Reinforced Item Pipe | 4 items/tick | 32 items | 3 |\n| Basic Fluid Pipe | 50 mB/tick | 500 mB | 1 |\n| Reinforced Fluid Pipe | 200 mB/tick | 2000 mB | 3 |\n\n## Modules\n\n### Filter Module\n- Whitelist or blacklist specific items\n- Supports NBT matching for enchanted or renamed items\n- Stackable — add multiple filters to one pipe\n\n### Router Module\n- Route items to specific destinations by priority\n- Round-robin or nearest-first distribution\n- Prevents item overflow with back-pressure logic\n\n### Speed Module\n- Doubles pipe throughput per module\n- Stackable up to 4x base speed\n\n## Crafting & Progression\n\n1. **Early game**: Craft basic pipes from copper and glass — simple point-to-point item transport\n2. **Mid game**: Upgrade to reinforced pipes with filter modules — automated sorting systems\n3. **Late game**: Build router networks with speed modules — factory-scale automation\n\n{{image:1}}\n\n## Automation\n\nConduit pipes integrate with any inventory — chests, machines, hoppers, and modded storage. Connect pipes to any face of a block and configure extraction/insertion independently. Use router modules to build complex distribution networks without redstone.\n\n## Configuration\n\n- Pipe render distance: 32 blocks (adjustable)\n- Default extraction rate: configurable\n- Enable/disable fluid pipe interactions with cauldrons\n\n## Compatibility\n\n- **Vanilla-compatible**: Yes\n- **Modpacks**: Welcome\n- **Works with**: Any mod with standard inventory support\n- **API**: Available for custom pipe types and modules",
    "images": [
      { "index": 0, "file": "description_images/0.png", "caption": "Item pipes connecting chests to a furnace with filter modules" },
      { "index": 1, "file": "description_images/1.png", "caption": "Late-game factory layout using reinforced pipes and router modules" }
    ]
  },
  "icon": "icon.png",
  "gallery": [
    { "index": 0, "file": "gallery/0.png", "featured": true, "title": "Pipe network overview", "description": "A complete pipe network with item and fluid transport" },
    { "index": 1, "file": "gallery/1.png", "featured": false, "title": "Filter module GUI", "description": "Configuring a filter module with whitelist items" }
  ],
  "files": {
    "jar": "jar/conduit-1.0.0.jar",
    "source": "source/"
  },
  "links": {
    "issues_url": "",
    "source_url": "",
    "wiki_url": "",
    "discord_url": ""
  },
  "publishing": {
    "modrinth_project_id": "",
    "github_owner": "",
    "github_repo_name": "conduit",
    "requested_status": ""
  }
}
```

---

### 4.4 Magic Mod — Spell Crafting

**Concept:** A mod that adds a rune-based spell crafting system where players combine runes to create custom spells.

**Name selection:** Evocative/thematic → "Runecrafter". Slug: `runecrafter`.

**Summary:** "A rune-based spell crafting mod where players inscribe and combine runes to create custom magical abilities"

**Complete manifest (mod_info + description):**

```json
{
  "cmp_version": 1,
  "mod_info": {
    "name": "Runecrafter",
    "slug": "runecrafter",
    "summary": "A rune-based spell crafting mod where players inscribe and combine runes to create custom magical abilities",
    "project_type": "mod",
    "categories": ["magic"],
    "additional_categories": ["adventure"],
    "license_id": "MIT",
    "license_url": "",
    "donation_urls": []
  },
  "version_info": {
    "mod_version": "1.0.0",
    "loaders": ["fabric"],
    "client_side": "required",
    "server_side": "required",
    "minecraft_versions": ["1.20.1"],
    "version_type": "release",
    "changelog": "Initial release — 24 runes, 6 spell schools, and runic inscription system",
    "dependencies": [],
    "featured": true
  },
  "description": {
    "body": "# Runecrafter\n\nInscribe ancient runes upon stone tablets and combine them to forge spells of devastating power. Runecrafter adds a deep spell crafting system where every spell is a unique creation built from elemental, defensive, and utility runes.\n\n## The Runic Power\n\nRunecrafter uses a mana-based casting system. Mana regenerates slowly over time or can be restored by consuming mana crystals found in rune shrines scattered throughout the world.\n\n{{image:0}}\n\n## Spells & Abilities\n\n### Elemental School\n\n| Spell | Effect | Mana Cost |\n|-------|--------|-----------|\n| Fire Bolt | Launches a projectile that ignites targets | 15 |\n| Ice Shard | Fires a piercing shard that slows enemies | 20 |\n| Lightning Strike | Calls down lightning at the target location | 45 |\n| Earth Spike | Raises stone spikes from the ground | 25 |\n\n### Defensive School\n\n| Spell | Effect | Mana Cost |\n|-------|--------|-----------|\n| Runic Shield | Absorbs damage for 10 seconds | 30 |\n| Frost Ward | Creates an ice barrier that freezes attackers | 35 |\n| Stone Skin | Reduces physical damage by 40% for 20 seconds | 25 |\n\n### Utility School\n\n| Spell | Effect | Mana Cost |\n|-------|--------|-----------|\n| Blink | Teleport 10 blocks forward | 20 |\n| Feather Fall | Negate fall damage for 30 seconds | 10 |\n| Reveal | Highlights invisible entities and hidden passages | 15 |\n\n## Rituals & Crafting\n\n1. **Gather runes** — Find rune shrines in the world or craft basic runes at a runic altar\n2. **Inscribe spells** — Place runes on a spell tablet in the inscription GUI to combine them\n3. **Cast spells** — Bind completed spell tablets to your spell book and cast with keybinds\n\nRune combinations follow intuitive logic: Fire + Projectile = Fire Bolt, Ice + Area = Frost Ward, Earth + Self = Stone Skin. Experiment to discover all combinations.\n\n{{image:1}}\n\n## Items & Artifacts\n\n- **Spell Book**: Holds up to 9 bound spells; cycle with scroll wheel or number keys\n- **Runic Altar**: Crafting station for basic runes; requires lapis and experience to operate\n- **Mana Crystal**: Consumable that restores 50 mana instantly\n- **Blank Spell Tablet**: Base item for spell inscription; crafted from paper and gold nugget\n\n## Configuration\n\n- Mana regeneration rate: adjustable\n- Spell damage multiplier: adjustable\n- Rune shrine spawn rate: adjustable\n\n## Compatibility\n\n- **Vanilla-compatible**: Yes\n- **Modpacks**: Welcome\n- **Works with**: Better Combat, Enchanting Infuser, other magic mods\n- **Note**: Spell damage scales with enchanting level for integration with vanilla progression",
    "images": [
      { "index": 0, "file": "description_images/0.png", "caption": "Runic altar with runes being crafted" },
      { "index": 1, "file": "description_images/1.png", "caption": "Spell inscription GUI showing rune combination" }
    ]
  },
  "icon": "icon.png",
  "gallery": [
    { "index": 0, "file": "gallery/0.png", "featured": true, "title": "Spell casting", "description": "Casting Lightning Strike from the spell book" },
    { "index": 1, "file": "gallery/1.png", "featured": false, "title": "Rune shrine", "description": "A naturally generated rune shrine in a forest" },
    { "index": 2, "file": "gallery/2.png", "featured": false, "title": "Spell book GUI", "description": "The spell book interface with bound spells" }
  ],
  "files": {
    "jar": "jar/runecrafter-1.0.0.jar",
    "source": "source/"
  },
  "links": {
    "issues_url": "",
    "source_url": "",
    "wiki_url": "",
    "discord_url": ""
  },
  "publishing": {
    "modrinth_project_id": "",
    "github_owner": "",
    "github_repo_name": "runecrafter",
    "requested_status": ""
  }
}
```

---

### 4.5 Utility Mod — Inventory Sorting

**Concept:** A mod that adds inventory sorting with configurable sort rules and keybinds.

**Name selection:** Literal/descriptive → "Sortify". Slug: `sortify`.

**Summary:** "Sorts inventory and container contents with a single click — auto-stack, arrange, and filter items by type or name"

**Complete manifest (mod_info + description):**

```json
{
  "cmp_version": 1,
  "mod_info": {
    "name": "Sortify",
    "slug": "sortify",
    "summary": "Sorts inventory and container contents with a single click — auto-stack, arrange, and filter items by type or name",
    "project_type": "mod",
    "categories": ["utility"],
    "additional_categories": ["storage"],
    "license_id": "MIT",
    "license_url": "",
    "donation_urls": []
  },
  "version_info": {
    "mod_version": "1.0.0",
    "loaders": ["fabric"],
    "client_side": "required",
    "server_side": "optional",
    "minecraft_versions": ["1.20.1", "1.20.4", "1.21.1"],
    "version_type": "release",
    "changelog": "Initial release — inventory sorting, container sorting, and configurable sort rules",
    "dependencies": [],
    "featured": true
  },
  "description": {
    "body": "# Sortify\n\nSorts inventory and container contents with a single click. Sortify auto-stacks, arranges, and filters items by type or name — works in any inventory screen.\n\n## Features\n\n- **Sort button** — Click the sort button in any inventory screen to instantly organize items\n- **Auto-stack** — Merges partial stacks of the same item into full stacks\n- **Sort modes** — Sort by item name, item type, mod origin, or inventory layout\n- **Container support** — Works in chests, shulker boxes, backpacks, and any modded container\n- **Player inventory sort** — Sort your own inventory independently with a separate keybind\n- **Sort sound** — Subtle click feedback (configurable)\n\n{{image:0}}\n\n## Configuration\n\n| Option | Default | Description |\n|--------|---------|-------------|\n| Sort mode | By type | Sort order: by name, by type, by mod, or inventory layout |\n| Auto-stack | Enabled | Merge partial stacks before sorting |\n| Sort button position | Top-right | Position of the sort button in container GUIs |\n| Player sort keybind | Middle-click | Keybind to sort player inventory |\n| Sort sound | Enabled | Play a click sound when sorting |\n\n## Commands\n\n- `/sortify sort` — Sort the currently open container\n- `/sortify player` — Sort your player inventory\n- `/sortify config` — Open the configuration screen\n\n## Compatibility\n\n- **Client-side only**: Yes — works on any server without server-side installation\n- **Server-side**: Optional — enables server-side sort validation for anti-cheat servers\n- **Modpacks**: Always welcome\n- **Works with**: Shulker Box Tooltip, Inventory HUD+, any container mod",
    "images": [
      { "index": 0, "file": "description_images/0.png", "caption": "Before and after sorting a chest inventory" }
    ]
  },
  "icon": "icon.png",
  "gallery": [
    { "index": 0, "file": "gallery/0.png", "featured": true, "title": "Inventory sorting", "description": "Before and after comparison of inventory sorting" }
  ],
  "files": {
    "jar": "jar/sortify-1.0.0.jar",
    "source": "source/"
  },
  "links": {
    "issues_url": "",
    "source_url": "",
    "wiki_url": "",
    "discord_url": ""
  },
  "publishing": {
    "modrinth_project_id": "",
    "github_owner": "",
    "github_repo_name": "sortify",
    "requested_status": ""
  }
}
```

---

*End of Mod Making Manual. Use this reference to create compelling, professional-quality mod listings that match the standards of the most popular mods on Modrinth.*
