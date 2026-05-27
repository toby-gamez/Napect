Napéct
======

Napect is an Android app (Jetpack Compose) for storing and managing recipes locally. It uses Room for local storage, Hilt for dependency injection, Material3 Compose components for UI, and includes features such as recipe import from a URL or shared image, tagging, favorites and a cooking/make mode.

Key Technologies
- Kotlin + Jetpack Compose
- Android Gradle Plugin (AGP)
- Room (local database)
- Hilt (DI)
- Firebase AI (via BOM) for optional ML features
- DataStore Preferences for settings

Getting Started

Prerequisites
- JDK 11
- Android SDK (compileSdk 36)
- Android Studio Flamingo or later recommended

Open in Android Studio
1. Open Android Studio and choose "Open" and select the repository root.
2. Let Gradle sync and allow Android Studio to download required SDK components.
3. Run the app on an emulator or physical device.

Command-line build
1. Make sure you have JDK 11 and the Android SDK available.
2. From the project root you can run the Gradle wrapper:

   ./gradlew assembleDebug

3. Install the APK on a connected device:

   ./gradlew installDebug

Project Structure (important files)
- app/
  - src/main/java/com/tkolymp/napect: application and UI code (NapectApp, MainActivity)
  - build.gradle.kts: module build configuration and dependencies
  - AndroidManifest.xml: app manifest (application class: NapectApplication)
- build.gradle.kts: top-level build file
- settings.gradle.kts: included modules

App Behavior / Notes
- The app starts at the Home tab which lists recipes. Use the FAB to add a recipe manually or import from a URL.
- The app keeps tags and recipes in a Room database (NapectDatabase).
- Camera usage and image picking are handled via ActivityResult contracts inside the AddRecipeScreen.
- Some dependencies and integrations are configured through the version catalog (libs.versions.toml). If you need to update versions, edit the version catalog used by this project.

Contributing
- This repository does not include a contribution workflow. Open an issue or create a PR with a small, focused change.

License
- No license file is included in this repository. Add one if you plan to publish or open-source the project.

Contact
- For questions about code structure, start with app/src/main/java/com/tkolymp/napect and the ViewModel classes in ui/recipes.
