# Shared deployment of the built candidate

[Español](DESPLEGAR-SERVIDOR.md) | English

First complete [COMPILAR-SERVIDOR](COMPILAR-SERVIDOR_EN.md) and select your mode's variables in [SERVIDOR-REEMPLAZO](SERVIDOR-REEMPLAZO_EN.md) or [SERVIDOR-PARALELO](SERVIDOR-PARALELO_EN.md). Run from SSH/console, not as a child of the service being stopped. This is not zero downtime: the UI stops briefly, not cron or its jobs.

The backup below covers code and configuration, not a complete data/crontab backup. Exclusions preserve .env, scripts, data and snippets. Rollback attempts to recover code; it does not undo migrations or guarantee recovery after disk failure or power loss.

With SERVICE, LIVE and ENV_FILE exported by your chosen guide:

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

If startup takes longer than the basic wait and the probe fails, code restoration will be attempted. Check the actual result in journalctl. On a first parallel installation, no previous code exists to restore: return to the original service using its guide.

Keep the backup path. To restore code MANUALLY: review its version and data compatibility, stop SERVICE, restore BACKUP/code/ with rsync -a --delete and the same exclusions, then start SERVICE. Do not blindly restore config.env or data.

The probe authenticates and queries capabilities; it does not run jobs. Then validate Android and, if write testing is needed, use a harmless task. Overall server health is separate from each job's health. Shell messages remain unchanged.
