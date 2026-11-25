package cl.duoc.therionlabs.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cl.duoc.therionlabs.network.ExcelDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ExportarExcelBoton(eventoId: String, ocupado: Boolean) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var descargando by remember { mutableStateOf(false) }
    val selector =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
        ) { uri ->
            if (uri != null) {
                descargando = true
                alcance.launch {
                    try {
                        ExcelDownloader.descargarYGuardar(contexto, eventoId, uri)
                        Toast.makeText(
                                contexto,
                                "Excel descargado correctamente",
                                Toast.LENGTH_LONG,
                            )
                            .show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(
                                contexto,
                                e.message ?: "No se pudo descargar el reporte.",
                                Toast.LENGTH_LONG,
                            )
                            .show()
                    } finally {
                        descargando = false
                    }
                }
            }
        }
    Button(
        onClick = { selector.launch("TherionLabs_Evento_$eventoId.xlsx") },
        enabled = !ocupado && !descargando,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (descargando) "Descargando…" else "Exportar Excel")
    }
}
