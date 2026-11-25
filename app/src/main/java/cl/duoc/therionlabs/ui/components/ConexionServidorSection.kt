package cl.duoc.therionlabs.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.duoc.therionlabs.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ConexionServidorSection(administrativo: Boolean, alGuardar: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var direccion by remember { mutableStateOf(ConfiguracionConexion.direccion) }
    var clave by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Conexión con el servidor", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            direccion,
            { direccion = it },
            label = { Text("Dirección del servidor") },
            singleLine = true,
            enabled = !ocupado,
            modifier = Modifier.fillMaxWidth(),
        )
        if (administrativo) {
            OutlinedTextField(
                clave,
                { clave = it },
                label = { Text("Clave administrativa del servidor") },
                singleLine = true,
                enabled = !ocupado,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (ConfiguracionConexion.claveAdministrativa.isBlank())
                    "La clave se solicita una vez por sesión."
                else "Administración conectada durante esta sesión.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            enabled = !ocupado,
            onClick = {
                ocupado = true
                mensaje = null
                alcance.launch {
                    try {
                        ConfiguracionConexion.configurar(contexto, direccion)
                        if (administrativo && clave.isNotBlank()) {
                            RespuestasServidor.exigir(
                                RetrofitClient.captadorAdminApi.listarCaptadores(clave.trim())
                            )
                            ConfiguracionConexion.autorizar(clave)
                        }
                        clave = ""
                        mensaje = "Configuración guardada."
                        alGuardar()
                    } catch (cancelacion: CancellationException) {
                        throw cancelacion
                    } catch (error: Exception) {
                        mensaje =
                            error.message
                                ?: "No se pudo conectar. Revisa la dirección y la conexión."
                    } finally {
                        ocupado = false
                    }
                }
            },
        ) {
            Text(if (ocupado) "Conectando…" else "Guardar conexión")
        }
        mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
