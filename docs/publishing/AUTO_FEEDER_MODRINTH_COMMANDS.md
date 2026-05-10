# Auto Feeder - Modrinth Upload Commands

## Bundle Location
`ToBeUploaded/20/`

## Files
- `Auto-Feeder-1.0.0.jar` - Main mod file
- `Auto-Feeder-1.0.0-sources.jar` - Source code

## Step 1: Create Project (if not exists)

```bash
curl -X POST "https://api.modrinth.com/v2/project" \
  -H "Authorization: YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "slug": "auto-feeder",
    "title": "Auto Feeder",
    "description": "Automatically feeds animals from nearby chests to breed them. Perfect for automated farms!",
    "body": "See description.md",
    "categories": ["utility", "technology"],
    "client_side": "optional",
    "server_side": "required",
    "license_id": "MIT",
    "initial_versions": []
  }'
```

## Step 2: Upload Version

```bash
curl -X POST "https://api.modrinth.com/v2/version" \
  -H "Authorization: YOUR_TOKEN" \
  -F 'data={
    "project_id": "auto-feeder",
    "version_number": "1.0.0",
    "version_title": "Auto Feeder 1.0.0",
    "changelog": "Initial release!\n\n- Automatic animal feeding from chests\n- Smart pair-based feeding system\n- Supports cows, pigs, sheep, chickens, horses, and rabbits\n- Efficient chest checking on interaction and periodic intervals\n- Configurable per-animal type, range, and timing\n- Visual and audio feedback",
    "game_versions": ["1.12.2"],
    "version_type": "release",
    "loaders": ["forge"],
    "featured": true,
    "dependencies": [],
    "file_parts": ["jar", "sources"]
  }' \
  -F "jar=@ToBeUploaded/20/Auto-Feeder-1.0.0.jar" \
  -F "sources=@ToBeUploaded/20/Auto-Feeder-1.0.0-sources.jar"
```

## Alternative: Use Modrinth Web Interface

1. Go to https://modrinth.com/dashboard/projects
2. Click "Create a project"
3. Fill in details from `ai_metadata/project_info.json`
4. Upload description from `ai_metadata/description.md`
5. Create version with details from `ai_metadata/version_info.json`
6. Upload both JAR files

## Project Details

- **Slug**: auto-feeder
- **Title**: Auto Feeder
- **Version**: 1.0.0
- **Minecraft**: 1.12.2
- **Loader**: Forge
- **Categories**: Utility, Technology
- **License**: MIT
