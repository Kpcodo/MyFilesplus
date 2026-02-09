---
description: Stage all changes, generate a commit message, and push to remote.
---

1. Stage all current changes.
// turbo
git add .

2. Analyze the changes in the workspace and the active editor to generate a descriptive commit message.

3. Execute the commit and push.
// turbo
python scripts/antigravity_tools.py commit "<generated_message>"
