# Template Create Mod Draft Bundle

This folder contains the perfect-example reference files and the bundle layout reference used by the auto-create draft workflow.

The most important style references are:

- `modname.txt`: perfect example of a clean mod title
- `summary.txt`: perfect example of a short Modrinth summary
- `description.txt`: perfect example of a long Modrinth page description
  It currently demonstrates the richer `Description` / `How It Works` / `Features` / `Why Use This Mod?` page structure.

The generator reads those three files and includes them in every AI prompt as style examples.

Typical bundle contents:

- `input/<original-jar>.jar`: copied jar that will be uploaded later
- `decompiled/`: locally decompiled source tree plus `mod_info.txt`
- `projectinfo.txt`: full project summary passed into DeepSeek
- `ai_request_user_message.txt`: the exact user message sent to C05 Local AI
- `ai_response.txt`: raw model output
- `listing.json`: parsed name + summary + long description
- `art/`: generated logo + description image assets, their prompts, and art metadata
- `modrinth.project.json`: editable Modrinth project creation payload
- `modrinth.version.json`: editable Modrinth version upload payload
- `icon.*`: auto-prepared icon file when the generated logo fits Modrinth's icon size limit
- `verify.txt`: change `pending` to `verified` after manual review
- `draft_state.json`: workflow state so re-runs do not lose track of project/version IDs
- `SUMMARY.md`: short human-readable overview

Suggested flow:

1. Put jars in `ToBeUploaded/`
2. Run `python3 scripts/auto_create_modrinth_draft_projects.py generate`
3. Review each generated bundle
4. Edit `verify.txt` so the first non-comment line is `verified`
5. Run `python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --modrinth-token YOUR_TOKEN`
6. Review the resulting draft on Modrinth, including the generated logo and the description image inserted under the title when upload permissions allow it
7. Submit it manually when you are satisfied

If you intentionally want to skip the `verify.txt` gate for a run, add `--verified` to `create-drafts`.
