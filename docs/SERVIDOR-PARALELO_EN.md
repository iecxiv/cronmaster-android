# Extension retaining the original code

[Español](SERVIDOR-PARALELO.md) | English

Documented alternative, not the confirmed initial deployment. Keeps /opt/cronmaster code, its .env and unit file. Installs another runtime at a FIXED path and another service. Shares scripts/data/snippets and crontabs: Android writes affect the same data. For full isolation, use another VM/LXC. Only one instance should remain active.

Take a backup/snapshot and complete [COMPILAR-SERVIDOR](COMPILAR-SERVIDOR_EN.md). As root:

```bash
install -d -m 700 /etc/cronmaster-native /opt/cronmaster-native-parallel/app
if [[ ! -e /etc/cronmaster-native/parallel.env ]]; then
  install -m 600 /opt/cronmaster/.env /etc/cronmaster-native/parallel.env
fi
nano /etc/cronmaster-native/parallel.env
```

Keep authentication and other variables, adjusting these without duplicating them:

```dotenv
PORT=3001
HOSTNAME=0.0.0.0
APP_URL=http://IP_DEL_SERVIDOR:3001
INTERNAL_API_URL=http://127.0.0.1:3001
```

Replace IP_DEL_SERVIDOR with your server IP or use your HTTPS domain. Check that port 3001 is free; HOSTNAME=127.0.0.1 restricts access to a local proxy. Do not source this .env.

```bash
cat > /etc/systemd/system/cronmaster-native.service <<'UNIT'
[Unit]
Description=Cronmaster native API, separate code
After=network.target
Conflicts=cronmaster.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/cronmaster-native-parallel/app
EnvironmentFile=/etc/cronmaster-native/parallel.env
ExecStart=/usr/bin/node /opt/cronmaster-native-parallel/app/server.js
Restart=on-failure
RestartSec=10
UMask=0077

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
```

Create symlinks only inside the new runtime; do not move original data:

```bash
bash <<'BASH'
set -Eeuo pipefail
APP=/opt/cronmaster-native-parallel/app
for name in scripts data snippets; do
  test -d "/opt/cronmaster/$name"
  if [[ ! -e "$APP/$name" && ! -L "$APP/$name" ]]; then ln -s "/opt/cronmaster/$name" "$APP/$name"; fi
  test -L "$APP/$name"
  [[ "$(readlink -f "$APP/$name")" == "$(realpath "/opt/cronmaster/$name")" ]]
done
BASH
systemctl disable --now cronmaster.service
systemctl enable cronmaster-native.service
export SERVICE=cronmaster-native.service
export LIVE=/opt/cronmaster-native-parallel/app
export ENV_FILE=/etc/cronmaster-native/parallel.env
```

Run [DESPLEGAR-SERVIDOR](DESPLEGAR-SERVIDOR_EN.md) in the same shell. Configure Android/proxy for 3001. Disable the official update job: it can start the original and stop the native instance through Conflicts. This changes the original service's state, not its file.

To return:

```bash
systemctl disable --now cronmaster-native.service
systemctl enable --now cronmaster.service
```

Restore the previous enablement state if it was originally disabled, and the proxy upstream if changed. Do not delete the native runtime: jobs created by it may reference its absolute path. Audit all users' crontabs before moving/deleting paths. Do not delete /opt/cronmaster, which contains shared data.

If you previously replaced /opt/cronmaster, this procedure does not make it pristine again: you must deliberately restore official code from a compatible backup. Updates: [UPDATE-PARALELO](UPDATE-PARALELO_EN.md).
