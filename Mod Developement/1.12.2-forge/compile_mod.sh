#!/bin/bash
set -e

if [ "$#" -lt 3 ]; then
    echo "Usage: ./compile_mod.sh <mod_id> <archivesBaseName> <group>"
    echo "Example: ./compile_mod.sh strong_seeds Strong-Seeds com.itamio.strong_seeds"
    exit 1
fi

MOD_ID=$1
ARCHIVES_BASE_NAME=$2
GROUP=$3

echo "Configuring build for ${ARCHIVES_BASE_NAME} (${MOD_ID}) in group ${GROUP}..."

# Update build.gradle to ensure metadata independence
# It looks for `group = "..."` and replaces the string, same for `archivesBaseName`
sed -i '' -e "s/^group = \".*\"/group = \"${GROUP}\"/g" build.gradle
sed -i '' -e "s/^archivesBaseName = \".*\"/archivesBaseName = \"${ARCHIVES_BASE_NAME}\"/g" build.gradle

# Also handle if it doesn't start with ^ (no leading spaces)
sed -i '' -e "s/group = \".*\"/group = \"${GROUP}\"/g" build.gradle || true
sed -i '' -e "s/archivesBaseName = \".*\"/archivesBaseName = \"${ARCHIVES_BASE_NAME}\"/g" build.gradle || true

# Generate independent mcmod.info using the script arguments
cat << INFO > src/main/resources/mcmod.info
[
{
  "modid": "${MOD_ID}",
  "name": "${ARCHIVES_BASE_NAME}",
  "description": "${ARCHIVES_BASE_NAME} automated build.",
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

echo "Running ./gradlew clean build..."
./gradlew clean build

echo "======================================"
# Extract final output
mkdir -p ModCollection
cp "build/libs/${ARCHIVES_BASE_NAME}-1.0.jar" "ModCollection/${ARCHIVES_BASE_NAME}-1.0.jar"

echo "Successfully built and exported to ModCollection/${ARCHIVES_BASE_NAME}-1.0.jar!"
