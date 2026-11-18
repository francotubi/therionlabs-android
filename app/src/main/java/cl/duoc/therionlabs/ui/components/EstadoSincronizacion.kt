
package cl.duoc.therionlabs.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.duoc.therionlabs.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun EstadoSincronizacionSection(usuarioId: String) {

    val contexto = LocalContext.current

    var pendientes by remember(usuarioId) {
        mutableIntStateOf(0)
    }

    var sincronizados by remember(usuarioId) {
        mutableIntStateOf(0)
    }

    var cargando by remember(usuarioId) {
        mutableStateOf(true)
    }

    LaunchedEffect(contexto, usuarioId) {

        val dao = AppDatabase.getDatabase(contexto).prospectoDao()

        while (isActive) {
            try {
                pendientes = dao.contarPendientes(usuarioId)
                sincronizados = dao.contarSincronizados(usuarioId)

            } catch (error: CancellationException) {
                throw error

            } catch (_: Exception) {
                // Conservamos los últimos valores disponibles.
            }

            cargando = false
            delay(3000)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Estado de sincronización",
            style = MaterialTheme.typography.titleMedium
        )

        if (cargando) {
            CircularProgressIndicator()
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Pendientes",
                        style = MaterialTheme.typography.labelMedium
                    )

                    Text(
                        text = "$pendientes",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Sincronizados",
                        style = MaterialTheme.typography.labelMedium
                    )

                    Text(
                        text = "$sincronizados",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }
    }
}
