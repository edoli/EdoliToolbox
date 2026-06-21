# AGENTS.md

## Project Overview

EdolAndroidTool is an Android utility collection app for personal tools used on Android devices. The app is built with Kotlin, Jetpack Compose, Material 3, and Gradle. The package name is `kr.co.edoli.edoltool`.

## Development Instructions

- Keep changes scoped to the requested feature or fix.
- Follow the existing Kotlin and Compose style in the project.
- Prefer adding new tool entries through `toolCatalog` before introducing larger navigation changes.
- After making code changes, always format the changed files and run validation.

## Formatting

Use the root `.editorconfig` as the source of formatting rules. Android Studio should respect it when running Reformat Code.

## Validation

Run this before finishing code changes:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

For documentation-only changes, at minimum run:

```powershell
git diff --check
```
