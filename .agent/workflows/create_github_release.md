---
description: Generates professional release notes and tags a new version for GitHub release.
---

1. **Check Git State**
   - Run `git status` to ensure clean working directory.

2. **Retrieve History**
   - Run `git tag --sort=-creatordate` to get the last tag.
   - Run `git log <last_tag>..HEAD --stat` to see changes.

3. **Generate Release Notes**
   - **Role**: Act as a Senior Technical Product Manager.
   - **Task**: Analyze commit messages and categorize them into:
     - 🚀 **Features Added**: New capabilities.
     - ✨ **Improvements**: UI polish, performance.
     - 🐛 **Bug Fixes**: Crash/error resolutions.
   - **Style**:
     - User-facing voice (benefits, not just technical specs).
     - Professional, enthusiastic (but not casual).
     - Clean Markdown structure.
   - **Output Format**:
     ```markdown
     # Release Notes - v<VERSION>

     [Summary paragraph]

     ### 🚀 Features Added
     - [Feature 1]
     - [Feature 2]

     ### ✨ Improvements
     - [Improvement 1]

     ### 🐛 Bug Fixes
     - [Fix 1]
     ```

4. **Tag & Release**
   - Propose the next logical version number based on semantic versioning and the changes.
   - Ask the user to confirm the version number.
   - If confirmed, run `git tag -a v<VERSION> -m "Release v<VERSION>"` (or include the summary).
   - "Push the tag" using `git push origin v<VERSION>`.
