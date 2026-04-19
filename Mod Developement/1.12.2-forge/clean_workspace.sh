#!/bin/bash

# Clean Workspace Script
# Use this before starting a new mod to prevent source code conflicts

echo "=========================================="
echo "  Mod Development Workspace Cleaner"
echo "=========================================="
echo ""
echo "This script will:"
echo "  1. Remove all mod source files from src/main/java/com/"
echo "  2. Remove all mod assets from src/main/resources/assets/"
echo "  3. Clean build artifacts"
echo ""
echo "⚠️  WARNING: This will delete all source files in the workspace!"
echo "   Make sure you've saved your current mod to ModCollection first."
echo ""
read -p "Do you want to continue? (yes/no): " -r
echo ""

if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    echo "Cancelled. No changes made."
    exit 0
fi

echo "Cleaning workspace..."
echo ""

# Remove all mod packages
if [ -d "src/main/java/com" ]; then
    echo "→ Removing mod source files..."
    rm -rf src/main/java/com/*
    echo "  ✓ Removed src/main/java/com/*"
else
    echo "  ℹ No source files to remove"
fi

# Remove all mod assets
if [ -d "src/main/resources/assets" ]; then
    echo "→ Removing mod assets..."
    rm -rf src/main/resources/assets/*
    echo "  ✓ Removed src/main/resources/assets/*"
else
    echo "  ℹ No assets to remove"
fi

# Clean build artifacts
echo "→ Cleaning build artifacts..."
./gradlew clean > /dev/null 2>&1
echo "  ✓ Build artifacts cleaned"

echo ""
echo "=========================================="
echo "  ✓ Workspace Cleaned Successfully!"
echo "=========================================="
echo ""
echo "Your workspace is now ready for a new mod."
echo ""
echo "Next steps:"
echo "  1. Create your mod package: mkdir -p src/main/java/com/yourmod"
echo "  2. Update build.gradle with your mod info"
echo "  3. Create src/main/resources/mcmod.info"
echo "  4. Write your mod code"
echo "  5. Build: ./gradlew clean build"
echo ""
echo "See docs/QUICK_START_NEW_MOD.md for detailed instructions."
echo ""
