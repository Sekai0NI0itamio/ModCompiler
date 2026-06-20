# Project File Fix Notes

## Issue
The Xcode project file had a corrupted product reference that caused the error:
```
The project 'ModCodeExplorer' is damaged and cannot be opened.
Exception: -[PBXVariantGroup setExplicitFileTypeIfNil:]: unrecognized selector
```

## Root Cause
The app product (`ModCodeExplorer.app`) was incorrectly defined as a `PBXVariantGroup` instead of a `PBXFileReference`.

**Before (Incorrect):**
```
A5000001 /* ModCodeExplorer.app */ = {
    isa = PBXVariantGroup;  // ❌ Wrong type
    children = ();
    name = ModCodeExplorer.app;
    sourceTree = "<group>";
};
```

**After (Correct):**
```
A5000001 /* ModCodeExplorer.app */ = {
    isa = PBXFileReference;  // ✅ Correct type
    explicitFileType = "wrapper.application";
    path = ModCodeExplorer.app;
    sourceTree = BUILT_PRODUCTS_DIR;
};
```

## What Was Fixed
1. Removed the incorrect `PBXVariantGroup` section
2. Added proper `PBXFileReference` for the app product in the PBXFileReference section
3. Set correct properties:
   - `explicitFileType = "wrapper.application"` (for .app bundles)
   - `sourceTree = BUILT_PRODUCTS_DIR` (build output location)

## Verification
The project now opens successfully in Xcode without errors.

## How to Build
1. Open `ModCodeExplorer.xcodeproj` in Xcode
2. Select your development team (or use "None" for local testing)
3. Press ⌘R to build and run

## Technical Details
In Xcode project files:
- **PBXFileReference**: Represents actual files (source files, products, etc.)
- **PBXVariantGroup**: Used for localized resources (not for app products)
- App products must use `PBXFileReference` with `explicitFileType = "wrapper.application"`

---

**Status**: ✅ Fixed and verified
