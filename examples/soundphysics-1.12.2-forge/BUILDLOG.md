# Sound Physics 1.12.2 Port - Build Log & Issue Tracker

## Overview
Port of Sound Physics Remastered (1.20.1) to Minecraft 1.12.2 Forge.
Original by Sonic Ether, vlad2305m. Ported by Itamio.

## Files Created
- `SoundPhysics.java` - Core OpenAL EFX engine (simplified from 703→~300 lines)
- `SoundPhysicsMod.java` - Main mod class with Forge events
- `SoundPhysicsConfig.java` - Properties-based configuration
- `ReverbParams.java` - 4 reverb slot presets
- `SoundEventHandler.java` - Forge event hooks for sound interception

## API Changes (1.20.1 → 1.12.2)

### Minecraft API
| 1.20.1 | 1.12.2 | Status |
|--------|--------|--------|
| `Minecraft.getInstance()` | `Minecraft.getMinecraft()` | ✓ Ported |
| `ClientLevel` | `WorldClient` | ✓ Ported |
| `LocalPlayer` | `EntityPlayerSP` | ✓ Ported |
| `SoundSource` | `SoundCategory` | ✓ Ported |
| `Mth` | `MathHelper` | ✓ Ported |
| `Vec3` | `Vec3d` | ✓ Ported |
| `BlockHitResult` | `RayTraceResult` | ✓ Ported |
| `HitResult.Type.MISS` | `RayTraceResult.Type.MISS` | ✓ Ported |
| `Direction` | `EnumFacing` | ✓ Ported |
| `ChatFormatting` | `TextFormatting` | Not used (debug removed) |
| `BlockPos.containing()` | `new BlockPos()` | ✓ Ported |
| `FluidTags.WATER` | N/A (removed) | Removed (simplified) |
| `IBlockState.isFaceSturdy()` | `IBlockState.isSideSolid()` | ✓ Ported |

### Sound System Hooking
| 1.20.1 | 1.12.2 | Status |
|--------|--------|--------|
| Mixin: `SoundSystemMixin` | `SoundSetupEvent` | ✓ Event-based |
| Mixin: `SourceMixin` | `PlaySoundEvent` + reflection | ✓ Reflection-based |
| Mixin: `LibraryMixin` | `SoundSetupEvent` | ✓ Event-based |

### Features Removed (incompatible with 1.12.2)
- Mixin system (10 mixin classes)
- ClonedClientLevel / Level cloning system (6 files)
- Simple Voice Chat integration (API didn't exist for 1.12.2)
- Cloth Config integration (GUI system different)
- Debug renderer (RaycastRenderer)
- Access wideners / Access transformers
- Multi-module build (common/fabric/forge)
- Block tag-based reflectivity/occlusion (tags work differently in 1.12)

### OpenAL Compatibility
- LWJGL 2 (1.12.2) vs LWJGL 3 (1.20.1): `org.lwjgl.openal.*` classes are compatible
- `ALC10`, `AL10`, `AL11`, `EXTEfx` - same API surface
- `alGenAuxiliaryEffectSlots`, `alGenEffects`, `alGenFilters` - all present in LWJGL 2
- `alSource3i` for auxiliary send filters - present in LWJGL 2
- `AL_EFFECT_EAXREVERB` - present in LWJGL 2

### Known Issues
1. **Source ID retrieval** - `PlaySoundEvent` doesn't directly expose the OpenAL source ID. Using reflection to access `SoundManager.playingSounds` → source object → int field. May fail on some Forge versions.
2. **Block reflectivity/occlusion** - Simplified from 3-tier lookup (block ID → tag → sound type → default) to simple registry name matching.
3. **No world cloning** - Uses `world.rayTraceBlocks()` directly, which may cause threading issues on busy servers. Acceptable for client-side-only mod.
4. **Reduced ray count** - Default ray count reduced from 32 to 16, bounces from 4 to 3 for 1.12.2 performance.
5. **No ambient sound pattern matching via ResourceLocation** - Using regex pattern match on sound name string.

### Compilation Issues & Fixes
1. TBD - First CI build pending
