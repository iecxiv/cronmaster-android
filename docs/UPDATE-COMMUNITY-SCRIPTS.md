# Actualizar la extensión en una instalación de Community Scripts

Aplica exclusivamente a la [variante Community Scripts](SERVIDOR-COMMUNITY-SCRIPTS.md): `/opt/cronmaster` y `cronmaster.service`. No modifica las guías de sustitución ni de instalación paralela.

## Reaplicar la extensión a la misma versión

1. Haz snapshot y backup de datos y crontabs. Revisa que no haya un actualizador oficial concurrente.
2. Actualiza conscientemente tu checkout de cronmaster-android a la revisión deseada, conservando cambios locales.
3. Repite la guía específica desde la detección de versión. Cada compilación crea un directorio nuevo; no borra los anteriores ni descarga latest.
4. La versión se lee de `/opt/cronmaster/package.json`; se valida una etiqueta exacta y la versión del código descargado. No es necesario editar a mano el número de versión.
5. Compila y prepara staging mientras el servicio actual sigue activo. Despliega únicamente si pasan TypeScript y build.
6. Comprueba health, rechazo sin credenciales, capabilities autenticadas y lectura de tareas. Conserva el backup y verifica Android.

## Cambiar también la versión de Cronmaster

La reaplicación anterior no actualiza Cronmaster a una release nueva. Si usas el actualizador de Community Scripts, trátalo como una operación separada con ventana de mantenimiento, revisión de migraciones y backups. Puede sobrescribir el runtime y quitar `/api/native`. Después vuelve a detectar la versión realmente instalada y compila la extensión para esa etiqueta. No reutilices un staging antiguo.

No encadenes actualizadores sin supervisión ni ejecutes dos despliegues simultáneos. El bloqueo de la guía solo coordina ejecuciones de ese procedimiento, no bloquea el actualizador externo.

La comprobación previa al despliegue rechaza un cambio de versión durante la compilación; no detecta todas las modificaciones concurrentes que mantengan el mismo número. Si falta la etiqueta, no cambies automáticamente a main ni a latest: detente y revisa el caso.

El rollback de la guía restaura código y preserva los datos actuales. No revierte migraciones, crontabs, efectos de tareas ni cambios hechos por una actualización oficial. Revisa compatibilidad antes de volver a una versión anterior.

## Cronjob opcional: extensión después del actualizador principal de Proxmox LXC

[English](UPDATE-COMMUNITY-SCRIPTS_EN.md#optional-cronjob-extension-after-the-main-proxmox-lxc-updater)

El script está en [scripts/update-cronmaster-with-native.sh](../scripts/update-cronmaster-with-native.sh). Es una alternativa encadenada al procedimiento manual anterior, para validar primero con una actualización supervisada y después monitorizar. No modifica las otras modalidades ni se instala automáticamente.

### Disposición concreta

- Aplicación activa: `/opt/cronmaster`, ejecutada por `cronmaster.service`.
- Compilaciones/backups de este caso: `/opt/cronmaster-native-build`. Esa carpeta NO es una segunda instancia.
- El script crea únicamente sus archivos nuevos bajo `/opt/cronmaster-native-build/automatic-update`, con builds y backups de nombres únicos. No sobrescribe los históricos, candidate.env ni last-backup de la raíz.
- La guía manual de instalación usa otro ROOT, `/opt/cronmaster-native-community`. No mezcles sus archivos de control o backups con los de este script ni supongas que comparten bloqueo.

### Requisitos e instalación

Ejecuta dentro del LXC como root. Necesita Node 22/Corepack, git, rsync, curl, flock, timeout, sha256sum, awk, perl, grep, tail y systemctl. Debe existir `/usr/local/bin/update_cronmaster` y un checkout revisado en `/root/cronmaster-android` con el adaptador y `scripts/probe-native.cjs`. No instala paquetes, cambia Node ni hace git pull automático. Utiliza Yarn 1.22.22 mediante Corepack. El heap de 4 GiB no limita toda la memoria del build.

#### Paso previo: preparar el checkout y comprobar los archivos

Tener compilaciones en `/opt/cronmaster-native-build` no implica que exista `/root/cronmaster-android`. Antes de instalar o ejecutar el cronjob, ejecuta este bloque. Clona dev únicamente si el destino no existe; si existe, no lo sobrescribe ni hace git pull. Comprueba que el adaptador y el probe no estén vacíos y muestra el commit del checkout.

```bash
bash <<'BASH'
set -Eeuo pipefail

REPO=/root/cronmaster-android

if [[ ! -e "$REPO" ]]; then
    git clone --depth 1 --branch dev \
        https://github.com/iecxiv/cronmaster-android.git \
        "$REPO"
fi

for file in \
    "$REPO/server-extension/app/api/native/route.ts" \
    "$REPO/scripts/probe-native.cjs"; do
    if [[ ! -s "$file" ]]; then
        echo "ERROR: falta o está vacío: $file"
        echo "El directorio existente no se ha sobrescrito."
        exit 1
    fi
done

echo 'OK: adaptador y probe disponibles.'
git -C "$REPO" rev-parse --short HEAD
BASH
```

Copia solo el contenido del bloque de código. La URL de git clone debe ser texto literal, sin corchetes ni paréntesis: no pegues un enlace Markdown con la forma `[URL](URL)` dentro del comando. Este paso descarga el checkout si falta, pero no ejecuta el actualizador principal, no compila ni reinicia Cronmaster.

Si faltan esos archivos, el script de actualización puede terminar inmediatamente con código 1 sin salida, porque sus comprobaciones `test -s` están antes del actualizador principal. Si el directorio ya existe pero está incompleto o no es un checkout Git válido, detente y revisa su contenido; este bloque no lo repara ni lo elimina automáticamente.

Revisa la versión del checkout y los cambios locales antes de actualizarlo. Instala el script desde él:

```bash
install -m 700 /root/cronmaster-android/scripts/update-cronmaster-with-native.sh /usr/local/sbin/update-cronmaster-with-native.sh
bash -n /usr/local/sbin/update-cronmaster-with-native.sh
```

Haz snapshot y backup independiente de configuración, datos y crontabs; comprueba espacio para fuente, dependencias, staging y copia del runtime. Verifica `systemctl show cronmaster.service -p WorkingDirectory -p ExecStart`: esta receta presupone `/opt/cronmaster`, el Node esperado y PORT del `.env` (3000 si no figura). Overrides de configuración/puerto requieren adaptación. El probe requiere API_KEY o AUTH_PASSWORD; OIDC solo no basta.

### Sustituir el job anterior, no duplicarlo

Mantén la periodicidad de tu job y sustituye su cuerpo por:

```bash
/usr/local/sbin/update-cronmaster-with-native.sh
```

La cadena ya llama a `/usr/local/bin/update_cronmaster`: no dejes además el job original ni programes un segundo job con retraso fijo. Conserva `/var/log/update_cronmaster.log`, el banner `/root/.ascii_cronmaster` y el filtro del resumen.

Usa cron del sistema dentro del LXC (`crontab -e` de root) o un planificador externo al cgroup de `cronmaster.service`. NO lo lances desde una tarea dependiente del servicio que el despliegue detiene: podría matar su propio actualizador. En crontab de root hay cinco campos de horario antes del comando; en /etc/cron.d se añade el usuario root. No se propone ni instala un horario nuevo.

### Condición exacta

- Principal devuelve error: no ejecuta native y conserva su código de salida.
- Principal termina correctamente y `version` en `/opt/cronmaster/package.json` no cambia: no clona, instala dependencias, compila ni reinicia para native. Puede crear carpetas de control y locks.
- Principal termina correctamente y cambia la versión: clona la etiqueta de esa nueva versión, incorpora el adaptador local y exige TypeScript y build correctos. No usa main/latest como sustituto de una etiqueta ausente.
- Una reinstalación con el mismo número no dispara native. El texto del log no decide si se compila.

La extensión puede faltar mientras se compila, desde que el actualizador oficial la sobrescribe. Se despliega sobre `/opt/cronmaster` y reinicia únicamente `cronmaster.service`. Conserva `.env*`, scripts, data y snippets; revisa otros archivos locales y adapta EXCLUDES antes de usar rsync --delete. No escribe crontabs ni modifica la unidad. Detener la web no detiene cron entero; el backup no es snapshot consistente de datos en escritura.

### Fallos, logs y reintento

Log principal: `/var/log/update_cronmaster.log` (se renueva). Log native: `/var/log/update_cronmaster_native.log` (se añade). En `automatic-update`, pending.json registra un native pendiente; last-backup apunta al último despliegue exitoso. Un fallo imprime su backup propio en el log.

Si falla la compilación, no reemplaza la aplicación activa. Si falla durante el despliegue, intenta restaurar el backup tomado DESPUÉS del update oficial: puede contener la release nueva SIN native. No vuelve automáticamente a la release anterior ni revierte migraciones. La recuperación puede fallar; revisa log, servicio y backup.

Una ejecución posterior sin cambio de versión no recompila automáticamente un pendiente; avisa. Tras revisar/corregir el problema:

```bash
/usr/local/sbin/update-cronmaster-with-native.sh --retry-native
```

El reintento no ejecuta el actualizador principal y exige que siga instalada la versión pendiente. También puede detener el servicio: usa consola/planificador externo.

La cadena usa `/run/lock/cronmaster-update-chain.lock` y `/opt/cronmaster-native-build/build.lock`. No coordina automáticamente otros actualizadores ni la guía con otro ROOT; evita cualquier vía concurrente. Recomprueba versión y hash de server.js antes de desplegar, sin garantizar detectar todos los cambios.

Comprueba health, HTTP 401 sin credenciales, capabilities autenticadas y servicio activo; no ejecuta tareas ni prueba todas las escrituras/proxy/Android. No ejecuta .env como shell ni imprime credenciales; no uses set -x. Los backups pueden contener secretos. No hay limpieza/logrotate automáticos: vigila disco y configura retención.

El script entregado pasó bash -n y pruebas simuladas de sin update, update correcto, error del principal, reintento válido y rechazo de reintento de otra versión. No se ha validado aquí una compilación o despliegue real. Prueba una actualización supervisada antes de dejarlo desatendido.
