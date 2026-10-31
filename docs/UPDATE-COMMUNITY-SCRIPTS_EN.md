# Update the extension on a Community Scripts installation

[Español](UPDATE-COMMUNITY-SCRIPTS.md) | English

Applies exclusively to [Community Scripts mode](SERVIDOR-COMMUNITY-SCRIPTS_EN.md): `/opt/cronmaster` and `cronmaster.service`. Does not alter replacement or parallel installation guides.

## Reapply the extension to the same version

1. Take a snapshot and back up data and crontabs. Check that no official updater runs concurrently.
2. Deliberately update your cronmaster-android checkout to the desired revision, preserving local changes.
3. Repeat the specific guide from version detection. Each build creates a new directory; it does not delete previous ones or download latest.
4. The version is read from `/opt/cronmaster/package.json`; an exact tag and the downloaded source version are validated. No manual version editing is needed.
5. Build and prepare staging while the current service stays active. Deploy only after TypeScript and build pass.
6. Check health, unauthenticated rejection, authenticated capabilities and task reads. Keep the backup and check Android.

## Also change the Cronmaster version

Reapplying as above does not upgrade Cronmaster to a new release. If using the Community Scripts updater, treat that as a separate operation with a maintenance window, migration review and backups. It may overwrite the runtime and remove `/api/native`. Afterwards detect the version actually installed again and build the extension for that tag. Do not reuse old staging.

Do not chain unsupervised updaters or run two deployments concurrently. The guide's lock coordinates only executions of that procedure; it does not lock the external updater.

The pre-deployment check rejects a version change during building; it does not detect every concurrent modification retaining the same number. If the tag is missing, do not automatically switch to main or latest: stop and review.

The guide's rollback restores code and preserves current data. It does not undo migrations, crontabs, task effects or changes made by an official update. Review compatibility before returning to an earlier version.

## Optional cronjob: extension after the main Proxmox LXC updater

[Español](UPDATE-COMMUNITY-SCRIPTS.md#cronjob-opcional-extensión-después-del-actualizador-principal-de-proxmox-lxc)

The script is [scripts/update-cronmaster-with-native.sh](../scripts/update-cronmaster-with-native.sh). This is a chained alternative to the manual procedure above: first validate a supervised update, then monitor it. It does not alter other modes and is not installed automatically.

### Specific layout

- Active application: `/opt/cronmaster`, run by `cronmaster.service`.
- Build/backup root for this case: `/opt/cronmaster-native-build`. This is NOT a second instance.
- The script puts all new state under `/opt/cronmaster-native-build/automatic-update`, with uniquely named builds/backups. It does not overwrite historical directories, candidate.env or last-backup in the root.
- The manual installation guide uses another ROOT, `/opt/cronmaster-native-community`. Do not mix its control files/backups with this script's, or assume they share a lock.

### Requirements and installation

Run inside the LXC as root. Requires Node 22/Corepack, git, rsync, curl, flock, timeout, sha256sum, awk, perl, grep, tail and systemctl. `/usr/local/bin/update_cronmaster` and a reviewed checkout at `/root/cronmaster-android`, containing the adapter and `scripts/probe-native.cjs`, must exist. It does not install packages, change Node or automatically git pull. It uses Yarn 1.22.22 through Corepack. The 4 GiB heap setting does not limit total build memory.

Review the checkout revision and local changes before updating it. Install the script from it:

```bash
install -m 700 /root/cronmaster-android/scripts/update-cronmaster-with-native.sh /usr/local/sbin/update-cronmaster-with-native.sh
bash -n /usr/local/sbin/update-cronmaster-with-native.sh
```

Take a snapshot and independent configuration/data/crontab backups; check space for source, dependencies, staging and a runtime copy. Check `systemctl show cronmaster.service -p WorkingDirectory -p ExecStart`: this recipe assumes `/opt/cronmaster`, the expected Node and PORT from `.env` (3000 if absent). Configuration/port overrides require adaptation. The probe requires API_KEY or AUTH_PASSWORD; OIDC alone is insufficient.

### Replace the previous job, do not duplicate it

Keep your schedule and replace the job body with:

```bash
/usr/local/sbin/update-cronmaster-with-native.sh
```

The chain already calls `/usr/local/bin/update_cronmaster`: do not also retain the original job or schedule a second job with a fixed delay. It preserves `/var/log/update_cronmaster.log`, the `/root/.ascii_cronmaster` banner and the summary filter. The shared script retains its Spanish operational messages.

Use system cron inside the LXC (root's `crontab -e`) or a scheduler external to the `cronmaster.service` cgroup. Do NOT run it from a task dependent on the service being stopped: it could kill its own updater. Root's crontab uses five schedule fields before the command; /etc/cron.d additionally requires the root user field. No new schedule is proposed or installed.

### Exact trigger

- Main updater returns an error: native is not run and its exit code is preserved.
- Main updater succeeds and `version` in `/opt/cronmaster/package.json` is unchanged: no cloning, dependency installation, building or native restart. Control directories/locks may be created.
- Main updater succeeds and the version changes: clone that new version's tag, add the local adapter and require successful TypeScript/build checks. Missing tags are not replaced with main/latest.
- Reinstallation with the same number does not trigger native. Log text does not decide whether to build.

The extension may be unavailable during building after the official updater overwrites it. Deployment targets `/opt/cronmaster` and restarts only `cronmaster.service`. It preserves `.env*`, scripts, data and snippets; review additional local files and adapt EXCLUDES before using rsync --delete. It does not write crontabs or modify the unit. Stopping the web UI does not stop all cron tasks; the backup is not a consistent snapshot of data being written.

### Failures, logs and retry

Main log: `/var/log/update_cronmaster.log` (overwritten). Native log: `/var/log/update_cronmaster_native.log` (appended). Under `automatic-update`, pending.json records a pending native rebuild; last-backup points to the last successful deployment. A failed attempt prints its own backup path in the log.

Build failure does not replace the active application. Failure during deployment attempts to restore the backup taken AFTER the official update: it may contain the new release WITHOUT native. It does not automatically return to the previous release or undo migrations. Recovery can fail; inspect the log, service and backup.

A subsequent run without a version change does not automatically rebuild a pending update; it warns. After reviewing/fixing the issue:

```bash
/usr/local/sbin/update-cronmaster-with-native.sh --retry-native
```

Retry does not run the main updater and requires the pending version to remain installed. It may also stop the service: use a console/external scheduler.

The chain uses `/run/lock/cronmaster-update-chain.lock` and `/opt/cronmaster-native-build/build.lock`. It does not automatically coordinate other updaters or the guide using another ROOT; avoid every concurrent update path. It rechecks version and the server.js hash before deploying, without guaranteeing detection of all changes.

Checks cover health, unauthenticated HTTP 401, authenticated capabilities and active service; they do not run tasks or test all writes/proxy/Android access. It does not execute .env as shell or print credentials; do not use set -x. Backups may contain secrets. No automatic cleanup/logrotate is configured: monitor disk and set retention.

The supplied script passed bash -n and mocked tests for no update, successful update, main updater failure, valid retry and rejection of a retry for another version. No real build/deployment has been validated here. Test a supervised update before unattended use.
