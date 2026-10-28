# Cronmaster Android App

Español | [English](README_EN.md)

Cliente Android nativo no oficial para Cronmaster, desarrollado con Kotlin y Jetpack Compose. Incluye una extensión del servidor y guías de instalación y actualización.

## Funciones

Tareas, scripts, backups, estadísticas, acceso cifrado mediante Android Keystore, iconos, estado de tareas y visor de terminal. Verde indica un resultado correcto; rojo, un fallo; gris, ausencia de un resultado verificable. Las sesiones se restauran, pero el servidor puede revocar credenciales. Se recomienda HTTPS. El cliente no implementa OIDC. Permite gestionar varios servidores.

## Compilar desde el código fuente

Clona la rama main:

```bash
git clone --branch main https://github.com/iecxiv/cronmaster-android.git
cd cronmaster-android
```

Abre el proyecto en una versión de Android Studio compatible con Android Gradle Plugin 8.7.3 y Gradle 8.9. Instala Android SDK Platform 35 y sincroniza Gradle. CI también instala Android SDK Build-Tools 35.0.0.

Selecciona JDK 17 como JDK de Gradle y configura JAVA_HOME de forma coherente para la línea de comandos. La compatibilidad del código fuente y del bytecode Java/Kotlin está configurada en Java 17; es independiente del JDK que ejecuta Gradle.

Para seleccionar JDK 17 en Android Studio:

1. Abre `File → Settings` en Windows/Linux o `Android Studio → Settings` en macOS.
2. Ve a `Build, Execution, Deployment → Build Tools → Gradle`.
3. En `Gradle JDK`, selecciona un JDK 17 instalado.
4. Si no aparece, selecciona `Download JDK…`, versión `17`, y Eclipse Temurin si está disponible u otra distribución JDK 17.
5. Comprueba que no has seleccionado `JetBrains Runtime 25.0.3`.
6. Pulsa `Apply → OK`.
7. Ejecuta `File → Sync Project with Gradle Files`.

Compila y ejecuta en un dispositivo o emulador con API 26 o posterior. Para compilar por terminal, configura ANDROID_HOME o un archivo local.properties no versionado que contenga sdk.dir.

APK de depuración, desde la raíz:

```bash
# macOS / Linux
./gradlew :app:assembleDebug
```

```powershell
# Windows PowerShell
.\gradlew.bat :app:assembleDebug
```

Después de una compilación correcta, el APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

Pruebas unitarias y compilación:

```bash
# macOS / Linux
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

```powershell
# Windows PowerShell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Con ADB configurado:

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Las versiones de plugins y dependencias están en `gradle/libs.versions.toml`. La distribución de Gradle está en `gradle/wrapper/gradle-wrapper.properties`. Los niveles de Android SDK y los destinos Java/Kotlin permanecen en `app/build.gradle.kts`.

### Inicialización del wrapper

Si falta algún archivo del wrapper, el trabajo `wrapper` del workflow genera el wrapper auténtico de Gradle 8.9 en un proyecto temporal vacío y publica en main únicamente `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` y `gradle/wrapper/gradle-wrapper.properties`. También registra el SHA-256 publicado de la distribución. No requiere un token personal: solo este trabajo solicita `contents: write`; el trabajo de compilación tiene permisos de lectura.

En la primera ejecución tras esta migración, espera al commit del wrapper antes de clonar o ejecuta `git pull --ff-only` después, en un checkout sin modificaciones. Si las políticas bloquean la publicación del bot, el trabajo falla explícitamente. El wrapper no se considera instalado hasta que sus cuatro archivos estén en main. Las compilaciones posteriores usan el wrapper versionado, no Gradle del sistema.

El trabajo de compilación descarga el commit exacto indicado por el trabajo del wrapper, permitiendo probarlo en la misma ejecución sin depender de otro evento push. Las reglas pueden exigir autorización del propietario para esos permisos. No debilites la protección de ramas sin revisar sus implicaciones.

El workflow genera un APK debug si supera las pruebas. Consulta su resultado en Actions. No incluye un APK release firmado ni crea tags o releases.

## Servidor: elige una modalidad

- Instalación existente de Proxmox Community Scripts: [implementación](docs/SERVIDOR-COMMUNITY-SCRIPTS.md) y [actualización](docs/UPDATE-COMMUNITY-SCRIPTS.md). Detecta la versión de `/opt/cronmaster/package.json`, compila fuera de producción y conserva el servicio original.
- Conservar código original: [instalación paralela](docs/SERVIDOR-PARALELO.md) y [actualización](docs/UPDATE-PARALELO.md).
- Sustituir código activo: [reemplazo](docs/SERVIDOR-REEMPLAZO.md) y [actualización](docs/UPDATE-REEMPLAZO.md).
- Pasos comunes de las modalidades anteriores: [compilar](docs/COMPILAR-SERVIDOR.md) y [desplegar](docs/DESPLEGAR-SERVIDOR.md). Community Scripts tiene un procedimiento independiente; no mezcles los bloques.

La modalidad paralela comparte datos y crontabs; no es aislamiento total. Solo una instancia debe estar activa. El adaptador está en `server-extension/app/api/native/route.ts`; consulta [su README](server-extension/README.md). Community Scripts sustituye el runtime compilado en `/opt/cronmaster`, aunque se documenta por separado por su origen y comprobaciones.

## Estado y seguridad

La base Android se importó del generador del usuario sin compilarla en el entorno de publicación. El adaptador incluye `next().toDate().toISOString()`, validado en Cronmaster 2.4.0. El usuario actualizó la instalación oficial a 2.4.1 y validó la extensión contra ella. La modalidad paralela es una alternativa documentada, no un despliegue confirmado.

El usuario ha comunicado un despliegue funcional sobre Community Scripts y ha aportado sus pasos. Los bloques generalizados con detección automática de versión no se han ejecutado en el entorno de publicación. Detectar una etiqueta no garantiza compatibilidad con cualquier release.

No publiques .env, claves, cookies, backups ni firmas Android. El servidor puede operar como root: utiliza autenticación y TLS. Los ejemplos no sustituyen backups de datos y crontabs.

Servidor original: https://github.com/fccview/cronmaster (AGPL-3.0). Proyecto independiente, sin afiliación. No se ha asignado una licencia adicional a este repositorio.
