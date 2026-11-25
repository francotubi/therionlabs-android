package cl.duoc.therionlabs.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.duoc.therionlabs.network.*
import kotlinx.coroutines.CancellationException

@Composable
fun ClimaSection() {
    var ciudad by remember { mutableStateOf("Valparaíso") }
    var datos by remember { mutableStateOf<ClimaActual?>(null) }
    var error by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    var consulta by remember { mutableIntStateOf(0) }
    LaunchedEffect(ciudad, consulta) {
        datos = null
        error = false
        cargando = true
        try {
            datos =
                ServicioClima.api
                    .consultar(
                        if (ciudad == "Valparaíso") -33.0472 else -33.0245,
                        if (ciudad == "Valparaíso") -71.6127 else -71.5518,
                    )
                    .current
            error = datos == null
        } catch (cancelacion: CancellationException) {
            throw cancelacion
        } catch (_: Exception) {
            error = true
        } finally {
            cargando = false
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Clima para actividades", style = MaterialTheme.typography.titleMedium)
            CampoSeleccion("Ciudad", ciudad, listOf("Valparaíso", "Viña del Mar"), { ciudad = it })
            if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
            datos?.let {
                Text("${it.temperature_2m} °C · ${ServicioClima.descripcion(it.weather_code)}")
                Text(
                    "Actualización: ${it.time.replace('T',' ')}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (error)
                Text(
                    "El clima no está disponible. Puedes continuar trabajando.",
                    style = MaterialTheme.typography.bodySmall,
                )
            Text("Datos: Open-Meteo · CC BY 4.0", style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { consulta++ }, enabled = !cargando) { Text("Actualizar clima") }
        }
    }
}
