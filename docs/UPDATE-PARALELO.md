# Actualizar la modalidad paralela

Español | [English](UPDATE-PARALELO_EN.md)

Aplica solo a /opt/cronmaster-native-parallel/app y cronmaster-native.service. No uses el despliegue de reemplazo.

1. Backup/snapshot de datos y crontabs, y revisión de requisitos/migraciones de la nueva release.
2. Repite [COMPILAR-SERVIDOR](COMPILAR-SERVIDOR.md) con su etiqueta. El servicio nativo sigue activo mientras compila.
3. Comprueba que la original está inactiva y elige variables:

```bash
systemctl is-active cronmaster.service || true
export SERVICE=cronmaster-native.service
export LIVE=/opt/cronmaster-native-parallel/app
export ENV_FILE=/etc/cronmaster-native/parallel.env
```

Si la original está activa, detente y revisa qué instancia estás usando. Ejecuta [DESPLEGAR-SERVIDOR](DESPLEGAR-SERVIDOR.md) para guardar código anterior, sustituir únicamente el runtime nativo fijo y probar autenticación.

```bash
systemctl status cronmaster-native.service --no-pager
node -p 'require("/opt/cronmaster-native-parallel/app/package.json").version'
ENV_FILE=/etc/cronmaster-native/parallel.env node /root/cronmaster-android/scripts/probe-native.cjs
```

Conserva los enlaces y la configuración. No cambies el directorio real en cada release: los wrappers/jobs pueden usar rutas absolutas. El rollback restaura código, no datos migrados.

No encadenes el actualizador oficial: puede arrancar cronmaster.service. Si quieres actualizar también la copia oficial de reserva, hazlo como operación separada, deteniendo antes la nativa y revisando compatibilidad de los datos compartidos. El código original antiguo no garantiza poder leer datos nuevos.

Para automatizar usa una tarea externa al servicio, con bloqueo y estos destinos. El actualizador que escribe sobre /opt/cronmaster NO sirve para esta modalidad. Los ejemplos del repositorio no instalan timers o cronjobs automáticamente.
