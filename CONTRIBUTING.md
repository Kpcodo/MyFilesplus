# Contributing to MyFiles+

First off, thank you for considering contributing to MyFiles+! It's people like you who make this project better.

To ensure a smooth process, please follow these guidelines.

## 📋 Table of Contents
- [Code of Conduct](#-code-of-conduct)
- [How Can I Contribute?](#-how-can-i-contribute)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Enhancements](#suggesting-enhancements)
  - [Pull Requests](#pull-requests)
- [Style Guidelines](#-style-guidelines)
  - [Kotlin Style](#kotlin-style)
  - [Compose Guidelines](#compose-guidelines)
  - [Git Commit Messages](#git-commit-messages)

## 🤝 Code of Conduct
By participating in this project, you are expected to uphold our commitment to a welcoming and inclusive environment. Please be respectful and professional in all interactions.

## 🚀 How Can I Contribute?

### Reporting Bugs
If you find a bug, please open an **Issue** and include:
- A clear, descriptive title.
- Steps to reproduce the bug.
- Expected vs. actual behavior.
- Device information (Model, Android Version).
- Screenshots or recordings if applicable.

### Suggesting Enhancements
Feature requests are welcome! When suggesting an enhancement:
- Use a clear title.
- Describe the feature and why it would be useful.
- If it involves UI changes, a mockup or sketch is highly appreciated.

### Pull Requests
1. **Fork** the repository and create your branch from `master`.
2. **Sync** your local setup (Gradle sync, etc.).
3. **Draft** your changes, ensuring they follow the style guidelines.
4. **Test** your changes thoroughly on a device or emulator.
5. **Open a PR** with a clear description of what you've done.
6. Reference any related Issues.

## 🎨 Style Guidelines

### Kotlin Style
- We follow the [Official Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html).
- Use `PascalCase` for classes and `camelCase` for variables and functions.
- Prefer `val` over `var` whenever possible.

### Compose Guidelines
- Composable functions should be `PascalCase`.
- Use `Modifier` as the first optional parameter of any UI component.
- Keep Composables stateless by hoisting state to ViewModels where appropriate.
- Use `MaterialTheme.colorScheme` and `MaterialTheme.typography` for consistent styling.

### Git Commit Messages
- Use the imperative mood in the subject line (e.g., "Add feature" instead of "Added feature").
- Keep the subject line short (under 50 characters).
- Use the body to explain **what** and **why**, if the change is complex.

Example:
```text
feat: implement auto-update download logic

- Added Ktor onDownload listener for progress
- Integrated FileProvider for APK installation
- Added success/error states in SettingsViewModel
```

---
Happy coding! 🚀
