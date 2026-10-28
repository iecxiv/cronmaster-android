# Cronmaster Android App

An **unofficial**, native Android client for **Cronmaster**, built with Kotlin and Jetpack Compose, including a server extension and installation and update guides.

## 

Includes tasks, scripts, backups, statistics, encrypted access via Android Keystore, icons, task health, and a terminal viewer. Green indicates a successful result, red indicates a failure, and gray indicates the absence of a verifiable result. Sessions are restored; the server can revoke credentials. HTTPS is recommended; OIDC is not implemented. Supports managing multiple servers.

## Build from source

Clone the main branch:

```bash
git clone --branch main https://github.com/iecxiv/cronmaster-android.git
cd cronmaster-android
```

Open the project in an Android Studio version compatible with Android Gradle Plugin 8.7.3 and Gradle 8.9. Install Android SDK Platform 35 and let Gradle synchronize the project. CI also installs Android SDK Build-Tools 35.0.0.

Select JDK 17 as the Gradle JDK and configure command-line JAVA_HOME consistently. Java/Kotlin source and bytecode compatibility are configured as Java 17; these settings are separate from the JDK used to run Gradle.

To select JDK 17 in Android Studio:

1. Open `File → Settings` on Windows/Linux, or `Android Studio → Settings` on macOS.
2. Go to `Build, Execution, Deployment → Build Tools → Gradle`.
3. In the `Gradle JDK` dropdown, select an installed JDK 17.
4. If JDK 17 is not available, choose `Download JDK…`, set the version to `17` and select Eclipse Temurin if listed, or another available JDK 17 distribution.
5. Ensure the selected Gradle JDK is the JDK 17 installation, not `JetBrains Runtime 25.0.3`.
6. Click `Apply → OK`.
7. Run `File → Sync Project with Gradle Files`.

Build and run on an Android device or emulator using API 26 or newer. For command-line builds, configure the Android SDK location using ANDROID_HOME or an uncommitted local.properties file containing sdk.dir.

To build a debug APK from the repository root:

```bash
# macOS / Linux
./gradlew :app:assembleDebug
```

```powershell
# Windows PowerShell
.\gradlew.bat :app:assembleDebug
```

The generated debug APK is placed under `app/build/outputs/apk/debug/app-debug.apk` after a successful build.

To run unit tests and build:

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

Plugin and dependency versions are centralized in `gradle/libs.versions.toml`. The Gradle distribution is configured in `gradle/wrapper/gradle-wrapper.properties`. Android SDK levels and Java/Kotlin targets remain in `app/build.gradle.kts`.

### First-time wrapper bootstrap

The workflow's `wrapper` job generates the genuine Gradle 8.9 wrapper in an empty temporary project if any wrapper file is missing, and commits only `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` and `gradle/wrapper/gradle-wrapper.properties` to main. It also records Gradle's published distribution SHA-256 checksum. No personal access token is required: only this job requests contents: write; the build job has read-only permissions.

For the first run after this migration, wait for that wrapper commit before cloning, or run `git pull --ff-only` in an unmodified checkout afterwards. If repository policies block the bot push, the wrapper job fails explicitly; the wrapper is not considered installed until its four files are visible in main. Normal later builds use the committed wrapper, not a system Gradle installation.

The build job checks out the exact commit output by the wrapper job, so it can test the generated wrapper in the same run without relying on a second push-triggered run. Repository rules may require an owner to allow the requested write permission. Do not weaken branch protection blindly.

El workflow genera un APK debug si supera las pruebas. Consulta su resultado en Actions. No se incluye APK release firmado ni se crea un tag o release.

## Servidor: elige una modalidad

- Instalación existente de Proxmox Community Scripts: [implementación](docs/SERVIDOR-COMMUNITY-SCRIPTS.md) y [actualización](docs/UPDATE-COMMUNITY-SCRIPTS.md). Detecta la versión de `/opt/cronmaster/package.json`, compila fuera de producción y conserva el servicio original.
- Conservar código original: [instalación paralela](docs/SERVIDOR-PARALELO.md) y [actualización](docs/UPDATE-PARALELO.md).
- Sustituir código activo: [reemplazo](docs/SERVIDOR-REEMPLAZO.md) y [actualización](docs/UPDATE-REEMPLAZO.md).
- Pasos comunes de las modalidades anteriores: [compilar](docs/COMPILAR-SERVIDOR.md) y [desplegar](docs/DESPLEGAR-SERVIDOR.md). Community Scripts tiene un procedimiento independiente; no mezcles los bloques.

La modalidad paralela comparte datos y crontabs, no es aislamiento total. Solo una instancia debe estar activa. El adaptador está en `server-extension/app/api/native/route.ts`; consulta también [su README](server-extension/README.md). Community Scripts sustituye el runtime compilado en `/opt/cronmaster`, aunque se documenta por separado por su origen y comprobaciones.

## Estado y seguridad

Base Android importada del generador del usuario, sin compilar en el entorno de publicación. El adaptador incluye la corrección `next().toDate().toISOString()` validada en Cronmaster 2.4.0. La instalación oficial 2.4.1 actualizada por el usuario, y se valido la extensión contra ella. La modalidad paralela es una alternativa documentada, no un despliegue confirmado.

El usuario ha comunicado un despliegue funcional de la extensión sobre una instalación de Community Scripts y ha aportado sus pasos. Los bloques generalizados con detección automática de versión no se han ejecutado en el entorno de publicación; detectar una etiqueta no garantiza compatibilidad con cualquier release.

No publiques .env, claves, cookies, backups ni firmas Android. El servidor puede operar como root; usa autenticación y TLS. Los ejemplos no sustituyen backups de datos y crontabs.

Servidor original: https://github.com/fccview/cronmaster (AGPL-3.0). Proyecto independiente, sin afiliación. No se ha asignado una licencia adicional a este repositorio.
