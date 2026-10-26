# Compilar Cronmaster con la extensión

Paso común para ambas modalidades. Ejecuta como root en la máquina donde instalaste Community Scripts: dentro del LXC si corresponde. Necesitas git, rsync, curl, Node y Corepack. La base conocida usó Node 22.23.4 y Yarn 1.22.22. Revisa los requisitos de cada release antes de cambiar herramientas globalmente.

Haz backup/snapshot de datos y crontabs. No ejecutes dos actualizadores a la vez. Desactiva el job oficial de actualización de Cronmaster antes de desplegar; podría sustituir la extensión. No detengas cron entero.

Clona este repositorio solo si no existe:

```bash
git clone https://github.com/iecxiv/cronmaster-android.git /root/cronmaster-android
```

Selecciona una etiqueta real. 2.4.0 es la base validada; para otra versión cambia TAG conscientemente. Si no existe, no sustituyas por main.

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

No se ha sustituido la aplicación activa. candidate.env se genera solo al terminar correctamente y no contiene secretos; no lo confundas con el .env del servidor. Si falla la compilación, no despliegues un candidato anterior. No desactives TypeScript ni cambies el lockfile para ocultar incompatibilidades del adaptador.

El límite de 4 GiB es del heap Node, no del árbol completo de procesos. Sigue con la instalación/actualización de la modalidad elegida.
