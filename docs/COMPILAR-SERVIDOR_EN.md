# Build Cronmaster with the extension

[Español](COMPILAR-SERVIDOR.md) | English

Shared step for the two original modes. Run as root on the machine where you installed Community Scripts, inside the LXC if applicable. Requires git, rsync, curl, Node and Corepack. The known base used Node 22.23.4 and Yarn 1.22.22. Review each release's requirements before changing global tools.

Back up/snapshot data and crontabs. Do not run two updaters concurrently. Disable the official Cronmaster update job before deploying: it could overwrite the extension. Do not stop the entire cron daemon.

Clone this repository only if it does not exist:

```bash
git clone https://github.com/iecxiv/cronmaster-android.git /root/cronmaster-android
```

Select a real tag. 2.4.0 is the validated base; deliberately change TAG for another version. If missing, do not substitute main.

```bash
bash <<'BASH'
set -Eeuo pipefail
umask 077
TAG=2.4.0
BASE=/opt/cronmaster-native-build
WORK="$BASE/candidates/$(date +%Y%m%d-%H%M%S)-$$"
SOURCE="$WORK/source"
STAGE="$WORK/stage"
mkdir -p "$WORK"
git clone --depth 1 --branch "$TAG" https://github.com/fccview/cronmaster.git "$SOURCE"
mkdir -p "$SOURCE/app/api/native"
cp /root/cronmaster-android/server-extension/app/api/native/route.ts "$SOURCE/app/api/native/route.ts"
cd "$SOURCE"
corepack prepare yarn@1.22.22 --activate
env NODE_ENV=development corepack yarn install --frozen-lockfile
NODE_OPTIONS=--max-old-space-size=4096 corepack yarn tsc --noEmit
NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 NODE_OPTIONS=--max-old-space-size=4096 corepack yarn build
test -f .next/standalone/server.js
test -f .next/server/app/api/native/route.js
mkdir -p "$STAGE/.next/static" "$STAGE/public" "$STAGE/app/_scripts"
cp -a .next/standalone/. "$STAGE/"
cp -a .next/static/. "$STAGE/.next/static/"
cp -a public/. "$STAGE/public/"
cp -a app/_scripts/. "$STAGE/app/_scripts/"
for name in .env scripts data snippets; do rm -rf -- "$STAGE/$name"; done
node -p "require('$STAGE/package.json').version"
printf 'STAGE=%q\nTAG=%q\n' "$STAGE" "$TAG" > "$BASE/candidate.env"
echo "Candidato preparado: $STAGE"
BASH
```

The active application has not been replaced. candidate.env is generated only after successful completion and contains no secrets; do not confuse it with the server .env. If building fails, do not deploy an older candidate. Do not disable TypeScript or change the lockfile to hide adapter incompatibilities.

The 4 GiB limit applies to the Node heap, not the full process tree. Continue with installation/update for your chosen mode. Shell blocks retain their original operational messages.
