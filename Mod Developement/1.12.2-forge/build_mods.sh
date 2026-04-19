#!/bin/bash
set -e

build_mod() {
    MOD_ID=$1
    MOD_NAME=$2
    PKG_NAME=$3
    SRC_DIR=$4

    echo "Building $MOD_NAME..."
    
    # Setup clean workspace for this mod
    rm -rf src/main/java/com/*
    cp -r "ModCollection/${SRC_DIR}" "src/main/java/com/${PKG_NAME}"
    
    # Update build.gradle
    sed -i '' "s/group = .*/group = \"com.itamio.${MOD_ID}\"/g" build.gradle
    sed -i '' "s/archivesBaseName = .*/archivesBaseName = \"${MOD_NAME}\"/g" build.gradle
    
    # Setup mcmod.info
    cat << INFO > src/main/resources/mcmod.info
[
{
  "modid": "${MOD_ID}",
  "name": "${MOD_NAME}",
  "description": "${MOD_NAME} mod.",
  "version": "\${version}",
  "mcversion": "\${mcversion}",
  "url": "",
  "updateUrl": "",
  "authorList": ["Itamio"],
  "credits": "The Forge and FML guys",
  "logoFile": "",
  "screenshots": [],
  "dependencies": []
}
]
INFO

    # Clean and build
    ./gradlew clean build

    # Save to output
    cp "build/libs/${MOD_NAME}-1.0.jar" "ModCollection/${MOD_NAME}-1.0.jar"
    echo "======================================"
}

mkdir -p ModCollection/Instant-Hoppers-Src
cp -r src/main/java/com/instanthoppers ModCollection/Instant-Hoppers-Src/

# Now build both properly
build_mod "strong_seeds" "Strong-Seeds" "strongseeds" "Strong-Seeds-Src"
build_mod "instant_hoppers" "Instant-Hoppers" "instanthoppers" "Instant-Hoppers-Src"

echo "DONE."
