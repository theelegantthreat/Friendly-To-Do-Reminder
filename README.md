# Friendly To‑Do Reminder

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-or-later-blue.svg)](https://www.gnu.org/licenses/gpl-3.0.en.html)

A small, friendly Android to‑do app that helps you keep track of tasks and sends reminders so nothing slips through the cracks.

Summary

- Platform: Android (Gradle, Android SDK)
- Language: Java / Kotlin (sources under `app/src/main`)
- License: GNU GPL v3.0 or later (see `LICENSE`)

Quick links

- Source: https://github.com/theelegantthreat/Friendly-To-Do-Reminder
- Issue tracker: https://github.com/theelegantthreat/Friendly-To-Do-Reminder/issues

Screenshots

(You can add screenshots under `assets/` and reference them here.)

Features

- Create, edit, and delete tasks
- Set due dates and reminders/notifications
- Mark tasks as completed
- Search and filter tasks
- Lightweight and easy to extend

Requirements

- Java 11+ (or the JDK version required by the project)
- Android SDK (with an emulator or a device)
- Android Studio (recommended) or Gradle (CLI)

Installation & Quick Start

1. Clone the repository:

```bash
git clone https://github.com/theelegantthreat/Friendly-To-Do-Reminder.git
cd Friendly-To-Do-Reminder
```

2. Open in Android Studio (recommended):

- Open Android Studio → Open an existing project → choose this repository root.
- Allow Android Studio to download any missing SDK components and Gradle tooling.
- Select a target device/emulator and click Run (green ▶️).

3. Command-line (Gradle):

If the Gradle wrapper (`gradlew` / `gradlew.bat`) is present, prefer it:

```bash
# macOS / Linux
./gradlew assembleDebug
./gradlew installDebug

# Windows
gradlew.bat assembleDebug
gradlew.bat installDebug
```

If no wrapper is available, use your locally installed Gradle (ensure compatibility):

```bash
gradle assembleDebug
gradle installDebug
```

Install the APK manually (if needed):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Development

- Run lint and unit tests:

```bash
./gradlew lint
./gradlew test
```

- Create a feature branch:

```bash
git checkout -b feature/your-feature-name
```

License

This project is licensed under the GNU General Public License v3.0 or later — see the `LICENSE` file for the full text.

Contributing

Contributions are welcome. To contribute:

1. Fork the repository.
2. Create a branch for your change.
3. Write tests and update documentation where applicable.
4. Open a pull request describing the change.

Please follow conventional commit messages and ensure your branch is up to date with the default branch before opening a PR.

Attribution / Copyright

Copyright (C) 2026 theelegantthreat

Contact

If you need help or want to propose new features, open an issue or reach out via GitHub.
