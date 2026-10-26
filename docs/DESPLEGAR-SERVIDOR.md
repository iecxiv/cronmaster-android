# Despliegue común del candidato compilado

Primero completa COMPILAR-SERVIDOR.md y selecciona las variables de tu modalidad en SERVIDOR-REEMPLAZO.md o SERVIDOR-PARALELO.md. Ejecuta desde SSH/consola, no como hijo del servicio que se va a detener. No hay cero downtime: se para brevemente la interfaz, no cron ni sus jobs.

El backup siguiente es de código y configuración, no un backup completo de datos/crontabs. Las exclusiones conservan .env, scripts, data y snippets. El rollback intenta recuperar código; no revierte migraciones ni garantiza recuperación ante fallo de disco o apagado.

Con SERVICE, LIVE y ENV_FILE exportadas por la guía elegida:

```bash
bash <<'BASH'
set -Eeuo pipefail
umask 077
: "${SERVICE:?Falta SERVICE}" "${LIVE:?Falta LIVE}" "${ENV_FILE:?Falta ENV_FILE}"
source /opt/cronmaster-native-build/candidate.env
test -f "$STAGE/server.js"
test -f "$ENV_FILE"
BACKUP="/opt/cronmaster-native-build/backups/$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p "$BACKUP/code"
EXCLUDES=(--exclude=/.env --exclude=/scripts --exclude=/data --exclude=/snippets)
HAD_CODE=0
CHANGED=0
[[ ! -f "$LIVE/server.js" ]] || HAD_CODE=1
recover() {
  status=$?
  trap - EXIT
  set +e
  if [[ "$status" != 0 ]]; then
    systemctl stop "$SERVICE"
    if [[ "$CHANGED" == 1 && "$HAD_CODE" == 1 ]]; then
      rsync -a --delete "${EXCLUDES[@]}" "$BACKUP/code/" "$LIVE/"
    fi
    if [[ "$HAD_CODE" == 1 ]]; then systemctl start "$SERVICE"; fi
    echo "Despliegue fallido. Revisa servicio y backup: $BACKUP"
  fi
  exit "$status"
}
trap recover EXIT
systemctl stop "$SERVICE"
if [[ "$HAD_CODE" == 1 ]]; then rsync -a "${EXCLUDES[@]}" "$LIVE/" "$BACKUP/code/"; fi
cp -a "$ENV_FILE" "$BACKUP/config.env"
CHANGED=1
rsync -a --delete "${EXCLUDES[@]}" "$STAGE/" "$LIVE/"
systemctl start "$SERVICE"
sleep 5
ENV_FILE="$ENV_FILE" node /root/cronmaster-android/scripts/probe-native.cjs
trap - EXIT
echo "Despliegue correcto. Backup: $BACKUP"
BASH
```

Si el arranque tarda más que la espera básica y el probe falla, se intentará restaurar código. Comprueba el resultado real en journalctl. En una primera instalación paralela no existe código anterior que recuperar: vuelve a la original según su guía.

Conserva la ruta del backup. Para recuperar MANUALMENTE código: revisa su versión y compatibilidad de datos, detén SERVICE, restaura BACKUP/code/ con rsync -a --delete y las mismas exclusiones y arranca SERVICE. No restaures config.env ni datos a ciegas.

El probe consulta capabilities autenticándose, no ejecuta jobs. Después valida Android y, si necesitas comprobar escritura, utiliza una tarea inofensiva. La salud general del servidor no es el health de cada job.
