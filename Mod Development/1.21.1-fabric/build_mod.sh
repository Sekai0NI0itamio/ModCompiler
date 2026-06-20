#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENGINE_DIR="$SCRIPT_DIR/engine"
MODS_DIR="$SCRIPT_DIR/mods"
OUTPUT_DIR="$SCRIPT_DIR/output"

if [ -z "$1" ]; then
    echo "Available mods:"
    for dir in "$MODS_DIR"/*/; do
        if [ -f "$dir/mod.properties" ]; then
            basename "$dir"
        fi
    done
    exit 0
fi

MOD_NAME="$1"
MOD_DIR="$MODS_DIR/$MOD_NAME"

if [ ! -d "$MOD_DIR" ]; then
    echo "ERROR: Mod directory not found: $MOD_DIR"
    exit 1
fi

if [ ! -f "$MOD_DIR/mod.properties" ]; then
    echo "ERROR: mod.properties not found in $MOD_DIR"
    exit 1
fi

MODID=$(grep '^modid=' "$MOD_DIR/mod.properties" | cut -d'=' -f2)
MOD_DISPLAY=$(grep '^name=' "$MOD_DIR/mod.properties" | cut -d'=' -f2)
MOD_GROUP=$(grep '^group=' "$MOD_DIR/mod.properties" | cut -d'=' -f2)
MOD_VERSION=$(grep '^version=' "$MOD_DIR/mod.properties" | cut -d'=' -f2)

echo "Building: $MOD_DISPLAY ($MODID) v$MOD_VERSION"

SRC_LINK="$ENGINE_DIR/src"
if [ -L "$SRC_LINK" ] || [ -d "$SRC_LINK" ]; then
    rm -rf "$SRC_LINK"
fi

ln -s "$MOD_DIR/src" "$SRC_LINK"

sed -i.bak \
    -e "s/archives_base_name=.*/archives_base_name=$MOD_DISPLAY/" \
    -e "s/maven_group=.*/maven_group=$MOD_GROUP/" \
    -e "s/mod_version=.*/mod_version=$MOD_VERSION/" \
    "$ENGINE_DIR/gradle.properties"
rm -f "$ENGINE_DIR/gradle.properties.bak"

cd "$ENGINE_DIR"
./gradlew build 2>&1

JAR_FILE="$ENGINE_DIR/build/libs/$MOD_DISPLAY-$MOD_VERSION.jar"
if [ -f "$JAR_FILE" ]; then
    cp "$JAR_FILE" "$OUTPUT_DIR/"
    echo "SUCCESS: $OUTPUT_DIR/$MOD_DISPLAY-$MOD_VERSION.jar"
else
    echo "ERROR: JAR not found at expected path"
    ls -la "$ENGINE_DIR/build/libs/" 2>/dev/null || true
fi

rm -rf "$SRC_LINK"

sed -i.bak \
    -e "s/archives_base_name=.*/archives_base_name=STOP/" \
    -e "s/maven_group=.*/maven_group=com.itamio.stop/" \
    -e "s/mod_version=.*/mod_version=1.0.0/" \
    "$ENGINE_DIR/gradle.properties"
rm -f "$ENGINE_DIR/gradle.properties.bak"
