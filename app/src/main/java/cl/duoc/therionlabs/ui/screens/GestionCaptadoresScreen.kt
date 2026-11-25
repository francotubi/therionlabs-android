package cl.duoc.therionlabs.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.duoc.therionlabs.network.*
import cl.duoc.therionlabs.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun GestionCaptadoresScreen(onVolver: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf("CAPTADOR") }
    var ocupado by remember { mutableStateOf(false) }
    var cuentas by remember { mutableStateOf(emptyList<CuentaRemota>()) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    suspend fun actualizar() {
        cuentas = RespuestasServidor.exigir(RetrofitClient.accesoApi.cuentas())
    }
    LaunchedEffect(Unit) {
        try {
            actualizar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mensaje = e.message
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Cuentas del equipo", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Cada persona ingresa con su usuario y contraseña. El código identifica las capturas del captador."
        )
        CampoEntrada("Nombre completo", nombre, { nombre = it }, enabled = !ocupado)
        CampoEntrada("Usuario", usuario, { usuario = it }, enabled = !ocupado)
        OutlinedTextField(
            clave,
            { clave = it },
            label = { Text("Contraseña inicial (mínimo 8 caracteres)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !ocupado,
            modifier = Modifier.fillMaxWidth(),
        )
        CampoSeleccion(
            "Perfil",
            rol,
            listOf("CAPTADOR", "ADMINISTRATIVO"),
            { rol = it },
            enabled = !ocupado,
        )
        if (rol == "CAPTADOR")
            CampoEntrada(
                "Código del captador (CAP-03)",
                codigo,
                { codigo = it.uppercase() },
                enabled = !ocupado,
            )
        Button(
            enabled = !ocupado && usuario.isNotBlank() && clave.length >= 8 && nombre.isNotBlank(),
            onClick = {
                ocupado = true
                mensaje = null
                alcance.launch {
                    try {
                        RespuestasServidor.exigir(
                            RetrofitClient.accesoApi.crear(
                                NuevaCuenta(
                                    usuario.trim(),
                                    clave,
                                    nombre.trim(),
                                    rol,
                                    if (rol == "CAPTADOR") codigo.trim() else null,
                                )
                            )
                        )
                        clave = ""
                        usuario = ""
                        nombre = ""
                        codigo = ""
                        mensaje = "Cuenta creada. Ya puede iniciar sesión en su teléfono."
                        actualizar()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        mensaje = e.message ?: "No se pudo crear la cuenta."
                    } finally {
                        ocupado = false
                    }
                }
            },
        ) {
            Text(if (ocupado) "Guardando…" else "Crear cuenta")
        }
        mensaje?.let { Text(it) }
        HorizontalDivider()
        Text("Cuentas registradas", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(
            enabled = !ocupado,
            onClick = {
                alcance.launch {
                    try {
                        actualizar()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        mensaje = e.message
                    }
                }
            },
        ) {
            Text("Actualizar cuentas")
        }
        cuentas.forEach { cuenta ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(cuenta.nombre, style = MaterialTheme.typography.titleMedium)
                    Text("Usuario: ${cuenta.usuario}")
                    Text(
                        if (cuenta.rol == "CAPTADOR") "Captador · ${cuenta.codigoCaptador}"
                        else "Administrativo"
                    )
                }
            }
        }
        TextButton(onClick = onVolver) { Text("Volver") }
    }
}
