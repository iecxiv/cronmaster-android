# Native API extension

[Español](README.md) | English

The complete adapter is at [app/api/native/route.ts](app/api/native/route.ts). The corrected file supplied by the user matched the existing dev copy byte for byte when this documentation was prepared. It includes `expression.next().toDate().toISOString()` for upcoming runs.

## Proxmox Community Scripts installations

Follow the [specific installation guide](../docs/SERVIDOR-COMMUNITY-SCRIPTS_EN.md) and [update guide](../docs/UPDATE-COMMUNITY-SCRIPTS_EN.md). Copy the adapter into the source for the same version detected in `/opt/cronmaster/package.json` and rebuild. Copying TypeScript only into the active runtime does not add the route to the compiled server.

The [replacement](../docs/SERVIDOR-REEMPLAZO_EN.md) and [parallel installation](../docs/SERVIDOR-PARALELO_EN.md) procedures are unchanged. The extension reuses Cronmaster authentication and actions. Keep authentication and HTTPS enabled; version detection does not replace TypeScript checks, building or authenticated tests.
