# Android Adaptive App Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a polished, native Android adaptive launcher icon to the Project Horus app and prove that it packages and launches on the existing emulator.

**Architecture:** Android API 26+ adaptive-icon XML composes a stable graphite background with a padded vector foreground and a monochrome themed-icon layer. The manifest names the standard and round resources, so launchers receive the intended icon without runtime code or a bitmap pipeline.

**Tech Stack:** Android resource XML, AdaptiveIconDrawable, VectorDrawable, Gradle/AGP, Android SDK platform-tools ADB.

## Global Constraints

- Canonical checkout is `C:\Users\salih\Project_Horus` on branch `codex/mobile-task-control-plane`; preserve unrelated changes.
- Android remains `compileSdk=35`, `targetSdk=35`, `minSdk=26`, JVM 17.
- The icon must be native adaptive XML, provide `ic_launcher`, `ic_launcher_round`, and an Android 13+ monochrome layer, and carry no text, credentials, screenshots, device metadata, or worker controls.
- The visual design is a flat graphite background (`#10242D`), turquoise orbital signal (`#55D7C5` / `#C9FFF6`), and small amber core (`#F2B455`); avoid gradients and external asset dependencies.
- Use TDD-style resource validation: introduce unresolved manifest icon references, observe `processDebugResources` fail, then add the minimal resources and verify the same task succeeds.
- After completion, update `README.md`, `README.tr.md`, and `.superpowers/sdd/progress.md` with verified work and the next concrete item, and commit the complete task.

---

### Task 1: Ship the Adaptive Launcher Icon

**Files:**
- Modify: `nova-android/app/src/main/AndroidManifest.xml`
- Create: `nova-android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `nova-android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `nova-android/app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `nova-android/app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `nova-android/app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `README.md`
- Modify: `README.tr.md`
- Modify: `.superpowers/sdd/progress.md` (ignored execution ledger)

**Interfaces:**
- Consumes: the application manifest and Android API 26 adaptive icon resource resolver.
- Produces: `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` resources with foreground, background, and themed monochrome layers.

- [ ] **Step 1: Establish a failing resource contract**

Add the following attributes to the existing `<application>` element, without creating the resources yet:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

Run:

```powershell
cd C:\Users\salih\Project_Horus\nova-android
.\gradlew.bat :app:processDebugResources --console=plain
```

Expected: FAIL because `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` are unresolved.

- [ ] **Step 2: Add the minimal adaptive icon resources**

Create both `mipmap-anydpi-v26` resources with this common composition:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

Use a flat `#10242D` background. Place the foreground artwork inside the adaptive safe zone: a turquoise orbital ring with a light inner signal and a central amber core. The monochrome vector must use a single `?android:colorControlNormal` path fill and retain the same silhouette.

- [ ] **Step 3: Verify packaging and launcher compatibility**

Run:

```powershell
cd C:\Users\salih\Project_Horus\nova-android
.\gradlew.bat :app:processDebugResources :app:lintDebug :app:assembleDebug --console=plain
.\gradlew.bat :app:installDebug --console=plain
& 'C:\Users\salih\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 shell cmd package resolve-activity --brief com.nova.agent
```

Expected: Gradle succeeds and the final ADB command returns `com.nova.agent/.MainActivity`.

- [ ] **Step 4: Record delivery and commit**

Add a concise verified icon-delivery entry to both README living-delivery sections, set Task 6 local worker reachability as the next work item, and update the ignored SDD ledger. Then commit:

```bash
git add nova-android/app/src/main/AndroidManifest.xml nova-android/app/src/main/res/mipmap-anydpi-v26 nova-android/app/src/main/res/drawable README.md README.tr.md docs/superpowers/specs/2026-07-12-android-adaptive-app-icon-design.md docs/superpowers/plans/2026-07-12-android-adaptive-app-icon.md
git commit -m "feat: add Android adaptive app icon"
```

## Plan Self-Review

### Spec Coverage

- Manifest wiring, adaptive foreground/background, round icon, Android 13+ monochrome support, exact palette, build checks, emulator activity resolution, delivery documentation, and commit boundary are all covered by Task 1.

### Placeholder Scan

- The plan contains no incomplete handoff, generic test instruction, or undefined resource name.

### Type Consistency

- The two manifest IDs and the two `mipmap-anydpi-v26` filenames are identical, and every adaptive icon references the same three drawable layer names.
