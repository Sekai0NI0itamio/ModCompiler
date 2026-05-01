#!/bin/bash
# Build ModrinthFetcher.app for Apple Silicon macOS
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/Sources"
OUT="$SCRIPT_DIR/build"
APP="$OUT/ModrinthFetcher.app"
SDK=$(xcrun --sdk macosx --show-sdk-path)
TARGET="arm64-apple-macosx14.0"

echo "==> Building ModrinthFetcher..."
echo "    SDK: $SDK"
echo "    Target: $TARGET"

mkdir -p "$OUT"
rm -rf "$APP"

SOURCES=(
    "$SRC/Models.swift"
    "$SRC/GitHubService.swift"
    "$SRC/FetchViewModel.swift"
    "$SRC/ContentView.swift"
    "$SRC/App.swift"
)

swiftc "${SOURCES[@]}" \
    -sdk "$SDK" \
    -target "$TARGET" \
    -framework SwiftUI \
    -framework AppKit \
    -framework Foundation \
    -parse-as-library \
    -O \
    -o "$OUT/ModrinthFetcher"

echo "==> Packaging .app bundle..."

mkdir -p "$APP/Contents/MacOS"
mkdir -p "$APP/Contents/Resources"

cp "$OUT/ModrinthFetcher" "$APP/Contents/MacOS/ModrinthFetcher"

cat > "$APP/Contents/Info.plist" << 'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>ModrinthFetcher</string>
    <key>CFBundleIdentifier</key>
    <string>com.itamio.ModrinthFetcher</string>
    <key>CFBundleName</key>
    <string>Modrinth Fetcher</string>
    <key>CFBundleDisplayName</key>
    <string>Modrinth Fetcher</string>
    <key>CFBundleVersion</key>
    <string>1.0.0</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>LSMinimumSystemVersion</key>
    <string>14.0</string>
    <key>NSPrincipalClass</key>
    <string>NSApplication</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSSupportsAutomaticGraphicsSwitching</key>
    <true/>
    <key>LSApplicationCategoryType</key>
    <string>public.app-category.developer-tools</string>
    <key>NSAppTransportSecurity</key>
    <dict>
        <key>NSAllowsArbitraryLoads</key>
        <false/>
        <key>NSExceptionDomains</key>
        <dict>
            <key>modrinth.com</key>
            <dict>
                <key>NSExceptionAllowsInsecureHTTPLoads</key>
                <false/>
                <key>NSIncludesSubdomains</key>
                <true/>
            </dict>
            <key>api.modrinth.com</key>
            <dict>
                <key>NSExceptionAllowsInsecureHTTPLoads</key>
                <false/>
                <key>NSIncludesSubdomains</key>
                <true/>
            </dict>
            <key>github.com</key>
            <dict>
                <key>NSExceptionAllowsInsecureHTTPLoads</key>
                <false/>
                <key>NSIncludesSubdomains</key>
                <true/>
            </dict>
        </dict>
    </dict>
</dict>
</plist>
PLIST

echo "==> Done!"
echo ""
echo "    App: $APP"
echo ""
echo "    To run:"
echo "    open '$APP'"
