#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export TERM=xterm NO_COLOR=1 CI=true
LIVE=/opt/cronmaster
ROOT=/opt/cronmaster-native-build
AUTO="$ROOT/automatic-update"
REPO=/root/cronmaster-android
SERVICE=cronmaster.service
MAIN_LOG=/var/log/update_cronmaster.log
NATIVE_LOG=/var/log/update_cronmaster_native.log
ASCII=/root/.ascii_cronmaster
UPSTREAM=https://github.com/fccview/cronmaster.git

[[ $EUID == 0 ]] || { echo 'Ejecutar como root.' >&2; exit 1; }
for cmd in node flock; do command -v "$cmd" >/dev/null; done
[[ $# == 0 || ( $# == 1 && $1 == --retry-native ) ]] || {
  echo "Uso: $0 [--retry-native]" >&2; exit 2;
}
install -d -m 700 "$AUTO"
exec 9>/run/lock/cronmaster-update-chain.lock
flock -n 9 || { echo 'Ya hay una actualización en curso.' >&2; exit 75; }
exec 8>"$ROOT/build.lock"
flock -n 8 || { echo 'Hay una compilación/despliegue en curso.' >&2; exit 75; }
version() { node -p 'require("/opt/cronmaster/package.json").version'; }
valid_version() { [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-+][0-9A-Za-z.-]+)?$ ]]; }

if [[ $# == 1 ]]; then
  test -s "$AUTO/pending.json"
  mapfile -t pending < <(node -e '
    const p=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));
    if(typeof p.from!=="string"||typeof p.to!=="string") process.exit(1);
    console.log(p.from); console.log(p.to);
  ' "$AUTO/pending.json")
  [[ ${#pending[@]} == 2 ]] || exit 1
  BEFORE=${pending[0]}; AFTER=${pending[1]}
  valid_version "$BEFORE"; valid_version "$AFTER"
  [[ "$(version)" == "$AFTER" ]] || { echo 'La versión pendiente ya no coincide con la instalada.' >&2; exit 1; }
else
  # Evita actualizar el principal si faltan herramientas/archivos básicos de native.
  for cmd in git corepack rsync curl timeout sha256sum awk perl grep tail systemctl; do command -v "$cmd" >/dev/null; done
  test -x /usr/local/bin/update_cronmaster
  test -s "$REPO/server-extension/app/api/native/route.ts"
  test -s "$REPO/scripts/probe-native.cjs"
  test -f "$LIVE/server.js"
  test -f "$LIVE/.env"
  BEFORE=$(version)
  valid_version "$BEFORE"
  rc=0
  /usr/local/bin/update_cronmaster >"$MAIN_LOG" 2>&1 || rc=$?
  if [[ -r "$ASCII" ]]; then cat "$ASCII"; printf '\n'; fi
  perl -CSDA -pe '
    s/\r/\n/g;
    s/\e\[[0-9;?]*[[:alpha:]]//g;
    s/[⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏]\s*Checking for update: cronmaster//g;
    s/[⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏]//g;
  ' "$MAIN_LOG" | grep -E \
    'No update available: cronmaster|Update complete|updated|ERROR|Error|Failed|failed' \
    | tail -n 1 || true
  if [[ $rc != 0 ]]; then
    echo "El actualizador principal falló ($rc). No se ejecuta native." >&2
    exit "$rc"
  fi
  AFTER=$(version)
  valid_version "$AFTER"
  if [[ "$BEFORE" == "$AFTER" ]]; then
    echo "Sin cambio de versión ($AFTER): no se regenera la extensión."
    if [[ -s "$AUTO/pending.json" ]]; then
      echo "AVISO: hay un native pendiente; revisar y ejecutar $0 --retry-native manualmente."
    fi
    exit 0
  fi
  node -e 'console.log(JSON.stringify({from:process.argv[1],to:process.argv[2]}))' "$BEFORE" "$AFTER" > "$AUTO/pending.json.tmp"
  mv "$AUTO/pending.json.tmp" "$AUTO/pending.json"
fi

echo "Reconstruyendo extensión para Cronmaster $AFTER. Log: $NATIVE_LOG"
exec 3>&1
exec >>"$NATIVE_LOG" 2>&1
printf '\n=== %s | %s -> %s ===\n' "$(date -Is)" "$BEFORE" "$AFTER"
STOPPED=0
CHANGED=0
BACKUP=
WORK=
EXCLUDES=(--exclude='/.env*' --exclude=/scripts --exclude=/data --exclude=/snippets)
finish() {
  local rc=$?
  trap - EXIT
  set +e
  if [[ $rc != 0 ]]; then
    echo "ERROR native, código $rc"
    if [[ "$CHANGED" == 1 ]]; then
      if systemctl stop "$SERVICE"; then
        if rsync -a --delete "${EXCLUDES[@]}" "$BACKUP/app/" "$LIVE/"; then
          echo 'Restaurado el código posterior al update oficial; puede no incluir native.'
        else
          echo 'ERROR CRÍTICO: falló la restauración del código.'
        fi
      else
        echo 'ERROR CRÍTICO: no se pudo detener el servicio para restaurar.'
      fi
    fi
    if [[ "$STOPPED" == 1 ]]; then systemctl start "$SERVICE" || echo 'ERROR CRÍTICO: falló el arranque.'; fi
    echo "Backup: ${BACKUP:-no creado}; build: ${WORK:-no iniciado}"
    echo "ERROR native. Revisa $NATIVE_LOG. Reintento: $0 --retry-native" >&3
  else
    echo "Extensión desplegada y comprobada para $AFTER. Backup: $BACKUP" >&3
  fi
  exit "$rc"
}
trap finish EXIT
for cmd in git corepack rsync curl timeout sha256sum awk systemctl; do command -v "$cmd" >/dev/null; done
test -s "$REPO/server-extension/app/api/native/route.ts"
test -s "$REPO/scripts/probe-native.cjs"
systemctl is-active --quiet "$SERVICE"
PORT=$(node -e '
  const fs=require("fs"); const {parseEnv}=require("node:util");
  const e=parseEnv(fs.readFileSync("/opt/cronmaster/.env","utf8"));
  const p=e.PORT||"3000";
  if(!/^\d+$/.test(p)||+p<1||+p>65535) throw Error("PORT inválido");
  if(!e.API_KEY&&!e.AUTH_PASSWORD) throw Error("Falta API_KEY o AUTH_PASSWORD para el probe");
  process.stdout.write(p);
')
BASELINE=$(sha256sum "$LIVE/server.js" | awk '{print $1}')
TAG=
for candidate in "$AFTER" "v$AFTER"; do
  if git ls-remote --exit-code --tags "$UPSTREAM" "refs/tags/$candidate" >/dev/null; then TAG=$candidate; break; fi
done
[[ -n "$TAG" ]] || { echo "No existe etiqueta para $AFTER; no se usa main/latest."; exit 1; }
WORK=$(mktemp -d "$AUTO/build-$AFTER-XXXXXXXX")
SOURCE="$WORK/source"
STAGE="$WORK/stage"
git clone --depth 1 --branch "$TAG" "$UPSTREAM" "$SOURCE"
[[ "$(node -p "require('$SOURCE/package.json').version")" == "$AFTER" ]]
install -d "$SOURCE/app/api/native"
install -m 644 "$REPO/server-extension/app/api/native/route.ts" "$SOURCE/app/api/native/route.ts"
install -m 600 "$REPO/scripts/probe-native.cjs" "$WORK/probe-native.cjs"
sha256sum "$SOURCE/app/api/native/route.ts" "$WORK/probe-native.cjs" > "$WORK/adapter-checksums.txt"
cd "$SOURCE"
git rev-parse HEAD > "$WORK/upstream-commit"
test -f yarn.lock
corepack prepare yarn@1.22.22 --activate
env NODE_ENV=development corepack yarn install --frozen-lockfile
NODE_OPTIONS=--max-old-space-size=4096 corepack yarn tsc --noEmit 2>&1 | tee "$WORK/typecheck.log"
NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 NODE_OPTIONS=--max-old-space-size=4096 corepack yarn build 2>&1 | tee "$WORK/build.log"
test -f .next/standalone/server.js
test -f .next/server/app/api/native/route.js
test -d .next/static
test -d public
mkdir -p "$STAGE"
cp -a .next/standalone/. "$STAGE/"
mkdir -p "$STAGE/.next/static" "$STAGE/public"
cp -a .next/static/. "$STAGE/.next/static/"
cp -a public/. "$STAGE/public/"
if [[ -d app/_scripts ]]; then mkdir -p "$STAGE/app/_scripts"; cp -a app/_scripts/. "$STAGE/app/_scripts/"; fi
for name in .env .env.local .env.production .env.production.local scripts data snippets; do rm -rf -- "$STAGE/$name"; done
test -f "$STAGE/server.js"
test -d "$STAGE/node_modules"
[[ "$(node -p "require('$STAGE/package.json').version")" == "$AFTER" ]]
[[ "$(version)" == "$AFTER" ]]
[[ "$(sha256sum "$LIVE/server.js" | awk '{print $1}')" == "$BASELINE" ]]
systemctl is-active --quiet "$SERVICE"
BACKUP=$(mktemp -d "$AUTO/backup-XXXXXXXX")
systemctl stop "$SERVICE"
STOPPED=1
mkdir "$BACKUP/app"
cp -a "$LIVE/." "$BACKUP/app/"
systemctl cat "$SERVICE" > "$BACKUP/service.txt"
CHANGED=1
rsync -a --delete "${EXCLUDES[@]}" "$STAGE/" "$LIVE/"
systemctl start "$SERVICE"
READY=0
for attempt in $(seq 1 60); do
  if curl -fsS --max-time 3 "http://127.0.0.1:$PORT/api/health" >/dev/null; then READY=1; break; fi
  sleep 2
done
[[ "$READY" == 1 ]]
STATUS=$(curl -sS --max-time 10 -o "$WORK/native-unauth.json" -w '%{http_code}' \
  -H 'Content-Type: application/json' --data '{"op":"capabilities","args":{}}' \
  "http://127.0.0.1:$PORT/api/native")
[[ "$STATUS" == 401 ]]
timeout 60s env ENV_FILE="$LIVE/.env" node "$WORK/probe-native.cjs"
systemctl is-active --quiet "$SERVICE"
printf '%s\n' "$BACKUP" > "$AUTO/last-backup.tmp"
mv "$AUTO/last-backup.tmp" "$AUTO/last-backup"
printf '%s\n' "$AFTER" > "$AUTO/last-native-version.tmp"
mv "$AUTO/last-native-version.tmp" "$AUTO/last-native-version"
rm -f "$AUTO/pending.json"
echo "OK. Build: $WORK; backup: $BACKUP"
