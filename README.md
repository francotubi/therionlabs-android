# TherionLabs · Valparaíso

Aplicación Android para organizar eventos de captación, registrar prospectos y gestionar su seguimiento en la Región de Valparaíso. El backend Spring Boot centraliza los datos en MySQL y permite compartirlos entre dispositivos al actualizar o sincronizar.

## Integrantes

Diego Barra, Franco Tubi, Maicol García y Benjamín Osorio.

## Funcionalidades

- Cada persona inicia sesión con una cuenta creada en el servidor. La aplicación recibe automáticamente el perfil y el código de captador; no se copian claves administrativas ni tokens.
- Administración crea cuentas administrativas o de captador, asociando cada captador a un código único como CAP-03. Un código de captador existente puede asociarse a una cuenta si aún no tiene una.
- Administración crea, consulta y edita eventos. Puede borrar eventos sin historial o cerrarlos para terminar la captación y conservar los prospectos y seguimientos.
- Los captadores descargan eventos abiertos y guardan fichas en Room sin conexión. Las capturas previas al cierre de un evento pueden enviarse después; el servidor rechaza capturas nuevas posteriores al cierre.
- WorkManager envía las capturas pendientes al recuperar conexión. Los reintentos mantienen el UUID original para evitar duplicados. Los rechazos conservan el motivo y permiten corregir los datos.
- Administración consulta los prospectos por evento, edita fichas, registra seguimientos, consulta estadísticas y exporta reportes Excel. No se borran fichas con gestiones registradas.
- Los registros de eventos cerrados siguen disponibles desde Registros. La lista se actualiza mediante las opciones de actualización; no se utilizan notificaciones en tiempo real.
- La oferta académica está limitada a Valparaíso y Viña del Mar, con carreras según la sede.
- Cámara y ubicación son opcionales. Las fotos permanecen en el teléfono: enviar su ruta no transfiere el archivo a otros dispositivos.
- El clima se consulta mediante Retrofit a Open-Meteo; un fallo del proveedor no impide capturar ni sincronizar.

## Ejecución

1. Abrir este proyecto en Android Studio con JDK 21 y Android SDK 36, revisión menor 1.
2. Ejecutar el backend de [therionlabs-backend](https://github.com/francotubi/therionlabs-backend) con MySQL. Configurar `THERIONLABS_DB_URL`, `THERIONLABS_DB_USER`, `THERIONLABS_DB_PASSWORD` y `PORT` (8081 por defecto).
3. En la primera ejecución del backend, definir `THERIONLABS_ADMIN_USER` y `THERIONLABS_ADMIN_PASSWORD` para crear la cuenta administrativa inicial. No hay una contraseña de producción incluida en el código. Cambiar esas variables después no altera una cuenta ya creada.
4. Ejecutar la aplicación. La dirección inicial del emulador es `http://10.0.2.2:8081/`. Desde el inicio de sesión, Configurar servidor permite guardar otra dirección antes de descargar eventos o capturar fichas. Para un teléfono físico se necesita una dirección accesible del backend. En el despliegue público debe utilizarse HTTPS.
5. Ingresar con la cuenta administrativa del servidor. En Cuentas, crear el usuario, contraseña inicial, nombre, perfil y código del captador. No se admiten códigos duplicados entre cuentas.
6. El captador ingresa por primera vez con internet en su teléfono. El código y la credencial para sincronizar se guardan automáticamente. Descargar los eventos antes de trabajar sin conexión.

No se permite mezclar servidores en una instalación que ya tiene eventos o registros locales. Para probar otro servidor se utiliza una instalación independiente; los datos de la anterior no se borran automáticamente.

## Sesiones y trabajo sin conexión

El primer acceso valida las credenciales en el backend. El teléfono conserva un resumen de la contraseña con sal, nunca la contraseña original. El captador puede volver a ingresar sin red durante siete días desde su última validación. Un rechazo del servidor no activa el acceso sin conexión.

Las credenciales remotas vencen a los 30 días. El servidor conserva únicamente su resumen criptográfico. La credencial de captura se cifra mediante Android Keystore y permite enviar capturas pendientes aunque se haya cerrado la pantalla de sesión. Al cerrar sesión se revoca la credencial de acceso a la interfaz; la credencial de captura mantiene su vencimiento original para no dejar fichas pendientes sin enviar. Si vence, es necesario volver a ingresar con internet. Cada dispositivo obtiene credenciales independientes.

El perfil administrativo puede consultar datos locales y registrar gestiones pendientes sin red después de haber validado su cuenta. Las operaciones remotas necesitan una sesión conectada; las gestiones pendientes se envían al volver a ingresar con internet. Los estados se muestran como guardados en el dispositivo o en el servidor.

## Endpoints del microservicio

| Método | Ruta | Uso |
| --- | --- | --- |
| POST | `/api/acceso/ingresar` | Validar usuario y contraseña; obtener perfil, captador y sesión |
| POST | `/api/acceso/salir` | Revocar la sesión de acceso |
| GET / POST | `/api/admin/cuentas` | Consultar y crear cuentas |
| GET | `/api/eventos?incluirEliminados=true` | Descargar eventos, cierres y bajas |
| POST | `/api/eventos` | Crear evento |
| PUT / DELETE | `/api/eventos/{id}` | Editar o borrar un evento vacío |
| POST | `/api/eventos/{id}/cerrar` | Terminar la captación conservando el historial |
| POST | `/api/participaciones/unirse` | Registrar participación con identidad del captador |
| POST | `/api/prospectos/sync` | Enviar una captura con UUID estable |
| GET | `/api/admin/eventos/{id}/prospectos` | Descargar fichas del evento |
| PUT / DELETE | `/api/admin/prospectos/{id}` | Editar o dar de baja una ficha |
| GET / POST | `/api/admin/prospectos/{id}/seguimientos` | Consultar y guardar gestiones |
| GET | `/api/admin/estadisticas/prospectos-por-evento` | Consultar totales |
| GET | `/api/admin/prospectos/{id}/excel` | Descargar el reporte Excel |

Las operaciones administrativas reciben `Authorization: Bearer ...`. La participación y el envío de prospectos reciben `X-Device-Token`, gestionado automáticamente por la aplicación. El servidor determina el rol de la cuenta y verifica la identidad del captador.

## API externa

Se consume `GET https://api.open-meteo.com/v1/forecast` con coordenadas de Valparaíso o Viña del Mar y las variables `temperature_2m` y `weather_code`. Se muestran temperatura, condición y hora informada por el proveedor.

[Documentación de Open-Meteo](https://open-meteo.com/en/docs). Datos: Open-Meteo, CC BY 4.0.

## Persistencia y arquitectura

Interfaz con Jetpack Compose y navegación por perfiles; ViewModel para el estado, Repository para acceso a datos, Retrofit para la red y Room 6 para usuarios validados, eventos, prospectos y seguimientos. La migración conserva los datos anteriores. La nueva autenticación conserva los identificadores locales de usuarios existentes para no romper las relaciones con el historial.

Las fichas usan 0 pendiente, 1 sincronizada y 2 rechazada. Cada captura y seguimiento mantiene su UUID. Las actualizaciones usan inserción o actualización sin reemplazar las filas relacionadas. La caché no sobrescribe una captura pendiente con una copia remota.

## Pruebas

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

Se comprueban validaciones, permisos, contratos HTTP, migración de Room, conservación de identidades, acceso sin conexión, rechazo de credenciales y cifrado de tokens. Los informes se generan en `app/build/reports/`. La cobertura del 80 % solicitada por la pauta aún debe medirse; el número de pruebas no demuestra ese porcentaje.

## APK firmado y evidencia de entrega

El APK actual es de revisión y utiliza la firma de depuración. Para la entrega debe generarse una compilación release con una llave `.jks` propia desde Build > Generate Signed App Bundle or APK, completar su configuración y comprobar la instalación y la firma con `apksigner verify`.

Antes de entregar falta incorporar aquí las capturas verificadas de la generación del APK firmado y de la llave exigidas por la pauta, la ubicación del APK final, la evidencia de planificación en Trello y el resultado de cobertura. No se incluyen contraseñas ni credenciales del servidor en el repositorio. El despliegue EC2 y la prueba con dos teléfonos físicos siguen pendientes.
