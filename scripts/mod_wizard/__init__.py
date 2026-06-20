"""
mod_wizard — Continuous interactive wizard for creating Minecraft 1.12.2 Forge mods.

Package structure:
    display.py           Terminal formatting and output helpers
    clipboard.py         Cross-platform clipboard reading
    prompt_composer.py   AI prompt generation with decompiled source links
    response_parser.py   Parse AI response → files + metadata + summary
    build_engine.py      Gradle build orchestration
    session.py           Session persistence (checkpoint save/restore + history)
    wizard.py            Step-by-step workflow functions
    main.py              Main loop + CLI entry point

Usage:
    python3 scripts/mod_wizard/main.py
    python3 scripts/mod_wizard/main.py --from-response ai_response.txt
"""
