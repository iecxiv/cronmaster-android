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
