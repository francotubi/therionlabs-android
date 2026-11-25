# Pruebas unitarias y cobertura

Comprobación del 25 de noviembre de 2026 con JDK 21, variante debug, JUnit 4, MockK, MockWebServer y JaCoCo 0.8.15.

- Pruebas ejecutadas: 66.
- Fallos: 0. Errores: 0. Omitidas: 0.
- Cobertura de líneas: **87.62 %** (1494 de 1705 líneas).
- Umbral comprobado por Gradle: 80 % de líneas.

| Capa | Líneas cubiertas / total | Cobertura de líneas |
| --- | --- | --- |
| `data/repository` | 458 / 468 | 97.86 % |
| `data/viewmodel` | 402 / 457 | 87.96 % |
| `domain` | 176 / 179 | 98.32 % |
| `network` | 372 / 458 | 81.22 % |
| `util` | 86 / 143 | 60.14 % |
| **Total** | **1494 / 1705** | **87.62 %** |

La medición incluye todas las clases de las capas indicadas. No se excluyen los adaptadores de red o utilidades Android que pertenecen a esas capas, aunque parte de su comportamiento se comprueba mediante pruebas instrumentadas. No mide Compose, entidades y DAO de Room, código generado, MainActivity ni la programación de WorkManager. La cobertura instrumentada no se suma al porcentaje unitario.

Las pruebas usan datos ficticios. Los DAO, transacciones y preferencias se sustituyen con MockK. Los contratos HTTP usan un servidor local MockWebServer. El cifrado real de Android Keystore y las migraciones de Room tienen pruebas independientes en `app/src/androidTest/`.

## Reproducción

Desde la carpeta del proyecto, con Android SDK y JDK configurados:

```powershell
.\gradlew.bat verificarCobertura lintDebug assembleDebug
```

`verificarCobertura` ejecuta `testDebugUnitTest`, genera `coberturaDebug` y falla si la cobertura de líneas queda por debajo del 80 %.

- Informe de cobertura: `app/build/reports/jacoco/coberturaDebug/html/index.html`.
- Informe de resultados: `app/build/reports/tests/testDebugUnitTest/index.html`.
- [XML de cobertura](Cobertura.xml).
- [Copia de los informes HTML y resultados JUnit](Informes-pruebas.zip): descomprimir y abrir `cobertura/index.html` o `pruebas/index.html`.

La APK release no incluye los archivos de prueba ni las dependencias de pruebas.
