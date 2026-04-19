#!/bin/bash

echo "Building Area Dig mod for Minecraft 1.12.2..."
echo "=================================================="

# Clean previous builds
echo "Cleaning previous builds..."
./gradlew clean

# Build the mod
echo "Building mod..."
./gradlew build

# Check if build was successful
if [ $? -eq 0 ]; then
    echo ""
    echo "=================================================="
    echo "Build successful!"
    echo "=================================================="
    echo ""
    echo "The compiled jar is located at:"
    echo "build/libs/Area-Dig-1.0.0.jar"
    echo ""
    echo "To test the mod:"
    echo "1. Copy the jar to your Minecraft mods folder"
    echo "2. Launch Minecraft 1.12.2 with Forge"
    echo "3. Enchant a pickaxe, axe, or shovel with Area Dig"
    echo "4. Mine a block and watch the area effect!"
    echo ""
    echo "Enchantment levels:"
    echo "  Level 1: 2-block radius (5x5x5 cube)"
    echo "  Level 2: 3-block radius (7x7x7 cube)"
    echo "  Level 3: 4-block radius (9x9x9 cube)"
    echo "  Level 4: 5-block radius (11x11x11 cube)"
    echo "  Level 5: 6-block radius (13x13x13 cube)"
    echo ""
else
    echo ""
    echo "=================================================="
    echo "Build failed! Check the error messages above."
    echo "=================================================="
    exit 1
fi
