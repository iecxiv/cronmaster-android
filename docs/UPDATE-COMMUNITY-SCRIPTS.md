# Actualizar la extensión en una instalación de Community Scripts

Aplica exclusivamente a la [variante Community Scripts](SERVIDOR-COMMUNITY-SCRIPTS.md): `/opt/cronmaster` y `cronmaster.service`. No modifica las guías de sustitución ni de instalación paralela.

## Reaplicar la extensión a la misma versión

1. Haz snapshot y backup de datos y crontabs. Revisa que no haya un actualizador oficial concurrente.
2. Actualiza conscientemente tu checkout de cronmaster-android a la revisión deseada, conservando cambios locales.
3. Repite la guía específica desde la detección de versión. Cada compilación crea un directorio nuevo; no borra los anteriores ni descarga latest.
4. La versión se lee de `/opt/cronmaster/package.json`; se valida una etiqueta exacta y la versión del código descargado. No es necesario editar a mano el número de versión.
5. Compila y prepara staging mientras el servicio actual sigue activo. Despliega únicamente si pasan TypeScript y build.
6. Comprueba health, rechazo sin credenciales, capabilities autenticadas y lectura de tareas. Conserva el backup y verifica Android.

## Cambiar también la versión de Cronmaster

La reaplicación anterior no actualiza Cronmaster a una release nueva. Si usas el actualizador de Community Scripts, trátalo como una operación separada con ventana de mantenimiento, revisión de migraciones y backups. Puede sobrescribir el runtime y quitar `/api/native`. Después vuelve a detectar la versión realmente instalada y compila la extensión para esa etiqueta. No reutilices un staging antiguo.

No encadenes actualizadores sin supervisión ni ejecutes dos despliegues simultáneos. El bloqueo de la guía solo coordina ejecuciones de ese procedimiento, no bloquea el actualizador externo.

La comprobación previa al despliegue rechaza un cambio de versión durante la compilación; no detecta todas las modificaciones concurrentes que mantengan el mismo número. Si falta la etiqueta, no cambies automáticamente a main ni a latest: detente y revisa el caso.

El rollback de la guía restaura código y preserva los datos actuales. No revierte migraciones, crontabs, efectos de tareas ni cambios hechos por una actualización oficial. Revisa compatibilidad antes de volver a una versión anterior.
