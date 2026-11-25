# Verificación

## Comprobaciones realizadas

- APK de revisión 2.2 generado e instalado en el emulador sin borrar los datos anteriores.
- 14 pruebas unitarias de Android sin fallos: validaciones, roles y contratos HTTP.
- 6 pruebas instrumentadas ejecutadas en el emulador sin fallos: migración de Room, persistencia, cifrado, asignación automática de captador, acceso sin red y conservación de la identidad de usuarios anteriores.
- El acceso sin conexión requiere una cuenta previamente validada y se limita a siete días. Un rechazo HTTP del servidor no utiliza la caché como alternativa.
- Análisis de Android sin errores. Las advertencias restantes se consultan en app/build/reports/lint-results-debug.html.
- Backend: 9 pruebas de integración sin fallos, incluyendo cuentas, permisos, revocación, vencimiento de sesiones y cierre de eventos.
- MySQL real: 9 comprobaciones nuevas correctas de acceso y cierre, incluyendo el envío único de capturas anteriores al cierre y la conservación de la ficha creada desde Android.
- Flujo anterior comprobado manualmente con el emulador: creación del evento, ficha guardada sin red, envío automático al recuperar Wi-Fi, consulta administrativa y seguimiento guardado en el servidor.
- Código fuente de Android y backend sin comentarios; backend sin README.

## Pruebas pendientes de entrega

1. Desplegar en EC2 con HTTPS y configuración privada de credenciales.
2. Probar la versión nueva en dos teléfonos físicos contra el mismo servidor.
3. Medir y completar la cobertura del 80 % de lógica exigida por la pauta. El número de pruebas no acredita esa cobertura.
4. Generar el APK release con su llave de entrega y agregar las capturas verificadas de firma y evidencia de Trello al README.

Esta compilación conserva la firma de depuración para revisión. No se presenta como APK final de publicación.
