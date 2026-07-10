# Project Horus

Project Horus is the Android-first autonomous mobile-agent product built on the Nova Agent gateway and Android client.

The current milestone is Mobile Task Control Plane: persistent tasks, replayable events, user commands, and explicit risk confirmation. Mobilerun device execution, native AccessibilityService control, and LiteRT-LM phone inference are separate milestones.

PC services run in WSL2 Ubuntu. Android builds run with JDK 17 and Android SDK 35.

## Development

```bash
npm run install:all
npm run test:gateway
npm run build
```

Android:

```powershell
cd nova-android
.\gradlew.bat test lintDebug assembleDebug
```
