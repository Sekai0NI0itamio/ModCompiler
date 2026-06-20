#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODS_DIR="$SCRIPT_DIR/mods"

if [ "$#" -lt 2 ]; then
    echo "Usage: ./new_mod.sh <mod-name> <modid> [group] [version]"
    echo ""
    echo "  mod-name   Directory name in mods/ (kebab-case, e.g. my-cool-mod)"
    echo "  modid      Forge mod ID (lowercase, no spaces, e.g. mycoolmod)"
    echo "  group      Java package group (default: com.itamio.<modid>)"
    echo "  version    Mod version (default: 1.0.0)"
    echo ""
    echo "Example:"
    echo "  ./new_mod.sh my-cool-mod mycoolmod"
    exit 1
fi

MOD_SLUG="$1"
MODID="$2"
MOD_GROUP="${3:-com.itamio.$MODID}"
MOD_VERSION="${4:-1.0.0}"
MOD_DISPLAY_NAME=$(echo "$MOD_SLUG" | sed 's/-/ /g' | sed 's/\b\(.\)/\u\1/g')

MOD_DIR="$MODS_DIR/$MOD_SLUG"

if [ -d "$MOD_DIR" ]; then
    echo "ERROR: Mod directory already exists: $MOD_DIR"
    exit 1
fi

echo "Creating new mod: $MOD_DISPLAY_NAME ($MODID)"
echo "  Directory: $MOD_DIR"
echo "  Group:     $MOD_GROUP"
echo "  Version:   $MOD_VERSION"
echo ""

mkdir -p "$MOD_DIR/src/main/java/$(echo "$MOD_GROUP" | tr '.' '/')"
mkdir -p "$MOD_DIR/src/main/resources/META-INF"
mkdir -p "$MOD_DIR/src/main/resources/data/$MODID"

cat > "$MOD_DIR/mod.properties" << PROPS
modid=$MODID
name=$MOD_DISPLAY_NAME
group=$MOD_GROUP
version=$MOD_VERSION
PROPS

PACKAGE_PATH="$(echo "$MOD_GROUP" | tr '.' '/')"
MAIN_CLASS_DIR="$MOD_DIR/src/main/java/$PACKAGE_PATH"

cat > "$MAIN_CLASS_DIR/Main.java" << JAVA
package $MOD_GROUP;

import net.minecraftforge.fml.common.Mod;

@Mod($MODID.MODID)
public class $MODID {
    public static final String MODID = "$MODID";

    public $MODID() {
    }
}
JAVA

cat > "$MOD_DIR/src/main/resources/META-INF/mods.toml" << TOML
modLoader="javafml"
loaderVersion="[47,)"
license="MIT"

[[mods]]
modId="$MODID"
version="\${file.jarVersion}"
displayName="$MOD_DISPLAY_NAME"
description='''
$MOD_DISPLAY_NAME mod.
'''

[[dependencies.$MODID]]
modId="forge"
mandatory=true
versionRange="[47,)"
ordering="NONE"
side="BOTH"

[[dependencies.$MODID]]
modId="minecraft"
mandatory=true
versionRange="[1.20.1,1.21)"
ordering="NONE"
side="BOTH"
TOML

cat > "$MOD_DIR/src/main/resources/pack.mcmeta" << META
{
  "pack": {
    "description": "$MOD_DISPLAY_NAME Resources",
    "pack_format": 15
  }
}
META

echo "=========================================="
echo "  Mod Created Successfully!"
echo "=========================================="
echo ""
echo "  Directory: $MOD_DIR"
echo ""
echo "Next steps:"
echo "  1. Edit the mod code in src/main/java/$PACKAGE_PATH/"
echo "  2. Build: ./build_mod.sh $MOD_SLUG"
