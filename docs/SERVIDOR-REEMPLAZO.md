# Extensión reemplazando el código de la instancia original

Modalidad realizada en la instalación del usuario. Mantiene /opt/cronmaster, su .env, cronmaster.service y el puerto configurado, pero SUSTITUYE el código oficial por el standalone con extensión. No es una instalación aislada.

1. Backup/snapshot de datos y crontabs; los backups contienen secretos.
2. Desactiva el job oficial que actualiza Cronmaster para no perder /api/native.
3. Completa [COMPILAR-SERVIDOR](COMPILAR-SERVIDOR.md).
4. Selecciona estas variables en tu shell:

```bash
export SERVICE=cronmaster.service
export LIVE=/opt/cronmaster
export ENV_FILE=/opt/cronmaster/.env
```

5. Ejecuta el bloque de [DESPLEGAR-SERVIDOR](DESPLEGAR-SERVIDOR.md) en esa misma shell. Conserva configuración y datos y hace backup de código antes de sustituirlo.
6. Verifica:

```bash
systemctl status cronmaster.service --no-pager
node -p 'require("/opt/cronmaster/package.json").version'
ENV_FILE=/opt/cronmaster/.env node /root/cronmaster-android/scripts/probe-native.cjs
journalctl -u cronmaster.service -n 60 --no-pager
```

En Android usa la misma URL de antes. No hay nuevo servicio ni puerto 3001. Poner route.ts en el directorio activo sin recompilar no registra la nueva ruta.

Actualizaciones: [UPDATE-REEMPLAZO](UPDATE-REEMPLAZO.md). Para recuperar código sigue el apartado de recuperación de DESPLEGAR-SERVIDOR. Los backups nuevos tienen code/; el primer backup histórico de la conversación tenía app/. No intercambies esos formatos.
