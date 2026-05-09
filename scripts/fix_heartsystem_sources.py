#!/usr/bin/env python3
"""
Fix Heart System mod source code for all version-specific API differences.
Extracts the zip, patches Java files per version, recreates the zip.
"""
import zipfile, os, shutil, tempfile, re, json, sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
INCOMING = REPO_ROOT / "incoming"
ZIP_PATH = INCOMING / "heartsystem-all-versions.zip"
BACKUP_PATH = INCOMING / "heartsystem-all-versions-backup.zip"
TEMP_DIR = Path(tempfile.mkdtemp(prefix="heartsystem-fix-"))

# ---- Version range detection helpers ----

def get_major_minor(version_str):
    """Return (major, minor) tuple for comparison."""
    parts = version_str.split(".")
    return (int(parts[0]), int(parts[1]))

def version_tuple(version_str):
    parts = version_str.split(".")
    return tuple(int(p) for p in parts)

def is_range(version_str, lo, hi):
    vt = version_tuple(version_str)
    return version_tuple(lo) <= vt <= version_tuple(hi)

# ---- Fix functions ----

def fix_forge_heartdata(content, loader, mc_version):
    """Fix HeartData.java for Forge/NeoForge versions."""
    vt = version_tuple(mc_version)
    
    # Forge 1.16.5 uses ModifiableAttributeInstance instead of AttributeInstance
    if mc_version == "1.16.5":
        content = content.replace(
            "import net.minecraft.entity.ai.attributes.AttributeInstance;",
            "import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;"
        )
        content = content.replace(
            "AttributeInstance attr",
            "ModifiableAttributeInstance attr"
        )
    
    # 1.20.5+: Operation.ADDITION -> Operation.ADD_VALUE
    if vt >= (1, 20, 5) and vt < (1, 21):
        content = content.replace(
            "AttributeModifier.Operation.ADDITION",
            "AttributeModifier.Operation.ADD_VALUE"
        )
    
    # 1.21+: ResourceLocation-based modifiers
    if vt >= (1, 21) and vt < (26, 1):
        # Check if already using ResourceLocation
        if "ResourceLocation" not in content:
            old_imports = "import java.util.UUID;"
            new_imports = """import net.minecraft.resources.ResourceLocation;
import java.util.UUID;"""
            content = content.replace(old_imports, new_imports)
            
            # Replace UUID field with ResourceLocation
            old_uuid_field = "private static final UUID MODIFIER_UUID = UUID.fromString(\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\");\n    private static final String MODIFIER_NAME = \"heartsystem.maxhealth\";"
            new_loc_field = "private static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(\"heartsystem\", \"maxhealth\");"
            content = content.replace(old_uuid_field, new_loc_field)
            
            # Fix getModifier check
            old_get = "if (attr.getModifier(MODIFIER_UUID) != null) {\n            attr.removeModifier(MODIFIER_UUID);\n        }"
            new_get = "attr.removeModifier(MODIFIER_ID);"
            content = content.replace(old_get, new_get)
            
            # Fix constructor
            old_ctor = "AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, AttributeModifier.Operation.ADDITION);"
            new_ctor = "AttributeModifier mod = new AttributeModifier(MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE);"
            content = content.replace(old_ctor, new_ctor)
    
    # 1.21.11+: Forge uses Identifier (not ResourceLocation) 
    if vt >= (1, 21, 11) or vt >= (26, 1):
        if "Identifier" not in content and "ResourceLocation" in content:
            content = content.replace("import net.minecraft.resources.ResourceLocation;", "import net.minecraft.resources.Identifier;")
            content = content.replace("ResourceLocation.fromNamespaceAndPath", "Identifier.fromNamespaceAndPath")
            content = content.replace("private static final ResourceLocation", "private static final Identifier")
        # Make sure ADD_VALUE is used
        content = content.replace(
            "AttributeModifier.Operation.ADDITION",
            "AttributeModifier.Operation.ADD_VALUE"
        )
    
    return content


def fix_fabric_heartdata(content, mc_version):
    """Fix HeartData.java for Fabric versions."""
    vt = version_tuple(mc_version)
    
    # 1.16.5-1.20.6: addModifier is private, use addPersistentModifier
    if vt < (1, 21):
        content = content.replace("addPermanentModifier", "addPersistentModifier")
        content = content.replace("attr.addModifier(mod)", "attr.addPersistentModifier(mod)")
    
    # 1.21+: check if using ResourceLocation-based system
    if vt >= (1, 21) and vt < (26, 1):
        if "ResourceLocation" not in content and "Identifier" not in content:
            old_imports = "import java.util.UUID;"
            new_imports = """import net.minecraft.resources.ResourceLocation;
import java.util.UUID;"""
            content = content.replace(old_imports, new_imports)
            
            # Replace UUID field
            old_uuid = "private static final UUID MODIFIER_UUID = UUID.fromString(\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\");"
            new_loc = "private static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(\"heartsystem\", \"maxhealth\");"
            content = content.replace(old_uuid, new_loc)
            
            # Remove name constant
            content = content.replace(
                "private static final String MODIFIER_NAME = \"heartsystem.maxhealth\";",
                ""
            )
            
            # Fix getter and constructor
            content = content.replace(
                "attr.getModifier(MODIFIER_UUID)",
                "attr.getModifier(MODIFIER_ID)"
            )
            content = content.replace("removeModifier(MODIFIER_UUID)", "removeModifier(MODIFIER_ID)")
            
            # Fix constructor with UUID, name -> ResourceLocation
            old_ctor = "new EntityAttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, EntityAttributeModifier.Operation.ADDITION)"
            new_ctor = "new EntityAttributeModifier(MODIFIER_ID, delta, EntityAttributeModifier.Operation.ADDITION)"
            content = content.replace(old_ctor, new_ctor)
            
            # Fix AttributeModifier constructor if already changed to non-Entity version
            old_ctor2 = "new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, AttributeModifier.Operation.ADDITION)"
            new_ctor2 = "new AttributeModifier(MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE)"
            content = content.replace(old_ctor2, new_ctor2)
    
    return content


def fix_neoforge_heart_storage(content, mc_version):
    """Fix HeartStorage.java for NeoForge versions."""
    vt = version_tuple(mc_version)
    
    # 1.20.4 neoforge: NbtIo.read/write need Path not File
    if mc_version == "1.20.4":
        content = content.replace("NbtIo.read(playerFile)", "NbtIo.read(playerFile.toPath())")
        content = content.replace("NbtIo.write(tag, playerFile)", "NbtIo.write(tag, playerFile.toPath())")
    
    # 1.21.x neoforge: similar issues
    if vt >= (1, 21) and vt < (26, 1):
        content = content.replace("NbtIo.read(playerFile)", "NbtIo.read(playerFile.toPath())")
        content = content.replace("NbtIo.write(tag, playerFile)", "NbtIo.write(tag, playerFile.toPath())")
    
    # 26.1.x neoforge: Already has correct pattern with .toPath()?
    if vt >= (26, 1):
        content = content.replace("NbtIo.read(playerFile)", "NbtIo.read(playerFile.toPath())")
        content = content.replace("NbtIo.write(tag, playerFile)", "NbtIo.write(tag, playerFile.toPath())")
    
    return content


def fix_forge_heart_event_handler(content, mc_version):
    """Fix HeartEventHandler.java for Forge versions."""
    vt = version_tuple(mc_version)
    
    # Forge 1.16.5: Uses ServerPlayerEntity, PlayerEntity, StringTextComponent
    if mc_version == "1.16.5":
        # The current source already uses correct 1.16.5 patterns
        # Just ensure event.getPlayer() not event.getEntity()
        content = content.replace("event.getEntity()", "event.getPlayer()")
        # Fix BannedPlayerList/BannedPlayerEntry (MCP 1.16.5 mappings)
        content = content.replace("import net.minecraft.server.players.UserBanList;", "import net.minecraft.server.management.BannedPlayerList;")
        content = content.replace("import net.minecraft.server.players.UserBanListEntry;", "import net.minecraft.server.management.BannedPlayerEntry;")
        content = content.replace("UserBanList banList", "BannedPlayerList banList")
        content = content.replace("UserBanListEntry", "BannedPlayerEntry")
    
    # Forge 1.17.1-1.18.x: broadcastMessage issue
    if vt >= (1, 17) and vt <= (1, 18):
        if "broadcastMessage" in content:
            # Replace broadcastMessage with forEach pattern
            old_broadcast = """                server.getPlayerList().broadcastMessage(
                    msg,
                    false
                );"""
            new_broadcast = """                server.getPlayerList().getPlayers().forEach(p -> p.sendMessage(msg, p.getUUID()));"""
            content = content.replace(old_broadcast, new_broadcast)
    
    # Forge 1.20.6: uses level field (not method) - should be fine
    # Forge 1.21+: level() is a method
    if vt >= (1, 21) and vt < (26, 1):
        if "player.level()" not in content:
            content = content.replace("player.level.isClientSide()", "player.level().isClientSide()")
            content = content.replace("newPlayer.level.isClientSide()", "newPlayer.level().isClientSide()")
            content = content.replace("event.getEntity().level.isClientSide()", "event.getEntity().level().isClientSide()")
    
    # Forge 26.1.2: NameAndId, server via level().getServer(), @SubscribeEvent removed
    if vt >= (26, 1):
        # The source already has the correct 26.1.2 pattern with NameAndId
        pass
    
    return content


def fix_fabric_player_death_mixin(content, mc_version):
    """Fix PlayerDeathMixin.java for Fabric versions."""
    vt = version_tuple(mc_version)
    
    # 1.16.5-1.18: broadcastChatMessage -> broadcast in 1.18
    if vt < (1, 19):
        if vt >= (1, 18):
            # 1.18 Fabric: broadcastChatMessage -> broadcast
            content = content.replace(
                "server.getPlayerManager().broadcastChatMessage(",
                "server.getPlayerManager().broadcast("
            )
        # Fix: self.world.isClient -> self.getWorld().isClient for 1.20.1+
        # Already correct for 1.16-1.19
    
    # 1.19-1.20.6: world field becomes private in 1.20.1+
    if vt >= (1, 19):
        if vt >= (1, 20, 1):
            content = content.replace("self.world.isClient", "self.getWorld().isClient")
            content = content.replace("deadPlayer.world.isClient", "deadPlayer.getWorld().isClient")
    
    # 1.21+: sendSystemMessage or sendMessage(Text)
    if vt >= (1, 21) and vt < (1, 21, 9):
        if "sendSystemMessage" not in content:
            # Replace sendMessage(msg, false) with sendSystemMessage(msg)
            content = content.replace("sendMessage(", "sendSystemMessage(")
            content = content.replace(", false)", "")
            # Also fix sendMessage(msg) without second arg
            content = content.replace("deadPlayer.sendSystemMessage(new LiteralText", "deadPlayer.sendSystemMessage(Component.literal")
            content = content.replace("killerPlayer.sendSystemMessage(new LiteralText", "killerPlayer.sendSystemMessage(Component.literal")
    
    # 1.21.9+: getServer() removed, NameAndId, Optional<Integer>
    if vt >= (1, 21, 9) or vt >= (26, 1):
        # The current zip may already have fixes
        # But verify: server via player.getServer() or player.level().getServer()?
        if "deadPlayer.level().getServer()" not in content and "deadPlayer.getServer()" in content:
            content = content.replace(
                "MinecraftServer server = deadPlayer.getServer();",
                "MinecraftServer server = deadPlayer.level().getServer();"
            )
        # GameProfile -> NameAndId
        if "NameAndId" not in content and "GameProfile" in content:
            # Match with or without trailing comma
            content = content.replace(
                "new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),",
                "new NameAndId(deadUUID, deadPlayer.getName().getString()),"
            )
            content = content.replace(
                "new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString())",
                "new NameAndId(deadUUID, deadPlayer.getName().getString())"
            )
            # Add NameAndId import after UserBanListEntry import
            if "import net.minecraft.server.players.NameAndId;" not in content:
                content = content.replace(
                    "import net.minecraft.server.players.UserBanListEntry;",
                    "import net.minecraft.server.players.UserBanListEntry;\nimport net.minecraft.server.players.NameAndId;"
                )
    
    return content


def fix_fabric_heart_storage(content, mc_version):
    """Fix HeartStorage.java for Fabric versions."""
    vt = version_tuple(mc_version)
    
    # 1.16.5-1.20.6: Use CompressedStreamTools for old versions
    if vt < (1, 18):
        # Use CompressedStreamTools for oldest fabric
        if "NbtIo.readCompressed" not in content and "CompressedStreamTools" not in content:
            content = content.replace("import net.minecraft.nbt.NbtIo;", "import net.minecraft.nbt.NbtIo;\nimport net.minecraft.nbt.CompressedStreamTools;")
            # Will fix actual calls below
        content = content.replace("NbtIo.readCompressed(file)", "CompressedStreamTools.read(file)")
        content = content.replace("NbtIo.writeCompressed(tag, file)", "CompressedStreamTools.write(tag, file)")
    
    # 1.18-1.20.x: readCompressed/writeCompressed
    if vt >= (1, 18) and vt < (1, 21):
        content = content.replace("NbtIo.readCompressed(file)", "NbtIo.readCompressed(file.toPath())")
        content = content.replace("NbtIo.writeCompressed(tag, file)", "NbtIo.writeCompressed(tag, file.toPath())")
    
    # 1.21.9+: getServer() removed, Optional for getInt
    if vt >= (1, 21, 9) or vt >= (26, 1):
        if "player.getServer()" in content and "player.level().getServer()" not in content:
            content = content.replace("player.getServer()", "player.level().getServer()")
        
        # Optional<Integer> for getInt
        if ".orElse(-1)" not in content:
            content = content.replace(
                "int h = tag.getInt(\"hearts\");",
                "int h = tag.getInt(\"hearts\").orElse(-1);"
            )
    
    return content


def fix_forge_heart_storage(content, mc_version):
    """Fix HeartStorage.java for Forge versions."""
    vt = version_tuple(mc_version)
    
    # 1.16.5: Uses CompressedStreamTools
    if mc_version == "1.16.5":
        # The source should use CompressedStreamTools instead of NbtIo
        if "NbtIo" in content and "CompressedStreamTools" not in content:
            content = content.replace("import net.minecraft.nbt.NbtIo;", "import net.minecraft.nbt.CompressedStreamTools;")
            content = content.replace("NbtIo.read(playerFile)", "CompressedStreamTools.read(playerFile)")
            content = content.replace("NbtIo.write(tag, playerFile)", "CompressedStreamTools.write(tag, playerFile)")
    
    # 1.17+: Use NbtIo (already correct in current source)
    
    # 1.21.5+: Optional for getInt (in 1.21.2-1.21.8 range, tag.getInt returns Optional)
    if vt >= (1, 21, 5) and vt < (1, 21, 9):
        if ".orElse(-1)" not in content:
            content = content.replace(
                "int h = tag.getInt(\"hearts\");",
                "int h = tag.getInt(\"hearts\").orElse(-1);"
            )
    
    # 1.21.9+ and 26.1+: Already has Optional handling, .toPath() for NbtIo methods
    if vt >= (26, 1):
        if ".orElse(-1)" not in content:
            content = content.replace(
                "int h = tag.getInt(\"hearts\");",
                "int h = tag.getInt(\"hearts\").orElse(-1);"
            )
    
    return content


# ---- Main fix routine ----

def parse_version_txt(path):
    meta = {}
    with open(path) as f:
        for line in f:
            line = line.strip()
            if "=" in line:
                k, v = line.split("=", 1)
                meta[k.strip()] = v.strip()
    return meta


def main():
    print("=== Heart System Source Fix Script ===")
    print(f"Zip: {ZIP_PATH}")
    print(f"Temp dir: {TEMP_DIR}")
    print()
    
    if not ZIP_PATH.exists():
        print(f"ERROR: Zip not found: {ZIP_PATH}")
        return 1
    
    # Extract zip to temp directory
    print("Extracting zip...")
    extract_dir = TEMP_DIR / "extracted"
    with zipfile.ZipFile(ZIP_PATH, "r") as zf:
        zf.extractall(extract_dir)
    
    # Process each version folder
    version_folders = sorted([d for d in extract_dir.iterdir() if d.is_dir()])
    print(f"Found {len(version_folders)} version folders in zip")
    
    fixes_applied = 0
    errors = []
    
    for folder in version_folders:
        folder_name = folder.name
        src_java = folder / "src" / "main" / "java" / "asd" / "itamio" / "heartsystem"
        
        if not src_java.exists():
            continue
        
        # Read version.txt to determine mc version and loader
        version_txt = folder / "version.txt"
        if not version_txt.exists():
            print(f"  SKIP {folder_name}: no version.txt")
            continue
        
        meta = parse_version_txt(version_txt)
        mc_version = meta.get("minecraft_version", "")
        loader = meta.get("loader", "")
        
        if not mc_version or not loader:
            print(f"  SKIP {folder_name}: incomplete version info")
            continue
        
        # Process each Java file (include subdirectories like mixin/)
        for java_file in sorted(src_java.rglob("*.java")):
            fname = java_file.name
            content = java_file.read_text()
            original = content
            
            try:
                if fname == "HeartData.java":
                    if loader in ("forge", "neoforge"):
                        content = fix_forge_heartdata(content, loader, mc_version)
                    elif loader == "fabric":
                        content = fix_fabric_heartdata(content, mc_version)
                
                elif fname == "HeartEventHandler.java":
                    if loader in ("forge", "neoforge"):
                        content = fix_forge_heart_event_handler(content, mc_version)
                
                elif fname == "HeartStorage.java":
                    if loader == "forge":
                        content = fix_forge_heart_storage(content, mc_version)
                    elif loader == "neoforge":
                        content = fix_neoforge_heart_storage(content, mc_version)
                    elif loader == "fabric":
                        content = fix_fabric_heart_storage(content, mc_version)
                
                elif fname == "PlayerDeathMixin.java":
                    if loader == "fabric":
                        content = fix_fabric_player_death_mixin(content, mc_version)
            except Exception as e:
                errors.append(f"{folder_name}/{fname}: {e}")
                continue
            
            if content != original:
                java_file.write_text(content)
                fixes_applied += 1
                print(f"  FIXED {folder_name}/{fname}")
    
    print(f"\nTotal fixes applied: {fixes_applied}")
    if errors:
        print(f"Errors: {len(errors)}")
        for e in errors:
            print(f"  {e}")
    
    # Recreate the zip (overwrite original)
    print(f"\nRecreating zip: {ZIP_PATH}")
    
    # Write to temp zip first to avoid corruption
    tmp_zip = TEMP_DIR / "heartsystem-all-versions.zip"
    with zipfile.ZipFile(tmp_zip, "w", zipfile.ZIP_DEFLATED) as zf:
        for folder in version_folders:
            folder_name = folder.name
            for file_path in sorted(folder.rglob("*")):
                if file_path.is_file():
                    arcname = f"{folder_name}/{file_path.relative_to(folder)}"
                    zf.write(file_path, arcname)
    
    # Replace original
    shutil.move(tmp_zip, ZIP_PATH)
    zip_size = ZIP_PATH.stat().st_size
    print(f"Zip updated: {ZIP_PATH} ({zip_size} bytes)")
    
    # Cleanup
    shutil.rmtree(TEMP_DIR)
    print(f"Temp dir cleaned: {TEMP_DIR}")
    print("\nDone! Zip is ready for rebuild.")
    
    return 0 if not errors else 1

if __name__ == "__main__":
    exit(main())
