---
id: MODRINTH-VERSION-TITLE-TOO-LONG
title: Modrinth publish fails with "version_title failed validation with error: length" when replacing shell versions
tags: [modrinth, publish, shell-version, version-title, api-error]
versions: []
loaders: []
symbols: [version_title, shell, replace]
error_patterns: ["version_title failed validation with error: length", "HTTP 400.*version_title.*length"]
---

## Issue

The Modrinth publish step fails with HTTP 400 when trying to upload a real jar
to replace an existing shell version.

## Error

```
Upload failed: Modrinth API request failed with HTTP 400.
Response: {"error":"invalid_input","description":"Error while validating input:
Field version_title failed validation with error: length"}
```

## Root Cause

When the publisher finds a shell version (tiny placeholder jar) on Modrinth, it
uploads the real jar as `v1.0.1` to replace it. The auto-generated version title
for the replacement is too long for Modrinth's validation (Modrinth enforces a
maximum title length).

This typically happens when:
1. Shell versions were created for a project before the real jars were built
2. The publisher tries to replace them with a `v1.0.1` upload
3. The auto-generated title includes the full mod name + version + loader + MC version,
   exceeding Modrinth's character limit

## Fix

**Option 1 (recommended): Delete the shell versions first, then publish fresh**

Use the `Delete Modrinth Shell Versions` workflow to remove all shells before
running the publish step:

```
Workflow: Delete Modrinth Shell Versions
Input: project_slug = your-mod-slug
Input: threshold_bytes = 5000
Input: dry_run = false
```

Then re-run the publish workflow. Without shells, the publisher uploads as `v1.0.0`
with a shorter title that passes validation.

**Option 2: Use the `Publish Modrinth Bundle` workflow with a prior run's artifacts**

If the build already succeeded but publish failed, use `Publish Modrinth Bundle`
with the run ID of the successful build. The publisher will detect the shells and
attempt replacement — if this also fails, delete the shells first (Option 1).

**Option 3: Publish from a different run that didn't encounter the shells**

If the shells were already deleted between runs, a fresh publish from any run's
artifacts will upload as `v1.0.0` and succeed.

## Example

In the Working Full Bright port (May 2026), NeoForge 1.20.2 and 1.20.4 had shell
versions. The first publish attempt failed with this error. The fix was to use
`Publish Modrinth Bundle` with a different run's artifacts — by that point the
shells had been replaced by the first (failed) attempt, so the second attempt
uploaded fresh as `v1.0.0` and succeeded.

## Prevention

When building a new mod from scratch, avoid creating shell versions on Modrinth
before the real jars are ready. If shells are needed as placeholders, delete them
with the `Delete Modrinth Shell Versions` workflow before the first real publish run.

## Verified

Confirmed in Working Full Bright all-versions port (May 2026).
NeoForge 1.20.2 and 1.20.4 published successfully after using `Publish Modrinth Bundle`
with a run that had the real jars.
