#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODULE_DIR="${ROOT_DIR}/node_modules/node-pty"
BUILD_DIR="${MODULE_DIR}/build/Release"
TMP_DIR="${ROOT_DIR}/.tmp/node-pty-universal"
ARM_DIR="${TMP_DIR}/arm64"
X64_DIR="${TMP_DIR}/x64"
ELECTRON_HOME="/tmp/electron-home"

mkdir -p "${ARM_DIR}" "${X64_DIR}" "${ELECTRON_HOME}"

echo "Rebuilding node-pty (arm64)..."
HOME="${ELECTRON_HOME}" npx electron-rebuild -f -w node-pty --arch arm64
cp "${BUILD_DIR}/pty.node" "${ARM_DIR}/pty.node"
cp "${BUILD_DIR}/spawn-helper" "${ARM_DIR}/spawn-helper"

echo "Rebuilding node-pty (x64)..."
HOME="${ELECTRON_HOME}" npx electron-rebuild -f -w node-pty --arch x64
cp "${BUILD_DIR}/pty.node" "${X64_DIR}/pty.node"
cp "${BUILD_DIR}/spawn-helper" "${X64_DIR}/spawn-helper"

echo "Creating universal binaries..."
lipo -create "${ARM_DIR}/pty.node" "${X64_DIR}/pty.node" -output "${BUILD_DIR}/pty.node"
lipo -create "${ARM_DIR}/spawn-helper" "${X64_DIR}/spawn-helper" -output "${BUILD_DIR}/spawn-helper"
chmod +x "${BUILD_DIR}/spawn-helper"

echo "Done. Verify with: file ${BUILD_DIR}/pty.node ${BUILD_DIR}/spawn-helper"
