# Security Policy for MyFiles+

Thank you for your interest in the security of **MyFiles+**. As a file management application, we treat user data privacy and local storage security with the utmost importance.

## 🛡️ Data Privacy & Architecture

**MyFiles+ is a local-only application.**

*   **No Cloud Uploads**: The application does not upload your files, metadata, or usage statistics to any external servers or cloud storage. All file operations (move, copy, delete, rename) are performed locally on your device.
*   **No Tracking**: We do not include third-party analytics or tracking SDKs that jeopardize user privacy.
*   **Permissions**:
    *   `MANAGE_EXTERNAL_STORAGE`: Used strictly for the core functionality of managing files across your device storage.
    *   `INTERNET`: Not requested/used for data exfiltration. (Note: Only used if future updates require fetching update info, but currently the app is offline-first).

## ✅ Supported Versions

We accept security reports for the latest stable release and the current beta branch.

| Version | Branch | Status |
| :--- | :--- | :--- |
| 1.x.x | `master` | :white_check_mark: Supported |
| Beta | `beta` | :white_check_mark: Supported |
| < 1.0 | n/a | :x: EOL |

## 🐛 Reporting a Vulnerability

If you discover a security vulnerability (e.g., unintended file exposure, permission bypass, or path traversal issues), please report it responsibly.

### How to Report
Please **do not** create public GitHub issues for security vulnerabilities.

1.  Draft a report with:
    *   Description of the vulnerability.
    *   Steps to reproduce.
    *   Affected Android versions/Devices.
2.  Email the maintainers directly at: **codebridgehub28@outlook.com** (Replace with actual email or contact method).

### Response Timeline
*   **Acknowledgement**: 48 hours.
*   **Assessment**: 5 business days.
*   **Fix**: As soon as possible, prioritized over feature work.

## ⚠️ Known Risks outside Scope
*   **Rooted Devices**: Running MyFiles+ on a rooted device grants it (and other apps) privileges beyond standard Android security models. We cannot guarantee security on compromised/rooted OS environments.
*   **Physical Access**: As a local app, we rely on Android's device encryption. If an attacker has unlocked physical access to your phone, they can access files via the app.

---
*Stay Safe, Stay Local.*
