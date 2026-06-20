#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODS_DIR="$SCRIPT_DIR/mods"

JAVA8_HOME=$(/usr/libexec/java_home -v 1.8 2>/dev/null || echo "")
if [ -n "$JAVA8_HOME" ]; then
    export JAVA_HOME="$JAVA8_HOME"
fi

echo "=========================================="
echo "  Building All Mods"
echo "=========================================="
echo ""

FAILED=()
SUCCEEDED=()
SKIPPED=()

for dir in "$MODS_DIR"/*/; do
    if [ ! -f "$dir/mod.properties" ]; then
        continue
    fi

    mod_slug=$(basename "$dir")
    modid=$(grep '^modid=' "$dir/mod.properties" | cut -d'=' -f2)
    mod_name=$(grep '^name=' "$dir/mod.properties" | cut -d'=' -f2)

    echo ""
    echo ">>> Building: $mod_name ($mod_slug)..."
    echo ""

    if "$SCRIPT_DIR/build_mod.sh" "$mod_slug"; then
        SUCCEEDED+=("$mod_name")
    else
        FAILED+=("$mod_name")
    fi
done

echo ""
echo "=========================================="
echo "  Build Summary"
echo "=========================================="
echo ""

if [ ${#SUCCEEDED[@]} -gt 0 ]; then
    echo "Succeeded (${#SUCCEEDED[@]}):"
    for m in "${SUCCEEDED[@]}"; do
        echo "  ✓ $m"
    done
fi

if [ ${#FAILED[@]} -gt 0 ]; then
    echo ""
    echo "Failed (${#FAILED[@]}):"
    for m in "${FAILED[@]}"; do
        echo "  ✗ $m"
    done
fi

echo ""

if [ ${#FAILED[@]} -eq 0 ]; then
    echo "All mods built successfully!"
    exit 0
else
    echo "Some mods failed to build."
    exit 1
fi
