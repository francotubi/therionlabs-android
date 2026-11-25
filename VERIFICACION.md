# Verificación

## Comprobaciones realizadas

- Versión 2.2 de revisión probada en el emulador sin borrar los datos anteriores. La versión 2.3 configura por defecto la dirección HTTPS de EC2 y la firma release.
- 14 pruebas unitarias de Android sin fallos: validaciones, roles y contratos HTTP.
- 6 pruebas instrumentadas ejecutadas en el emulador sin fallos: migración de Room, persistencia, cifrado, asignación automática de captador, acceso sin red y conservación de la identidad de usuarios anteriores.
- El acceso sin conexión requiere una cuenta previamente validada y se limita a siete días. Un rechazo HTTP del servidor no utiliza la caché como alternativa.
- Análisis de Android sin errores. Las advertencias restantes se consultan en app/build/reports/lint-results-debug.html.
- Backend: 9 pruebas de integración sin fallos, incluyendo cuentas, permisos, revocación, vencimiento de sesiones y cierre de eventos.
- MySQL real: 9 comprobaciones nuevas correctas de acceso y cierre, incluyendo el envío único de capturas anteriores al cierre y la conservación de la ficha creada desde Android.
- Flujo anterior comprobado manualmente con el emulador: creación del evento, ficha guardada sin red, envío automático al recuperar Wi-Fi, consulta administrativa y seguimiento guardado en el servidor.
- Código fuente de Android y backend sin comentarios; backend sin README.

## Pruebas pendientes de entrega

1. Despliegue en EC2 realizado con Ubuntu, MySQL, servicio systemd y Nginx. HTTPS público verificado con respuesta 200 de /api/eventos y certificado válido para la Elastic IP 184.192.154.68. Falta ejecutar la prueba de renovación automática.
2. Probar la versión nueva en dos teléfonos físicos contra el mismo servidor.
3. Medir y completar la cobertura del 80 % de lógica exigida por la pauta. El número de pruebas no acredita esa cobertura.
4. Agregar las capturas verificadas de firma y evidencia de Trello al README.

La llave de firma release y sus credenciales se conservan fuera de Git. La comprobación HTTPS del endpoint público no acredita por sí sola el flujo completo de acceso y captura en dos teléfonos.
