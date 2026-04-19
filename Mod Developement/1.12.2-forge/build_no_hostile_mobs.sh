#!/bin/bash

echo "Building No Hostile Mobs mod for Minecraft 1.12.2..."
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
    echo "build/libs/No-Hostile-Mobs-1.0.0.jar"
    echo ""
    echo "To test the mod:"
    echo "1. Copy the jar to your Minecraft mods folder"
    echo "2. Launch Minecraft 1.12.2 with Forge"
    echo "3. Check config/nohostilemobs.cfg to customize blocked mobs"
    echo ""
else
    echo ""
    echo "=================================================="
    echo "Build failed! Check the error messages above."
    echo "=================================================="
    exit 1
fi
