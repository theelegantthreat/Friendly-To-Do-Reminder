# Friendly To-Do Reminder

A simple, friendly to-do list app that helps you track tasks and sends reminders so you never forget important items.

> Friendly-To-Do-Reminder aims to be lightweight, accessible, and easy to extend.

## Features

- Create, edit, and delete to-do items
- Set due dates and reminders/notifications
- Mark tasks as completed
- Search and filter tasks
- Simple and intuitive UI

## Tech / Language Composition

This repository contains the source code for the app. The project is an Android application using Gradle and the Android SDK. Source code is located under `app/src/main` (Java/Kotlin and Android resources).

If you want exact breakdowns of languages used, run a language analysis or check the repository language stats on GitHub.

## Installation

1. Clone the repository:

   ```bash
   git clone https://github.com/theelegantthreat/Friendly-To-Do-Reminder.git
   cd Friendly-To-Do-Reminder
   ```

2. Requirements

   - Java 11 (or the JDK version specified by the project)
   - Android SDK (with an emulator or a connected device)
   - Android Studio (recommended) or Gradle (if you prefer the command line)

3. Open and run (recommended - Android Studio)

   - Open Android Studio and choose "Open an existing project", then select this repository's root.
   - Let Android Studio download any missing SDK components and Gradle tooling.
   - Select a device or emulator and click Run (the green ▶️) to build and install the app.

4. Build and run from the command line (Gradle)

   - If the repository includes the Gradle wrapper (`gradlew` / `gradlew.bat`), prefer using it:

     ```bash
     # macOS / Linux
     ./gradlew assembleDebug
     ./gradlew installDebug   # builds and installs to a connected device/emulator

     # Windows
     gradlew.bat assembleDebug
     gradlew.bat installDebug
     ```

   - If there is no Gradle wrapper, use your locally installed Gradle (make sure it's compatible with the project):

     ```bash
     gradle assembleDebug
     gradle installDebug
     ```

   - To install the produced APK manually:

     ```bash
     adb install -r app/build/outputs/apk/debug/app-debug.apk
     ```

Notes

- If the Gradle wrapper is missing and you prefer a wrapper, open the project in Android Studio and it will generate or configure wrappers as needed.
- If you hit SDK or build errors, open the project in Android Studio and follow the IDE suggestions to install missing SDK packages or update the Gradle plugin.

## Usage

- Open the app on your device or emulator.
- Create a new task with a title and optional description.
- Set a due date and a reminder time.
- Tasks will appear in the main list; tap/click to mark as complete or edit details.

## Development

- Follow the repository's contribution guidelines (see below).
- Run the linter and tests before opening a PR if applicable (project-specific commands may vary):

  ```bash
  # examples — adjust to your project's tooling
  ./gradlew lint
  ./gradlew test
  ```

- Create feature branches from the default branch:

  ```bash
  git checkout -b feature/your-feature-name
  ```

## Contributing

Contributions are welcome! To contribute:

1. Fork the repository.
2. Create a branch for your change.
3. Commit your changes with clear messages.
4. Open a pull request describing the change and why it's useful.

If you have ideas for features or bug reports, please open an issue.

## License

This project is provided under the MIT License. See LICENSE for details (or add a LICENSE file if none exists).

## Contact

Maintained by theelegantthreat. For questions or suggestions, open an issue or contact the maintainer via GitHub.
