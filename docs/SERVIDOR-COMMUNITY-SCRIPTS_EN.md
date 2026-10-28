# Extension on Cronmaster installed through Proxmox Community Scripts

[Español](SERVIDOR-COMMUNITY-SCRIPTS.md) | English

Third mode, based on the working deployment reported by the user. Applies to an existing `/opt/cronmaster` installation whose `cronmaster.service` runs `server.js`. Builds a separate copy and replaces only the active runtime, preserving configuration and data. It creates no other service or instance. It does not change replacement or parallel installation guides. The generalized blocks below require validation on your server.

Installation origin: https://community-scripts.org/scripts/cronmaster and https://github.com/community-scripts/ProxmoxVE/blob/main/tools/addon/cronmaster.sh. Do not rerun the installer to add the extension.

## 1. Check the environment and prepare backups

Run as root through SSH or console, not from a Cronmaster child process. First take a snapshot and back up data, configuration and all users' crontabs. Deliberately pause automatic updates throughout the procedure. Stopping the UI does not stop cron or its tasks; a backup taken during writes is not a consistent snapshot.

```bash
systemctl cat cronmaster.service
systemctl status cronmaster.service --no-pager
node --version
node -p 'require("/opt/cronmaster/package.json").version'
```

This recipe assumes Node 22 with Corepack, `server.js`, `.env` and working directory `/opt/cronmaster`. Verify that the service uses the same Node and configuration. If PORT is overridden, paths/users differ, containers are involved or data lives outside scripts/data/snippets, adapt and review before continuing. Node and the systemd unit are not changed. Install missing tools using your system package manager: git, rsync, curl and flock.

Prepare a client checkout and its adapter, or use an existing checkout after reviewing local changes:

```bash
git clone --branch dev https://github.com/iecxiv/cronmaster-android.git /root/cronmaster-android
```

If the destination exists, do not overwrite it. Verify the revision to deploy. Do not copy `.env` into the build source.

## 2. Detect version, download, add the route and build

Read the installed version, not latest. Search for an exact tag first, then the v-prefixed form; abort if neither exists. Version/tag detection does not guarantee compatibility: TypeScript and build must succeed. The 4 GiB heap setting does not limit total build memory. Operational shell messages are kept as in the Spanish guide.

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

The route comes directly from the repository's corrected file; do not paste a generator's heredoc inside route.ts. Copying it only to `/opt/cronmaster/app/api/native` does not rebuild the runtime. If building fails, do not deploy: review logs in the displayed build directory. candidate.env is generated locally as root and contains no secrets; do not replace it with a third-party file.

## 3. Deploy with backup and automatic recovery

This block preserves `/opt/cronmaster/.env`, scripts, data and snippets. It does not modify crontabs or install updaters. The full installation copy may contain secrets and use substantial disk space: check capacity and permissions first. Do not share backups. Review additional local files before using rsync --delete.

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

The .env file is not executed as shell: parseEnv reads PORT. Authentication is preserved. The existing probe uses API_KEY or AUTH_PASSWORD to query capabilities without running tasks; it does not implement OIDC. This recipe requires one of those credentials. If you use only OIDC, stop and prepare a compatible authenticated test; do not disable authentication. On failure, consult `journalctl -u cronmaster.service -n 100 --no-pager`. Recovery is an attempt, not a guarantee against power loss, full disks or restoration failures.

## 4. Validate through the mobile app's URL

Check login, capabilities and task reads through your usual HTTPS URL. The local test above does not validate the proxy or every write operation. To read tasks without changing anything, from an interactive terminal:

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

Enter a trusted URL without credentials, query, fragment or trailing slash. Redirects are not followed. Do not share task responses or cookies. For loopback-only tests, deliberately adapt the protocol and use `http://127.0.0.1:PORT`; do not send passwords through remote HTTP. In Android, use the server's usual URL; this mode opens no parallel port.

## 5. Return to the previous code

Keep the backup. This rollback restores only code and preserves current configuration/data; it does not undo migrations or task effects. Review compatibility first. last-backup points to the last successful deployment; after a failed attempt, review and use that attempt's printed path instead.

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

For subsequent changes, follow [UPDATE-COMMUNITY-SCRIPTS](UPDATE-COMMUNITY-SCRIPTS_EN.md). Do not mix this procedure with destinations from other modes.
