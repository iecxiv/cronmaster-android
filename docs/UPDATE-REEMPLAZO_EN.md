# Update replacement mode

[Español](UPDATE-REEMPLAZO.md) | English

Applies to /opt/cronmaster and cronmaster.service.

1. Review release notes, Node requirements and possible migrations. Back up/snapshot data and crontabs.
2. Preserve the adapter and any local repository changes; do not git pull over unreviewed modifications.
3. Repeat [COMPILAR-SERVIDOR](COMPILAR-SERVIDOR_EN.md) with the exact selected tag.
4. Select the variables from [SERVIDOR-REEMPLAZO](SERVIDOR-REEMPLAZO_EN.md) and run [DESPLEGAR-SERVIDOR](DESPLEGAR-SERVIDOR_EN.md).
5. Verify the version, authenticated access and tasks/logs from Android.

If the official job installed 2.4.1 and /api/native disappeared, rebuild that same version with the adapter. The dashboard version does not prove adapter compatibility. If the tag is missing, do not silently use main. If TypeScript fails, do not deploy or suppress the check.

Do not leave the official updater running alongside this: it installs the prebuild without the extension. Custom updaters supplied in the conversation are not approximately reproduced here or assumed installed; review their --help, paths and logs before use. Automate with one process external to the service it restarts, a lock and an authenticated test.

Android's refresh button does not update software. Code rollback does not undo migrated data; keep a copy compatible with the extension, not just an official prebuild backup.
