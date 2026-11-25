# TherionLabs · Valparaíso

Aplicación Android de captación y seguimiento de prospectos por evento, con almacenamiento local y conexión al backend Spring Boot.

## Integrantes

Diego Barra, Franco Tubi, Maicol García y Benjamín Osorio.

## Funciones

- Administración crea, consulta, edita y da de baja eventos mediante el servidor.
- Los captadores descargan eventos y registran prospectos en el dispositivo.
- Las capturas pendientes se envían cuando hay conexión. Los rechazos conservan su motivo y pueden corregirse para reintentar.
- Administración descarga las fichas por evento, las edita y registra seguimientos.
- Los seguimientos tienen identificadores estables para que un reintento no duplique la gestión.
- Las fichas sincronizadas se dan de baja en el servidor. Las que tienen gestiones se conservan.
- La oferta académica se limita a Valparaíso y Viña del Mar, con carreras por sede.
- Cámara y ubicación son opcionales. Las fotos permanecen en el dispositivo; enviar su ruta no transfiere el archivo a otro teléfono.
- Administración dispone de gestión de captadores, estadísticas y exportación a Excel.
- El clima de Valparaíso y Viña del Mar se consulta con Retrofit a Open-Meteo como servicio externo, separado del backend.

## Ejecución

1. Abrir esta carpeta en Android Studio y usar JDK 21.
2. Instalar Android SDK 36, revisión menor 1.
3. Ejecutar el backend actualizado con MySQL y su clave administrativa configurada.
4. Ejecutar la app. En el emulador, la dirección inicial es `http://10.0.2.2:8081/`.
5. Para un teléfono físico, configurar en Cuenta la dirección accesible del computador o servidor. La dirección se configura antes de descargar eventos o registrar fichas para no mezclar bases de servidores distintos.
6. Ingresar la clave administrativa del servidor en Cuenta. Esta clave es distinta de la contraseña local de acceso y se mantiene únicamente durante la sesión.
7. Registrar el captador en el servidor, generar su credencial y vincularla desde Cuenta en el dispositivo del captador.

## Cuentas locales

| Usuario | Contraseña | Perfil |
| --- | --- | --- |
| administrativo | Admin2026! | Administrativo |
| captador | Captador2026! | Captador |
| captador2 | Captador2026! | Captador |

El acceso local permite abrir la aplicación sin internet. Las operaciones remotas administrativas requieren además la clave del servidor; los envíos requieren la credencial del dispositivo.

## Operaciones del backend

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/api/eventos?incluirEliminados=true` | Actualizar eventos y bajas locales |
| POST | `/api/eventos` | Crear evento |
| PUT / DELETE | `/api/eventos/{id}` | Editar o dar de baja un evento |
| POST | `/api/participaciones/unirse` | Vincular participación con token del dispositivo |
| POST | `/api/prospectos/sync` | Enviar captura con UUID estable |
| GET | `/api/admin/eventos/{id}/prospectos` | Consultar fichas del evento |
| PUT / DELETE | `/api/admin/prospectos/{id}` | Editar o dar de baja una ficha |
| GET / POST | `/api/admin/prospectos/{id}/seguimientos` | Consultar y registrar gestiones |
| GET / POST | `/api/admin/captadores` | Consultar y registrar captadores |
| POST | `/api/admin/captadores/{id}/dispositivo` | Generar credencial |
| POST | `/api/admin/captadores/{id}/renovar-credencial` | Renovar credencial |
| GET | `/api/admin/estadisticas/prospectos-por-evento` | Estadísticas |
| GET | `/api/admin/prospectos/{id}/excel` | Reporte del evento |

Las operaciones administrativas utilizan `X-Admin-Key`. La participación y la recepción de prospectos utilizan `X-Device-Token`.

## API externa

Se utiliza `GET https://api.open-meteo.com/v1/forecast` con las coordenadas de la ciudad elegida y las variables `temperature_2m` y `weather_code`. La pantalla Eventos muestra temperatura, condición y hora informada por el proveedor. Un fallo no bloquea la captura ni la sincronización.

[Documentación de Open-Meteo](https://open-meteo.com/en/docs). Datos con atribución Open-Meteo, CC BY 4.0.

## Datos locales

Room utiliza la versión 6. La migración conserva fichas, eventos y gestiones anteriores. Los estados de una ficha son 0 pendiente, 1 sincronizada y 2 rechazada. La consulta del servidor no sobrescribe una captura pendiente de envío. Cada seguimiento recibe un UUID persistente.

WorkManager programa envíos con conexión de red y reintentos ante fallos temporales. También se puede solicitar sincronización manual desde Cuenta. Es necesario haber descargado el evento y vinculado el captador antes de trabajar sin internet.

Las gestiones guardadas sin conexión permanecen pendientes. Para enviarlas debe volver a configurarse la clave administrativa si terminó el proceso o se cerró sesión.

## Comprobaciones

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug compileDebugAndroidTestKotlin
```

Para ejecutar las pruebas de persistencia y migración con un emulador o teléfono conectado:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

El APK de revisión se genera en `app/build/outputs/apk/debug/`. La firma de publicación y la comprobación de cobertura exigida para la entrega se preparan por separado.
