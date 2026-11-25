# Verificación

## Comprobaciones automatizadas

- Compilación del APK de revisión: correcta.
- Pruebas unitarias: 14 ejecutadas, sin fallos. Incluyen validaciones, perfiles, mensajes del servidor, configuración de conexión, contratos Retrofit, identidad de participación y mapeo de fichas.
- Análisis de Android: sin errores. Las advertencias restantes incluyen dependencias anteriores, recursos sin uso y recomendaciones de estilo.
- Pruebas instrumentadas de Room: compiladas. Su ejecución requiere un teléfono o emulador conectado.
- Migración 5 a 6: comparación SQL con el esquema generado por Room, incluyendo columnas, valores predeterminados, claves e índices. Se conserva una ficha con estado rechazado y una gestión anterior.

## Comprobaciones pendientes en dispositivo

1. Iniciar el backend actualizado con MySQL y configurar su dirección y clave en Cuenta.
2. Crear un evento desde administración. Comprobar que otro dispositivo lo descarga.
3. Registrar y vincular al captador. Descargar el evento antes de desconectar internet.
4. Capturar una ficha sin conexión y verificar que permanece pendiente tras cerrar y abrir la aplicación.
5. Recuperar la conexión y comprobar que la ficha se sincroniza una sola vez.
6. Consultar la ficha desde administración en otra instalación.
7. Editar la ficha y actualizar el listado.
8. Registrar una gestión y comprobar su recepción en el servidor. Si no hay conexión, debe permanecer pendiente.
9. Repetir un RUT en el mismo evento desde otro captador. El rechazo debe conservar el motivo.
10. Corregir la ficha rechazada y solicitar sincronización nuevamente.
11. Comprobar bajas, restricciones por rol, cámara, ubicación y exportación a Excel.
12. Verificar la consulta de clima y que su fallo no impida registrar fichas.

## Entrega final

Esta versión es de revisión. Todavía deben verificarse la integración con MySQL y el despliegue, medir y completar la cobertura requerida, y generar la firma de publicación.
