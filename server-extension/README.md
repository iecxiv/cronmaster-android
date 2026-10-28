# Extensión API nativa

El adaptador completo está en [app/api/native/route.ts](app/api/native/route.ts). El archivo corregido aportado por el usuario coincide byte por byte con la copia existente en dev al preparar esta documentación. Incluye `expression.next().toDate().toISOString()` para las próximas ejecuciones.

## Instalaciones de Proxmox Community Scripts

Sigue la [implementación específica](../docs/SERVIDOR-COMMUNITY-SCRIPTS.md) y su [actualización](../docs/UPDATE-COMMUNITY-SCRIPTS.md). Copia el adaptador al código fuente de la misma versión detectada en `/opt/cronmaster/package.json` y recompila. Copiar TypeScript únicamente al runtime activo no incorpora la ruta al servidor compilado.

No se modifican los procedimientos de [sustitución](../docs/SERVIDOR-REEMPLAZO.md) ni de [instalación paralela](../docs/SERVIDOR-PARALELO.md). La extensión reutiliza autenticación y acciones de Cronmaster. Mantén autenticación y HTTPS; la detección de versión no reemplaza la comprobación TypeScript, la compilación ni las pruebas autenticadas.
