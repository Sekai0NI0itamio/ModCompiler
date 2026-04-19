# Better Sprinting - Modrinth Upload Commands

## Step 1: Generate Bundle
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle Better-Sprinting-1.0.0.jar --use-ai-metadata
```

## Step 2: Verify Bundle
```bash
echo "verified" > AutoCreateModrinthBundles/better-sprinting-1.0.0/verify.txt
```

## Step 3: Create Draft
```bash
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --only-bundle better-sprinting-1.0.0 --verified
```

## Complete One-Liner
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle Better-Sprinting-1.0.0.jar --use-ai-metadata && echo "verified" > AutoCreateModrinthBundles/better-sprinting-1.0.0/verify.txt && python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --only-bundle better-sprinting-1.0.0 --verified
```

## Bundle Info
- **Bundle:** #24
- **Jar:** Better-Sprinting-1.0.0.jar
- **Source:** Better-Sprinting-Src/
- **AI Metadata:** Pre-generated
- **Client-Side:** Required
- **Server-Side:** Unsupported
