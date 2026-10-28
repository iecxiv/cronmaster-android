# Update parallel mode

[Español](UPDATE-PARALELO.md) | English

Applies only to /opt/cronmaster-native-parallel/app and cronmaster-native.service. Do not use replacement deployment.

1. Back up/snapshot data and crontabs and review the new release's requirements/migrations.
2. Repeat [COMPILAR-SERVIDOR](COMPILAR-SERVIDOR_EN.md) with its tag. The native service stays active during building.
3. Check that the original is inactive and select variables:

```bash
systemctl is-active cronmaster.service || true
export SERVICE=cronmaster-native.service
export LIVE=/opt/cronmaster-native-parallel/app
export ENV_FILE=/etc/cronmaster-native/parallel.env
```

If the original is active, stop and review which instance you are using. Run [DESPLEGAR-SERVIDOR](DESPLEGAR-SERVIDOR_EN.md) to back up previous code, replace only the fixed native runtime and test authentication.

```bash
systemctl status cronmaster-native.service --no-pager
node -p 'require("/opt/cronmaster-native-parallel/app/package.json").version'
ENV_FILE=/etc/cronmaster-native/parallel.env node /root/cronmaster-android/scripts/probe-native.cjs
```

Preserve symlinks and configuration. Do not change the real directory for each release: wrappers/jobs may use absolute paths. Rollback restores code, not migrated data.

Do not chain the official updater: it can start cronmaster.service. To update the reserve official copy too, do so separately, stopping the native instance first and reviewing shared-data compatibility. Old original code is not guaranteed to read new data.

For automation, use an external task with locking and these destinations. An updater writing to /opt/cronmaster does NOT serve this mode. Repository examples do not install timers or cronjobs automatically.
