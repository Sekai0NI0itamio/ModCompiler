#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENGINE_DIR="$SCRIPT_DIR/engine"
MODS_DIR="$SCRIPT_DIR/mods"
OUTPUT_DIR="$SCRIPT_DIR/output"

JAVA8_HOME=$(/usr/libexec/java_home -v 1.8 2>/dev/null || echo "")
if [ -n "$JAVA8_HOME" ]; then
    export JAVA_HOME="$JAVA8_HOME"
fi

if [ "$#" -lt 1 ]; then
    echo "Usage: ./build_mod.sh <mod-name> [clean]"
    echo ""
    echo "Available mods:"
    if [ -d "$MODS_DIR" ]; then
        for dir in "$MODS_DIR"/*/; do
            if [ -f "$dir/mod.properties" ]; then
                mod_name=$(basename "$dir")
                modid=$(grep '^modid=' "$dir/mod.properties" | cut -d'=' -f2)
                echo "  $mod_name  ($modid)"
            fi
        done
    else
        echo "  (no mods directory found)"
    fi
    exit 1
fi

MOD_SLUG="$1"
MOD_DIR="$MODS_DIR/$MOD_SLUG"
PROPS_FILE="$MOD_DIR/mod.properties"

if [ ! -d "$MOD_DIR" ]; then
    echo "ERROR: Mod directory not found: $MOD_DIR"
    echo "Available mods:"
    for dir in "$MODS_DIR"/*/; do
        echo "  $(basename "$dir")"
    done
    exit 1
fi

if [ ! -f "$PROPS_FILE" ]; then
    echo "ERROR: mod.properties not found in $MOD_DIR"
    exit 1
fi

MODID=$(grep '^modid=' "$PROPS_FILE" | cut -d'=' -f2)
MOD_NAME=$(grep '^name=' "$PROPS_FILE" | cut -d'=' -f2)
MOD_GROUP=$(grep '^group=' "$PROPS_FILE" | cut -d'=' -f2)
MOD_VERSION=$(grep '^version=' "$PROPS_FILE" | cut -d'=' -f2)

if [ -z "$MODID" ] || [ -z "$MOD_NAME" ] || [ -z "$MOD_GROUP" ] || [ -z "$MOD_VERSION" ]; then
    echo "ERROR: mod.properties is missing required fields (modid, name, group, version)"
    echo "Contents of $PROPS_FILE:"
    cat "$PROPS_FILE"
    exit 1
fi

echo "=========================================="
echo "  Building: $MOD_NAME ($MODID)"
echo "  Group:    $MOD_GROUP"
echo "  Version:  $MOD_VERSION"
echo "=========================================="
echo ""

SRC_LINK="$ENGINE_DIR/src"

if [ -L "$SRC_LINK" ] || [ -d "$SRC_LINK" ]; then
    echo "Cleaning previous src link..."
    rm -f "$SRC_LINK"
fi

echo "Linking mod source..."
ln -s "$MOD_DIR/src" "$SRC_LINK"

echo "Configuring build.gradle..."

MANIFEST_BLOCK=""
FML_CORE_PLUGIN=$(grep '^fml_core_plugin=' "$PROPS_FILE" | cut -d'=' -f2-)
MIXIN_CONFIGS=$(grep '^mixin_configs=' "$PROPS_FILE" | cut -d'=' -f2-)

if [ -n "$FML_CORE_PLUGIN" ]; then
    MANIFEST_BLOCK="manifest { attributes 'FMLCorePlugin': '${FML_CORE_PLUGIN}', 'FMLCorePluginContainsFMLMod': true, 'ForceLoadAsMod': true }"
elif [ -n "$MIXIN_CONFIGS" ]; then
    MANIFEST_BLOCK="manifest { attributes 'MixinConfigs': '${MIXIN_CONFIGS}', 'FMLCorePluginContainsFMLMod': true, 'ForceLoadAsMod': true }"
fi

EXTRA_DEPS=""
EXTRA_DEPS_LINE=$(grep '^extra_dependencies=' "$PROPS_FILE" | cut -d'=' -f2-)
if [ -n "$EXTRA_DEPS_LINE" ]; then
    EXTRA_DEPS="$EXTRA_DEPS_LINE"
fi

python3 -c "
text = open('$ENGINE_DIR/build.gradle').read()
text = text.replace('__MOD_VERSION__', '$MOD_VERSION')
text = text.replace('__MOD_GROUP__', '$MOD_GROUP')
text = text.replace('__MOD_NAME__', '$MOD_NAME')
text = text.replace('__MODID__', '$MODID')
text = text.replace('__JAR_MANIFEST_BLOCK__', '''$MANIFEST_BLOCK''')
text = text.replace('__EXTRA_DEPENDENCIES__', '''$EXTRA_DEPS''')
open('$ENGINE_DIR/build.gradle', 'w').write(text)
"

echo "Running Gradle build..."
cd "$ENGINE_DIR"
./gradlew clean build

BUILD_EXIT=$?

if [ $BUILD_EXIT -eq 0 ]; then
    mkdir -p "$OUTPUT_DIR"

    JAR_FILE="$ENGINE_DIR/build/libs/${MOD_NAME}-${MOD_VERSION}.jar"
    if [ ! -f "$JAR_FILE" ]; then
        JAR_FILE="$ENGINE_DIR/build/libs/${MOD_NAME}-1.0.jar"
    fi

    if [ -f "$JAR_FILE" ]; then
        cp "$JAR_FILE" "$OUTPUT_DIR/"
        echo ""
        echo "=========================================="
        echo "  BUILD SUCCESSFUL"
        echo "=========================================="
        echo "  Output: $OUTPUT_DIR/$(basename "$JAR_FILE")"
        echo "=========================================="

        PRISM_MODS_DIR="$HOME/Library/Application Support/PrismLauncher/instances/1.12.2/minecraft/mods"
        if [ -d "$PRISM_MODS_DIR" ]; then
            echo ""
            echo "Deploying to PrismLauncher instance..."
            rm -f "$PRISM_MODS_DIR"/*.jar
            cp "$JAR_FILE" "$PRISM_MODS_DIR/"
            echo "  Cleared old mods and copied: $(basename "$JAR_FILE")"
            echo "  Target: $PRISM_MODS_DIR"
        else
            echo ""
            echo "WARNING: PrismLauncher mods directory not found:"
            echo "  $PRISM_MODS_DIR"
            echo "  Skipping deployment."
        fi
    else
        echo ""
        echo "WARNING: Build succeeded but JAR not found at expected location."
        echo "Looking in: $ENGINE_DIR/build/libs/"
        ls -la "$ENGINE_DIR/build/libs/" 2>/dev/null || echo "(directory not found)"
    fi
else
    echo ""
    echo "=========================================="
    echo "  BUILD FAILED"
    echo "=========================================="
fi

echo "Cleaning up..."
rm -f "$SRC_LINK"

python3 -c "
import re
text = open('$ENGINE_DIR/build.gradle').read()
text = re.sub(r'version = \".*\"', 'version = \"__MOD_VERSION__\"', text)
text = re.sub(r'group = \".*\"', 'group = \"__MOD_GROUP__\"', text)
text = re.sub(r'archivesBaseName = \".*\"', 'archivesBaseName = \"__MOD_NAME__\"', text)
text = re.sub(r'manifest \{.*\}', '__JAR_MANIFEST_BLOCK__', text)
text = text.replace('mixins.$MODID.refmap.json', 'mixins.__MODID__.refmap.json')
open('$ENGINE_DIR/build.gradle', 'w').write(text)
"

exit $BUILD_EXIT
