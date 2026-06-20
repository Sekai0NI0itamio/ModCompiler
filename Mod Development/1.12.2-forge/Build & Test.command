#!/bin/bash
# Double-click to build and test a single 1.12.2 Forge mod
# Prompts you to select which mod to build, then launches Prism Launcher

cd "$(dirname "$0")"

MODS_DIR="mods"
OUTPUT_DIR="output"
MC_MODS="/Users/stevennovak/Library/Application Support/PrismLauncher/instances/1.12.2 survival/minecraft/mods"

echo "========================================="
echo "  1.12.2 Forge - Build & Test"
echo "========================================="
echo ""

JAVA_HOME=$(/usr/libexec/java_home -v 1.8 2>/dev/null)
if [ -z "$JAVA_HOME" ]; then
    echo "ERROR: Java 8 not found. Install it and try again."
    echo "Press Enter to exit..."
    read
    exit 1
fi
export JAVA_HOME

MODS=()
for dir in "$MODS_DIR"/*/; do
    if [ -f "$dir/mod.properties" ]; then
        MODS+=("$(basename "$dir")")
    fi
done

if [ ${#MODS[@]} -eq 0 ]; then
    echo "No mods found in $MODS_DIR/"
    echo "Press Enter to exit..."
    read
    exit 1
fi

echo "Select a mod to build and test:"
echo ""
for i in "${!MODS[@]}"; do
    NUM=$((i+1))
    MODDIR="${MODS[$i]}"
    MODVER=$(grep '^version=' "$MODS_DIR/$MODDIR/mod.properties" | cut -d'=' -f2)
    MODNAME=$(grep '^name=' "$MODS_DIR/$MODDIR/mod.properties" | cut -d'=' -f2)
    echo "  $NUM) $MODNAME v$MODVER"
done
echo ""

read -p "Enter number (1-${#MODS[@]}): " CHOICE

if ! [[ "$CHOICE" =~ ^[0-9]+$ ]] || [ "$CHOICE" -lt 1 ] || [ "$CHOICE" -gt ${#MODS[@]} ]; then
    echo "Invalid selection."
    echo "Press Enter to exit..."
    read
    exit 1
fi

SELECTED="${MODS[$((CHOICE-1))]}"
MODNAME=$(grep '^name=' "$MODS_DIR/$SELECTED/mod.properties" | cut -d'=' -f2)
MODVER=$(grep '^version=' "$MODS_DIR/$SELECTED/mod.properties" | cut -d'=' -f2)

echo ""
echo "Building $MODNAME v$MODVER..."
echo ""

bash build_mod.sh "$SELECTED" 2>&1

JAR_FILE="$OUTPUT_DIR/$MODNAME-$MODVER.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo ""
    echo "ERROR: Build failed. JAR not found."
    echo "Press Enter to exit..."
    read
    exit 1
fi

echo ""
echo "Build successful: $JAR_FILE"

if [ -d "$MC_MODS" ]; then
    cp "$JAR_FILE" "$MC_MODS/"
    echo "Installed to: $MC_MODS/$MODNAME-$MODVER.jar"
else
    echo ""
    echo "WARNING: Minecraft mods folder not found at:"
    echo "  $MC_MODS"
    echo "Copy the JAR manually from: $JAR_FILE"
fi

echo ""
echo "Launching Prism Launcher..."
open -a "Prism Launcher"
