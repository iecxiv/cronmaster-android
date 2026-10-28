# Extensión sobre Cronmaster instalado con Proxmox Community Scripts

Tercera variante, basada en el despliegue funcional comunicado por el usuario. Se aplica a la instalación existente en `/opt/cronmaster` cuyo `cronmaster.service` ejecuta `server.js`. Compila una copia separada y sustituye únicamente el runtime activo conservando configuración y datos. No crea otro servicio ni otra instancia. No cambia las guías de sustitución o instalación paralela. Los bloques generalizados siguientes requieren validación en tu servidor.

Origen de la instalación: https://community-scripts.org/scripts/cronmaster y https://github.com/community-scripts/ProxmoxVE/blob/main/tools/addon/cronmaster.sh. No vuelvas a ejecutar el instalador para añadir la extensión.

## 1. Comprobar el entorno y preparar copias

Ejecuta como root desde SSH o consola, no desde un proceso hijo de Cronmaster. Haz antes snapshot y backup de datos, configuración y crontabs de todos los usuarios. Pausa conscientemente cualquier actualización automática durante todo el procedimiento. Detener la interfaz no detiene cron ni sus tareas; un backup realizado mientras escriben no es una instantánea consistente.

```bash
systemctl cat cronmaster.service
systemctl status cronmaster.service --no-pager
node --version
node -p 'require("/opt/cronmaster/package.json").version'
```

Esta receta presupone Node 22 con Corepack, `server.js`, `.env` y el directorio de trabajo `/opt/cronmaster`. Verifica que el servicio usa el mismo Node y la misma configuración. Si hay overrides de PORT, otra ruta, otro usuario, contenedores o datos fuera de scripts/data/snippets, adapta y revisa antes de continuar. No cambia Node ni la unidad systemd. Instala las herramientas ausentes mediante el gestor de paquetes de tu sistema: git, rsync, curl y flock.

Prepara un checkout del cliente y su adaptador, o usa uno existente tras revisar sus cambios locales:

```bash
git clone --branch dev https://github.com/iecxiv/cronmaster-android.git /root/cronmaster-android
```

Si el destino ya existe, no lo sobrescribas. Verifica la revisión que vas a desplegar. No copies `.env` al código fuente de compilación.

## 2. Detectar versión, descargar, incorporar la ruta y compilar

Lee la versión instalada, no latest. Busca primero una etiqueta exacta y después la variante con prefijo v; si ninguna existe, aborta. Detectar versión y etiqueta no garantiza compatibilidad: TypeScript y build deben finalizar correctamente. El heap de 4 GiB no limita el consumo total de la compilación.

```bash
bash <<'BASH'
set -Eeuo pipefail
umask 077
[[ $EUID == 0 ]]
for cmd in node corepack git rsync curl flock; do command -v "$cmd" >/dev/null; done
test -f /opt/cronmaster/server.js
test -f /opt/cronmaster/.env
ROOT=/opt/cronmaster-native-community
install -d -m 700 "$ROOT"
exec 9>"$ROOT/build.lock"
flock -n 9
VERSION="$(node -p 'require("/opt/cronmaster/package.json").version')"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-+][0-9A-Za-z.-]+)?$ ]]
UPSTREAM=https://github.com/fccview/cronmaster.git
TAG=
for candidate in "$VERSION" "v$VERSION"; do
  if git ls-remote --exit-code --tags "$UPSTREAM" "refs/tags/$candidate" >/dev/null; then
    TAG="$candidate"; break
  fi
done
[[ -n "$TAG" ]] || { echo "No existe etiqueta para $VERSION; no se usa latest."; exit 1; }
WORK="$(mktemp -d "$ROOT/build-$VERSION-XXXXXXXX")"
SOURCE="$WORK/source"
STAGE="$WORK/stage"
git clone --depth 1 --branch "$TAG" "$UPSTREAM" "$SOURCE"
[[ "$(node -p "require('$SOURCE/package.json').version")" == "$VERSION" ]]
cd "$SOURCE"
git rev-parse HEAD | tee "$WORK/upstream-commit"
test -f yarn.lock
install -d app/api/native
install -m 644 /root/cronmaster-android/server-extension/app/api/native/route.ts app/api/native/route.ts
corepack prepare yarn@1.22.22 --activate
env NODE_ENV=development corepack yarn install --frozen-lockfile
NODE_OPTIONS=--max-old-space-size=4096 corepack yarn tsc --noEmit 2>&1 | tee "$WORK/typecheck.log"
NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 NODE_OPTIONS=--max-old-space-size=4096 corepack yarn build 2>&1 | tee "$WORK/build.log"
test -f .next/standalone/server.js
test -d .next/static
test -d public
mkdir -p "$STAGE"
cp -a .next/standalone/. "$STAGE/"
mkdir -p "$STAGE/.next/static" "$STAGE/public"
cp -a .next/static/. "$STAGE/.next/static/"
cp -a public/. "$STAGE/public/"
for name in .env scripts data snippets; do rm -rf -- "$STAGE/$name"; done
test -f "$STAGE/server.js"
test -d "$STAGE/node_modules"
[[ "$(node -p "require('$STAGE/package.json').version")" == "$VERSION" ]]
printf 'VERSION=%q\nSTAGE=%q\n' "$VERSION" "$STAGE" > "$ROOT/candidate.env.tmp"
mv "$ROOT/candidate.env.tmp" "$ROOT/candidate.env"
echo "Candidato preparado: $STAGE"
echo "La instalación activa no se ha modificado."
BASH
```

La ruta proviene directamente del archivo corregido del repositorio; no pegues el heredoc de un generador dentro de route.ts. Copiarla solo a `/opt/cronmaster/app/api/native` no recompila el runtime. Si falla la compilación, no despliegues: revisa los logs del directorio build mostrado. candidate.env se genera localmente como root y no contiene secretos; no lo reemplaces por un archivo de terceros.

## 3. Desplegar con backup y recuperación automática

Este bloque mantiene `/opt/cronmaster/.env`, scripts, data y snippets. No modifica crontabs ni instala actualizadores. La copia completa de la instalación puede contener secretos y ocupar bastante disco: comprueba espacio y permisos antes. No compartas los backups. Revisa archivos locales adicionales antes de utilizar rsync --delete.

```bash
bash <<'BASH'
set -Eeuo pipefail
umask 077
[[ $EUID == 0 ]]
ROOT=/opt/cronmaster-native-community
exec 9>"$ROOT/build.lock"
flock -n 9
source "$ROOT/candidate.env"
LIVE=/opt/cronmaster
SERVICE=cronmaster.service
test -f "$LIVE/server.js"
test -f "$LIVE/.env"
test -f "$STAGE/server.js"
[[ "$(node -p 'require("/opt/cronmaster/package.json").version')" == "$VERSION" ]]
systemctl is-active --quiet "$SERVICE"
PORT="$(node -e 'const fs=require("fs"); const {parseEnv}=require("node:util"); const p=parseEnv(fs.readFileSync("/opt/cronmaster/.env","utf8")).PORT||"3000"; if(!/^\d+$/.test(p)||+p<1||+p>65535) throw Error("PORT inválido"); process.stdout.write(p)')"
BACKUP="$(mktemp -d "$ROOT/backup-XXXXXXXX")"
EXCLUDES=(--exclude=/.env --exclude=/scripts --exclude=/data --exclude=/snippets)
STOPPED=0
CHANGED=0
recover() {
  code=$?
  trap - EXIT
  set +e
  if [[ "$code" != 0 ]]; then
    if [[ "$CHANGED" == 1 ]]; then
      systemctl stop "$SERVICE"
      rsync -a --delete "${EXCLUDES[@]}" "$BACKUP/app/" "$LIVE/"
      result=$?
      echo "Resultado de restaurar código: $result"
    fi
    if [[ "$STOPPED" == 1 ]]; then systemctl start "$SERVICE"; fi
    echo "Despliegue fallido; revisa backup, servicio y journal: $BACKUP"
  fi
  exit "$code"
}
trap recover EXIT
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
STATUS="$(curl -sS --max-time 10 -o "$BACKUP/native-probe.json" -w '%{http_code}' -H 'Content-Type: application/json' --data '{"op":"capabilities","args":{}}' "http://127.0.0.1:$PORT/api/native")"
[[ "$STATUS" == 401 ]] || { echo "Se esperaba 401 sin credenciales, llegó $STATUS"; exit 1; }
ENV_FILE="$LIVE/.env" node /root/cronmaster-android/scripts/probe-native.cjs
systemctl is-active --quiet "$SERVICE"
printf '%s\n' "$BACKUP" > "$ROOT/last-backup.tmp"
mv "$ROOT/last-backup.tmp" "$ROOT/last-backup"
trap - EXIT
echo "Desplegado. Backup: $BACKUP"
systemctl status "$SERVICE" --no-pager
BASH
```

No se ejecuta `.env` como shell: PORT se lee con parseEnv. La autenticación se conserva. El probe existente utiliza API_KEY o AUTH_PASSWORD para consultar capabilities sin ejecutar tareas; no implementa OIDC. Esta receta exige una de esas dos credenciales. Si solo usas OIDC, detente y prepara una prueba autenticada compatible, no desactives la autenticación. Ante fallos consulta `journalctl -u cronmaster.service -n 100 --no-pager`. La recuperación es un intento, no una garantía ante apagado, disco lleno o fallo de restauración.

## 4. Validar desde la URL del móvil

Comprueba login, capabilities y lectura de tareas mediante tu URL habitual HTTPS. La prueba local anterior no valida el proxy ni todas las operaciones de escritura. Para leer tareas sin modificar nada, desde una terminal interactiva:

```bash
bash <<'BASH'
set -Eeuo pipefail
umask 077
IFS= read -r -p 'URL HTTPS de Cronmaster: ' BASE </dev/tty
BASE="${BASE%/}"
[[ "$BASE" == https://* ]]
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
IFS= read -r -s -p 'Contraseña de Cronmaster: ' PASSWORD </dev/tty
printf '\n'
printf '%s' "$PASSWORD" | node -e 'let s=""; process.stdin.setEncoding("utf8"); process.stdin.on("data",c=>s+=c); process.stdin.on("end",()=>process.stdout.write(JSON.stringify({password:s})))' > "$TMP/login.json"
unset PASSWORD
curl --fail-with-body -sS --max-time 30 --cookie-jar "$TMP/cookies" -H 'Content-Type: application/json' --data-binary @"$TMP/login.json" "$BASE/api/auth/login" > "$TMP/login-response.json"
node -e 'if(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).success!==true) process.exit(1)' "$TMP/login-response.json"
rm -f "$TMP/login.json"
for op in capabilities jobs; do
  curl --fail-with-body -sS --max-time 60 --cookie "$TMP/cookies" -H 'Content-Type: application/json' --data "{\"op\":\"$op\",\"args\":{}}" "$BASE/api/native" > "$TMP/$op.json"
done
node -e 'const fs=require("fs"); const dir=process.argv[1]; const c=JSON.parse(fs.readFileSync(dir+"/capabilities.json","utf8")); const j=JSON.parse(fs.readFileSync(dir+"/jobs.json","utf8")); if(c.success!==true||c.version!==1||j.success!==true||!Array.isArray(j.data)) throw Error("Prueba inválida"); console.log("Capacidades correctas. Tareas: "+j.data.length)' "$TMP"
BASH
```

Introduce una URL de confianza, sin credenciales, query ni fragmento, y sin barra final. No se siguen redirecciones. No compartas respuestas de tareas ni cookies. Para probar solo por loopback, adapta conscientemente el protocolo y usa `http://127.0.0.1:PUERTO`; no envíes contraseñas por HTTP remoto. En Android usa la URL habitual del servidor; esta variante no abre un puerto paralelo.

## 5. Volver al código anterior

Conserva el backup. Este rollback restaura únicamente código y mantiene configuración y datos actuales; no revierte migraciones ni efectos de tareas. Revisa compatibilidad antes de usarlo. El archivo last-backup apunta al último despliegue exitoso; si uno falló, usa la ruta impresa por ese intento tras revisarla.

```bash
bash <<'BASH'
set -Eeuo pipefail
umask 077
ROOT=/opt/cronmaster-native-community
exec 9>"$ROOT/build.lock"
flock -n 9
BACKUP="$(cat "$ROOT/last-backup")"
[[ "$BACKUP" == "$ROOT"/backup-* ]]
test -f "$BACKUP/app/server.js"
systemctl stop cronmaster.service
trap 'systemctl start cronmaster.service || true' EXIT
rsync -a --delete --exclude=/.env --exclude=/scripts --exclude=/data --exclude=/snippets "$BACKUP/app/" /opt/cronmaster/
systemctl start cronmaster.service
trap - EXIT
systemctl status cronmaster.service --no-pager
BASH
```

Para siguientes cambios sigue [UPDATE-COMMUNITY-SCRIPTS](UPDATE-COMMUNITY-SCRIPTS.md). No mezcles este procedimiento con los destinos de las otras modalidades.
