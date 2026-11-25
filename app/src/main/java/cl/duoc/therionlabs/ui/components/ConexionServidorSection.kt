package cl.duoc.therionlabs.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.duoc.therionlabs.network.ConfiguracionConexion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ConexionServidorSection() {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var direccion by remember { mutableStateOf(ConfiguracionConexion.direccion) }
    var ocupado by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Conexión con el servidor", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            direccion,
            { direccion = it },
            label = { Text("Dirección del servidor") },
            singleLine = true,
            enabled = !ocupado,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = !ocupado,
            onClick = {
                ocupado = true
                alcance.launch {
                    try {
                        ConfiguracionConexion.configurar(contexto, direccion)
                        mensaje = "Dirección guardada."
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        mensaje = error.message ?: "Revisa la dirección del servidor."
                    } finally {
                        ocupado = false
                    }
                }
            },
        ) {
            Text(if (ocupado) "Guardando…" else "Guardar dirección")
        }
        mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
