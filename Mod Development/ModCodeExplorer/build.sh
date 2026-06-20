#!/bin/bash

# Build script for ModCodeExplorer
# This script builds the application using xcodebuild

set -e

echo "🔨 Building ModCodeExplorer..."

# Check if Xcode is installed
if ! command -v xcodebuild &> /dev/null; then
    echo "❌ Error: Xcode is not installed or xcodebuild is not in PATH"
    echo "Please install Xcode from the Mac App Store"
    exit 1
fi

# Check Xcode version
XCODE_VERSION=$(xcodebuild -version | head -n 1)
echo "✅ Using $XCODE_VERSION"

# Get the project directory
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_FILE="$PROJECT_DIR/ModCodeExplorer.xcodeproj"

# Check if project file exists
if [ ! -d "$PROJECT_FILE" ]; then
    echo "❌ Error: Xcode project file not found at $PROJECT_FILE"
    exit 1
fi

# Clean previous build
echo "🧹 Cleaning previous build..."
xcodebuild -project "$PROJECT_FILE" \
           -scheme ModCodeExplorer \
           -configuration Release \
           -destination 'platform=macOS' \
           clean

# Build the project
echo "🚀 Building ModCodeExplorer..."
xcodebuild -project "$PROJECT_FILE" \
           -scheme ModCodeExplorer \
           -configuration Release \
           -destination 'platform=macOS' \
           build

echo ""
echo "✅ Build successful!"
echo ""
echo "The built application can be found in DerivedData:"
echo "  ~/Library/Developer/Xcode/DerivedData/ModCodeExplorer-*/Build/Products/Release/ModCodeExplorer.app"
echo ""
echo "To run the application:"
echo "  open ~/Library/Developer/Xcode/DerivedData/ModCodeExplorer-*/Build/Products/Release/ModCodeExplorer.app"
