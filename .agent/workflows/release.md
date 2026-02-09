---
description: Generate release notes, increment version, and create a GitHub release after approval.
---

1. Get the current git diff and latest tag.
// turbo
python scripts/antigravity_tools.py get_diff
// turbo
python scripts/antigravity_tools.py next_tag

2. Read the active editor context and the diff above.
3. Generate release notes follow this format:
   ## [Version] - [Date]
   ### ✨ New Features
   ### 🐞 Bug Fixes
   ### 🛠️ Technical Improvements

4. Propose the generated notes and the next version tag.
5. **Wait for Approval**: Stop and ask the user to reply with 'Approved'.
6. If approved, run:
// turbo
python scripts/antigravity_tools.py finalize_release <tag> <path_to_notes_file>
