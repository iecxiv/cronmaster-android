# Cronmaster Android App

[Español](README.md) | English

An unofficial native Android client for Cronmaster, built with Kotlin and Jetpack Compose. Includes a server extension and installation and update guides.

## Features

Tasks, scripts, backups, statistics, encrypted access through Android Keystore, icons, task health and a terminal viewer. Green means a successful result, red means failure, and gray means no verifiable result. Sessions are restored, but the server can revoke credentials. HTTPS is recommended. The client does not implement OIDC. Multiple servers are supported.

## Build from source

Clone the main branch:

```bash
git clone --branch main https://github.com/iecxiv/cronmaster-android.git
cd cronmaster-android
```

Open the project in an Android Studio version compatible with Android Gradle Plugin 8.7.3 and Gradle 8.9. Install Android SDK Platform 35 and synchronize Gradle. CI also installs Android SDK Build-Tools 35.0.0.

Select JDK 17 as the Gradle JDK and configure command-line JAVA_HOME consistently. Java/Kotlin source and bytecode compatibility are configured as Java 17; this is separate from the JDK running Gradle.

To select JDK 17 in Android Studio:

1. Open `File → Settings` on Windows/Linux or `Android Studio → Settings` on macOS.
2. Go to `Build, Execution, Deployment → Build Tools → Gradle`.
3. Select an installed JDK 17 under `Gradle JDK`.
4. If unavailable, select `Download JDK…`, version `17`, and Eclipse Temurin if listed, or another JDK 17 distribution.
5. Check that you have not selected `JetBrains Runtime 25.0.3`.
6. Click `Apply → OK`.
7. Run `File → Sync Project with Gradle Files`.

Build and run on a device or emulator with API 26 or newer. For command-line builds, configure ANDROID_HOME or an uncommitted local.properties file containing sdk.dir.

Debug APK, from the repository root:

```bash
# macOS / Linux
./gradlew :app:assembleDebug
```

```powershell
# Windows PowerShell
.\gradlew.bat :app:assembleDebug
```

After a successful build, the APK is at `app/build/outputs/apk/debug/app-debug.apk`.

Unit tests and build:

```bash
# macOS / Linux
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

```powershell
# Windows PowerShell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

With ADB configured:

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Plugin and dependency versions are in `gradle/libs.versions.toml`. The Gradle distribution is in `gradle/wrapper/gradle-wrapper.properties`. Android SDK levels and Java/Kotlin targets remain in `app/build.gradle.kts`.

### Wrapper bootstrap

If any wrapper file is missing, the workflow's `wrapper` job generates the genuine Gradle 8.9 wrapper in an empty temporary project and commits only `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` and `gradle/wrapper/gradle-wrapper.properties` to main. It also records the published distribution SHA-256. No personal token is required: only this job requests `contents: write`; the build job has read-only permissions.

For the first run after this migration, wait for the wrapper commit before cloning, or run `git pull --ff-only` afterwards in an unmodified checkout. If policies block the bot push, the job fails explicitly. The wrapper is not considered installed until all four files are in main. Later builds use the committed wrapper, not system Gradle.

The build job checks out the exact commit reported by the wrapper job, allowing it to test the wrapper in the same run without another push event. Repository rules may require owner approval for these permissions. Do not weaken branch protection without reviewing the implications.

The workflow generates a debug APK if tests pass. Check its result in Actions. It does not include a signed release APK or create tags or releases.

## Server: choose a deployment mode

- Existing Proxmox Community Scripts installation: [installation](docs/SERVIDOR-COMMUNITY-SCRIPTS_EN.md) and [updates](docs/UPDATE-COMMUNITY-SCRIPTS_EN.md). Detects the version from `/opt/cronmaster/package.json`, builds outside production and retains the original service.
- Keep the original code: [parallel installation](docs/SERVIDOR-PARALELO_EN.md) and [updates](docs/UPDATE-PARALELO_EN.md).
- Replace the active code: [replacement](docs/SERVIDOR-REEMPLAZO_EN.md) and [updates](docs/UPDATE-REEMPLAZO_EN.md).
- Shared steps for the preceding modes: [build](docs/COMPILAR-SERVIDOR_EN.md) and [deploy](docs/DESPLEGAR-SERVIDOR_EN.md). Community Scripts has a separate procedure; do not mix the command blocks.

Parallel mode shares data and crontabs; it is not full isolation. Only one instance should be active. The adapter is at `server-extension/app/api/native/route.ts`; see [its README](server-extension/README_EN.md). Community Scripts replaces the compiled runtime in `/opt/cronmaster`, although documented separately because of its origin and checks.

## Status and security

The Android base was imported from the user's generator without building it in the publishing environment. The adapter includes `next().toDate().toISOString()`, validated with Cronmaster 2.4.0. The user upgraded the official installation to 2.4.1 and validated the extension against it. Parallel mode is a documented alternative, not a confirmed deployment.

The user reported a working Community Scripts deployment and supplied the steps. The generalized blocks with automatic version detection have not been executed in the publishing environment. Finding a tag does not guarantee compatibility with every release.

Do not publish .env, keys, cookies, backups or Android signing material. The server may run as root: use authentication and TLS. Examples do not replace data and crontab backups.

Original server: https://github.com/fccview/cronmaster (AGPL-3.0). Independent project, not affiliated. No additional license has been assigned to this repository.
