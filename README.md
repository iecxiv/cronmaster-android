# CronMaster Android App
Cliente **no oficial** Android nativo de **Cronmaster** en Kotlin y Jetpack Compose, con extensión de servidor y guías de instalación y actualización.

An **unofficial**, native Android client for **Cronmaster**, built with Kotlin and Jetpack Compose, including a server extension and installation and update guides.

## Android v0.2.0

Namespace y applicationId: `iecxiv.cronmaster`. Incluye tareas, scripts, backups, estadísticas, acceso cifrado con Android Keystore, iconos, salud de tareas y visor terminal. Verde indica resultado correcto, rojo fallo y gris ausencia de resultado verificable. La sesión se restaura; el servidor puede revocar credenciales. HTTPS recomendado; OIDC no implementado.

Requiere JDK 17 y Android SDK 35:

```bash
bash preparar-gradle.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

El workflow genera un APK debug si supera las pruebas. Consulta su resultado en Actions; publicar fuentes no certifica una compilación correcta. No se incluye APK release firmado ni se crea un tag o release por utilizar la denominación v0.2.0.

## Servidor: elige una modalidad

- Conservar código original: [instalación paralela](docs/SERVIDOR-PARALELO.md) y [actualización](docs/UPDATE-PARALELO.md).
- Sustituir código activo: [reemplazo](docs/SERVIDOR-REEMPLAZO.md) y [actualización](docs/UPDATE-REEMPLAZO.md).
- Pasos comunes: [compilar](docs/COMPILAR-SERVIDOR.md) y [desplegar](docs/DESPLEGAR-SERVIDOR.md).

La modalidad paralela comparte datos y crontabs, no es aislamiento total. Solo una instancia debe estar activa. El adaptador está en `server-extension/app/api/native/route.ts`.

## Estado y seguridad

Base Android importada del generador v0.2.0 del usuario, sin compilar en el entorno de publicación. El adaptador incluye la corrección `next().toDate().toISOString()` validada en Cronmaster 2.4.0. La instalación oficial 2.4.1 fue comunicada por el usuario, pero eso no valida la extensión contra ella. La modalidad paralela es una alternativa documentada, no un despliegue confirmado.

No publiques .env, claves, cookies, backups ni firmas Android. El servidor puede operar como root; usa autenticación y TLS. Los ejemplos no sustituyen backups de datos y crontabs.

Servidor original: https://github.com/fccview/cronmaster (AGPL-3.0). Proyecto independiente, sin afiliación. No se ha asignado una licencia adicional a este repositorio.
