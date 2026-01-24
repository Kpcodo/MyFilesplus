<div align="center">
  <img src="app/src/main/res/drawable/ic_myfiles_plus.png" width="128" />
  <h1>MyFiles+</h1>
  <p><b>A flagship-grade, private, and powerful File Manager for Android.</b></p>
  
  [![Version](https://img.shields.io/badge/version-1.3.0-blue.svg?style=for-the-badge)](https://github.com/Kpcodo/MyFilesplus/releases)
  [![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg?style=for-the-badge&logo=android)](https://developer.android.com/about/versions/oreo)
  [![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-purple.svg?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
  [![Material 3](https://img.shields.io/badge/Material-3-red.svg?style=for-the-badge)](https://m3.material.io/)
</div>

---

**MyFiles+** is a modern, fast, and lightweight File Manager built for the modern Android ecosystem. Designed with **Jetpack Compose** and **Material 3**, it provides a seamless, fluid experience while offering desktop-class file management capabilities right in your pocket.

## ✨ Features

### 📁 Advanced File Management
- **Seamless Navigation**: Fluid swipe gestures to switch between Home, Recents, Bin, and Settings.
- **Batch Operations**: Copy, move, rename, and delete multiple files with high-performance execution.
- **Deep Search**: Instant search with advanced filters (Type, Size, Date).
- **Archive Support**: Full integration for ZIP extraction and management.
- **Secure Bin**: Built-in trash system to prevent accidental data loss with easy restoration.

### 📊 Storage Insights & Health
- **Visual Dashboard**: Comprehensive breakdown of your storage categories (Images, Videos, Apps, Docs, etc.).
- **Smart Forecast**: AI-driven storage prediction that tells you exactly when you'll run out of space based on usage patterns.
- **Storage Health**: Identify large files and "junk" (temp/log/cache) that are consuming valuable space.

### 🧹 Intelligent Cleaning
- **Large File Finder**: Quickly identify and remove space-intensive files.
- **Junk Discovery**: Detect and clear hidden `.tmp`, `.log`, and `.temp` files.
- **Empty Folder Cleaner**: Deep-scan for unused empty directories to keep your storage organized.
- **Liquid Animations**: Satisfying, high-fidelity animations for cleaning processes.

### 📦 Integrated Media & Editors
- **Video Previews**: Instant high-quality video thumbnails.
- **Built-in Viewers**: native support for image viewing and video playback.
- **Text Editor**: Integrated monospace text editor with copy-to-clipboard functionality.

### 💅 Premium Design & Experience
- **Material You**: Dynamic color support that adapts to your wallpaper (Android 12+).
- **AMOLED Dark Mode**: True black theme for OLED screens to save battery and reduce eye strain.
- **Optimistic UI**: Blazing fast interactions with optimistic state updates for zero-lag feeling.
- **Modern Typography**: Featuring the latest Material 3 typography system.

## 🛠️ Tech Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (100% Declarative UI)
- **Architecture**: MVVM (Model-View-ViewModel) + Clean Architecture principles
- **Asynchronous Logic**: [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & Flow
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/) (Optimized for Android)
- **Networking**: [Ktor](https://ktor.io/) (High-performance asynchronous client)
- **Data Persistence**: [Jetpack DataStore](https://developer.android.com/topic/libraries/architecture/datastore) (Preferences)
- **Archive Management**: [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/)
- **Standard Library**: [Kotlin Serialization](https://kotlinlang.org/docs/serialization.html)

## 📸 Screenshots

<div align="center">
  <table>
    <tr>
      <td><img src="screenshots/01_permission.png" width="220" /><br/><p align="center">Privacy First</p></td>
      <td><img src="screenshots/02_home.png" width="220" /><br/><p align="center">Modern Dashboard</p></td>
      <td><img src="screenshots/03_home_scrolled.png" width="220" /><br/><p align="center">Category Flow</p></td>
      <td><img src="screenshots/04_search.png" width="220" /><br/><p align="center">Smart Search</p></td>
    </tr>
    <tr>
      <td><img src="screenshots/05_storage_health.png" width="220" /><br/><p align="center">Storage Health</p></td>
      <td><img src="screenshots/06_bin.png" width="220" /><br/><p align="center">Secure Bin</p></td>
      <td><img src="screenshots/07_ghost_files.png" width="220" /><br/><p align="center">Cleaning</p></td>
      <td><img src="screenshots/08_settings.png" width="220" /><br/><p align="center">Settings</p></td>
    </tr>
  </table>
</div>

## 🚀 Setup & Installation

1.  **Clone** the repository:
    ```bash
    git clone https://github.com/Kpcodo/MyFilesplus.git
    ```
2.  **Open** the project in [Android Studio](https://developer.android.com/studio).
3.  **Sync** Gradle and ensure you have **JDK 17** configured.
4.  **Run** on an Android device or emulator (Android 8.0/API 26+).

## 🤝 Contributing

We welcome all contributions! Whether it's reporting a bug, suggesting a feature, or submitting a pull request, your help is appreciated. Please see [CONTRIBUTING.md](CONTRIBUTING.md) for more details.

## 🛡️ Security & Privacy

MyFiles+ is built with privacy in mind. We do not collect or transmit your personal data. All file operations are performed locally on your device. For more details, see [SECURITY.md](SECURITY.md).

---
<div align="center">
  Built with ❤️ by the MyFiles+ Team
</div>
