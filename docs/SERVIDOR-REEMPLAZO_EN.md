# Extension replacing the original instance's code

[Español](SERVIDOR-REEMPLAZO.md) | English

Mode performed on the user's installation. Keeps /opt/cronmaster, its .env, cronmaster.service and configured port, but REPLACES official code with the extended standalone build. This is not an isolated installation.

1. Back up/snapshot data and crontabs; backups contain secrets.
2. Disable the official Cronmaster update job so that /api/native is not lost.
3. Complete [COMPILAR-SERVIDOR](COMPILAR-SERVIDOR_EN.md).
4. Select these variables in your shell:

```bash
export SERVICE=cronmaster.service
export LIVE=/opt/cronmaster
export ENV_FILE=/opt/cronmaster/.env
```

5. Run the block in [DESPLEGAR-SERVIDOR](DESPLEGAR-SERVIDOR_EN.md) in that same shell. It preserves configuration and data and backs up code before replacement.
6. Verify:

```bash
systemctl status cronmaster.service --no-pager
node -p 'require("/opt/cronmaster/package.json").version'
ENV_FILE=/opt/cronmaster/.env node /root/cronmaster-android/scripts/probe-native.cjs
journalctl -u cronmaster.service -n 60 --no-pager
```

Use the same URL in Android as before. There is no new service or port 3001. Placing route.ts in the active directory without rebuilding does not register the new route.

Updates: [UPDATE-REEMPLAZO](UPDATE-REEMPLAZO_EN.md). To recover code, follow the recovery section in DESPLEGAR-SERVIDOR. New backups use code/; the conversation's first historical backup used app/. Do not interchange these formats.
