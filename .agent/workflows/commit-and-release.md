---
description: Triggered by "Commit And Release". Commits, builds, and releases the Android app.
---

// turbo-all

1. Run the release automation script.
   ```bash
   python scripts/release.py
   ```

Note: Ensure you have `gh` (GitHub CLI) installed and authenticated. The script will handle version extraction, committing, building, renaming, and releasing.
