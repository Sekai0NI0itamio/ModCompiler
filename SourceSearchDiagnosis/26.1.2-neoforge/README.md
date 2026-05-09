# Minecraft Source Search Results

This artifact was produced by the `AI Source Search` GitHub Actions workflow.
It contains decompiled Minecraft source code search results for use by an AI
coding IDE (Kiro, Copilot, etc.) when fixing mod compilation errors.

## Files

- `search-info.txt`         — search parameters and statistics
- `all-java-files.txt`      — full list of every .java file found
- `queries/`                — one result file per search query
- `full-classes/`           — full content of files that matched queries
- `api-overview/`           — broad API samples: event classes, render classes,
                              client classes, and a random 10% sample of all files
- `gradle-output.log`       — Gradle output (useful if sources are missing)

## How to use

1. Read `search-info.txt` to confirm sources were found.
2. Read `queries/<query>.txt` to see matches for each query.
3. Read `full-classes/<ClassName>.java` for the complete class definition.
4. Browse `api-overview/` for broad API discovery when you don't know the class name.
5. Use the API signatures you find to fix the mod source code.
