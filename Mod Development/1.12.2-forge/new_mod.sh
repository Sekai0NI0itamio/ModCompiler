#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODS_DIR="$SCRIPT_DIR/mods"

if [ "$#" -lt 2 ]; then
    echo "Usage: ./new_mod.sh <mod-name> <modid> [group] [version]"
    echo ""
    echo "  mod-name   Directory name in mods/ (kebab-case, e.g. my-cool-mod)"
    echo "  modid      Forge mod ID (lowercase, no spaces, e.g. mycoolmod)"
    echo "  group      Java package group (default: asd.itamio.<modid>)"
    echo "  version    Mod version (default: 1.0.0)"
    echo ""
    echo "Example:"
    echo "  ./new_mod.sh my-cool-mod mycoolmod"
    echo "  ./new_mod.sh super-pickaxe superpickaxe com.example.superpickaxe 1.0.0"
    exit 1
fi

MOD_SLUG="$1"
MODID="$2"
MOD_GROUP="${3:-asd.itamio.$MODID}"
MOD_VERSION="${4:-1.0.0}"
MOD_NAME=$(echo "$MOD_SLUG" | sed 's/-/-/g')

MOD_DIR="$MODS_DIR/$MOD_SLUG"

if [ -d "$MOD_DIR" ]; then
    echo "ERROR: Mod directory already exists: $MOD_DIR"
    exit 1
fi

echo "Creating new mod: $MOD_NAME ($MODID)"
echo "  Directory: $MOD_DIR"
echo "  Group:     $MOD_GROUP"
echo "  Version:   $MOD_VERSION"
echo ""

mkdir -p "$MOD_DIR/src/main/java/$(echo "$MOD_GROUP" | tr '.' '/')"
mkdir -p "$MOD_DIR/src/main/resources/assets/$MODID"

cat > "$MOD_DIR/mod.properties" << PROPS
modid=$MODID
name=$MOD_NAME
group=$MOD_GROUP
version=$MOD_VERSION
PROPS

PACKAGE_PATH="$(echo "$MOD_GROUP" | tr '.' '/')"
MAIN_CLASS_DIR="$MOD_DIR/src/main/java/$PACKAGE_PATH"

cat > "$MAIN_CLASS_DIR/Main.java" << JAVA
package $MOD_GROUP;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "$MODID", name = "$MOD_NAME", version = "$MOD_VERSION")
public class Main {

    @Mod.Instance("$MODID")
    public static Main instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
    }
}
JAVA

cat > "$MOD_DIR/src/main/resources/mcmod.info" << INFO
[
{
  "modid": "$MODID",
  "name": "$MOD_NAME",
  "description": "$MOD_NAME mod.",
  "version": "\${version}",
  "mcversion": "\${mcversion}",
  "url": "",
  "updateUrl": "",
  "authorList": ["Itamio"],
  "credits": "",
  "logoFile": "",
  "screenshots": [],
  "dependencies": []
}
]
INFO

cat > "$MOD_DIR/src/main/resources/pack.mcmeta" << META
{
  "pack": {
    "description": "$MOD_NAME Resources",
    "pack_format": 3
  }
}
META

echo "=========================================="
echo "  Mod Created Successfully!"
echo "=========================================="
echo ""
echo "  Directory: $MOD_DIR"
echo ""
echo "Files created:"
echo "  mod.properties"
echo "  src/main/java/$PACKAGE_PATH/Main.java"
echo "  src/main/resources/mcmod.info"
echo "  src/main/resources/pack.mcmeta"
echo "  src/main/resources/assets/$MODID/"
echo ""
echo "Next steps:"
echo "  1. Edit the mod code in src/main/java/$PACKAGE_PATH/"
echo "  2. Add assets (textures, models, etc.) to src/main/resources/assets/$MODID/"
echo "  3. Build: ./build_mod.sh $MOD_SLUG"
echo ""
