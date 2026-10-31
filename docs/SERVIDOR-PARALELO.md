# Extensión manteniendo el código original

Español | [English](SERVIDOR-PARALELO_EN.md)

Alternativa documentada, no el despliegue inicial confirmado. Mantiene el código /opt/cronmaster, su .env y su unidad. Instala otro runtime de ruta FIJA y otro servicio. Comparte scripts/data/snippets y crontabs: las escrituras de Android afectan a los mismos datos. Para aislamiento total usa otra VM/LXC. Solo una instancia debe quedar activa.

Haz backup/snapshot y completa [COMPILAR-SERVIDOR](COMPILAR-SERVIDOR.md). Desde root:

```bash
install -d -m 700 /etc/cronmaster-native /opt/cronmaster-native-parallel/app
if [[ ! -e /etc/cronmaster-native/parallel.env ]]; then
  install -m 600 /opt/cronmaster/.env /etc/cronmaster-native/parallel.env
fi
nano /etc/cronmaster-native/parallel.env
```

Conserva autenticación y demás variables, ajustando sin duplicarlas:

```dotenv
PORT=3001
HOSTNAME=0.0.0.0
APP_URL=http://IP_DEL_SERVIDOR:3001
INTERNAL_API_URL=http://127.0.0.1:3001
```

Sustituye IP_DEL_SERVIDOR o utiliza tu dominio HTTPS. Comprueba que 3001 esté libre; HOSTNAME=127.0.0.1 limita el acceso a un proxy local. No ejecutes source sobre este .env.

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

Crea enlaces solo dentro del nuevo runtime; no muevas los datos originales:

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

Ejecuta [DESPLEGAR-SERVIDOR](DESPLEGAR-SERVIDOR.md) en la misma shell. Configura Android/proxy para 3001. Desactiva el job oficial de actualización: puede arrancar la original y detener la nativa por Conflicts. Esto cambia el estado del servicio original, no su archivo.

Para volver:

```bash
systemctl disable --now cronmaster-native.service
systemctl enable --now cronmaster.service
```

Restituye el estado previo si originalmente no estaba habilitado, y el upstream del proxy si lo cambiaste. No borres el runtime nativo: jobs creados por él pueden referenciar su ruta absoluta. Audita los crontabs de todos los usuarios antes de mover/eliminar rutas. No borres /opt/cronmaster, pues contiene datos compartidos.

Si ya reemplazaste antes /opt/cronmaster, este procedimiento no lo vuelve prístino: para ello necesitas restaurar conscientemente su código oficial desde un backup compatible. Actualizaciones: [UPDATE-PARALELO](UPDATE-PARALELO.md).
