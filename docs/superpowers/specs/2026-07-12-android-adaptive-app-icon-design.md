# Android Adaptive App Icon Design

## Purpose

Give the Android application a first-class launcher identity before the next delivery APK. The existing manifest has no application icon, so launchers currently fall back to generic application treatment.

## Decision

Use Android native adaptive-icon resources rather than raster artwork. The application has `minSdk=26`, so adaptive icons are available on every supported device. This keeps the mark crisp at small sizes, allows Android 13+ themed-icon treatment, and avoids a new runtime or asset-processing dependency.

The mark is a compact autonomous-agent signal: a dark graphite field, a turquoise orbital ring, and a small amber core. It carries no text, user data, device information, or controls.

## Resource Architecture

- `AndroidManifest.xml` references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` provide the API 26 baseline with foreground and background layers only.
- `mipmap-anydpi-v33/ic_launcher.xml` and `ic_launcher_round.xml` overlay that baseline on Android 13+ with foreground, background, and the themed monochrome layer.
- `drawable/ic_launcher_background.xml` supplies a flat graphite background.
- `drawable/ic_launcher_foreground.xml` supplies the padded colour mark inside Android's safe zone.
- `drawable/ic_launcher_monochrome.xml` supplies the single-colour themed-icon silhouette with the foreground's `-35` degree orbital rotation.

No legacy density PNGs are needed because the minimum supported API is 26. The icon resources are deliberately local Android XML, keeping the APK deterministic and the source editable.

## Validation

1. First point the manifest at the two launcher resources and run `:app:processDebugResources`; it must fail before the resources exist because both IDs are unresolved.
2. Add the API 26 foreground/background layers and API 33 monochrome overlays. Verify that neither v26 XML contains `<monochrome>` and both v33 XML files do, then rerun `:app:processDebugResources`, `:app:assembleDebug`, and `:app:lintDebug` successfully.
3. Install the debug APK on `emulator-5554` and resolve `com.nova.agent`'s launcher activity through ADB to prove the icon-bearing manifest remains launchable.
4. Update both living-delivery README sections and the SDD ledger with the exact verification result and the next concrete work item.

## Scope Boundaries

- Do not add dynamic icon switching, notification icons, a splash redesign, launcher permissions, or image-generation dependencies.
- Do not change task execution, worker policy, Android network contracts, or user-visible controls.
