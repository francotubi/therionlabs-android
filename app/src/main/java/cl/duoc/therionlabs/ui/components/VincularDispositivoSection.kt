package cl.duoc.therionlabs.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.duoc.therionlabs.network.VinculadorDispositivo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import cl.duoc.therionlabs.worker.SyncScheduler

@Composable
fun VincularDispositivoSection(usuarioId: String) {

    val contexto = LocalContext.current
    val vinculador = remember(contexto) {
        VinculadorDispositivo(contexto)
    }

    val scope = rememberCoroutineScope()

    var token by remember(usuarioId) {
        mutableStateOf("")
    }

    var verificando by remember {
        mutableStateOf(false)
    }

    var mensaje by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Vincular dispositivo",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Ingresa la credencial asignada a $usuarioId.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token del dispositivo") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !verificando,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                verificando = true
                mensaje = null

                scope.launch {
                    try {

                        vinculador.vincular(
                            usuarioId = usuarioId,
                            token = token
                        )

                        try {
                            SyncScheduler.solicitarSincronizacion(contexto)
                        } catch (error: Exception) {
                            android.util.Log.e(
                                "TherionLabsSync",
                                "No se pudo programar la sincronización",
                                error
                            )
                        }

                        token = ""
                        mensaje = "Dispositivo vinculado correctamente."


                    } catch (e: CancellationException) {
                        throw e

                    } catch (e: Exception) {
                        mensaje = e.message
                            ?: "No se pudo vincular el dispositivo."

                    } finally {
                        verificando = false
                    }
                }
            },
            enabled = token.isNotBlank() && !verificando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (verificando)
                    "Verificando..."
                else
                    "Vincular dispositivo"
            )
        }

        mensaje?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
