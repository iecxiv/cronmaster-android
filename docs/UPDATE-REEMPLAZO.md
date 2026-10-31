# Actualizar la modalidad de reemplazo

Español | [English](UPDATE-REEMPLAZO_EN.md)

Aplica a /opt/cronmaster y cronmaster.service.

1. Revisa las notas de la release, Node y posibles migraciones. Haz backup/snapshot de datos y crontabs.
2. Conserva el adaptador y cualquier cambio local del repositorio; no hagas git pull sobre modificaciones sin revisarlas.
3. Repite [COMPILAR-SERVIDOR](COMPILAR-SERVIDOR.md) con la etiqueta exacta elegida.
4. Selecciona las variables de [SERVIDOR-REEMPLAZO](SERVIDOR-REEMPLAZO.md) y ejecuta [DESPLEGAR-SERVIDOR](DESPLEGAR-SERVIDOR.md).
5. Verifica versión, acceso autenticado y tareas/logs desde Android.

Si el job oficial instaló 2.4.1 y desapareció /api/native, recompila esa misma versión con el adaptador. La versión del dashboard no demuestra compatibilidad del adaptador. Si falta la etiqueta, no uses main silenciosamente. Si falla TypeScript, no despliegues ni suprimas la comprobación.

No dejes el actualizador oficial funcionando en paralelo: instala el prebuild sin extensión. Los actualizadores personalizados entregados en la conversación no se incluyen aquí por aproximación ni se dan por instalados; revisa su --help, rutas y logs antes de usarlos. Automatiza con un solo proceso externo al servicio que reinicia, bloqueo y prueba autenticada.

El botón de refresco de Android no actualiza software. Un rollback de código no revierte datos migrados; conserva una copia compatible con la extensión, no solo un backup del prebuild oficial.
