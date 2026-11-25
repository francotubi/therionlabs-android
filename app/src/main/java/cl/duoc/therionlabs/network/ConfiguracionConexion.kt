package cl.duoc.therionlabs.network

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URI

object ConfiguracionConexion {
    var direccion by mutableStateOf("http://10.0.2.2:8081/")
        private set

    var claveAdministrativa by mutableStateOf("")
        private set

    fun inicializar(contexto: Context) {
        direccion =
            contexto
                .getSharedPreferences("conexion", Context.MODE_PRIVATE)
                .getString("direccion", direccion) ?: direccion
    }

    suspend fun configurar(contexto: Context, nueva: String) {
        val url = normalizarDireccion(nueva)
        require(
            url == direccion ||
                (cl.duoc.therionlabs.data.local.AppDatabase.getDatabase(contexto)
                    .prospectoDao()
                    .obtenerParaExportacion()
                    .isEmpty() &&
                    cl.duoc.therionlabs.data.local.AppDatabase.getDatabase(contexto)
                        .operacionDao()
                        .listaEventos()
                        .isEmpty())
        ) {
            "Para cambiar de servidor usa una instalación sin registros locales. Así no se mezclan datos de servidores distintos."
        }
        if (direccion != url) cerrarSesion()
        direccion = url
        contexto
            .getSharedPreferences("conexion", Context.MODE_PRIVATE)
            .edit()
            .putString("direccion", url)
            .apply()
    }

    fun autorizar(clave: String) {
        claveAdministrativa = clave.trim()
    }

    fun cerrarSesion() {
        claveAdministrativa = ""
    }

    fun exigirClave(): String =
        claveAdministrativa.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException(
                "Configura la clave administrativa en Cuenta para conectar con el servidor."
            )

    fun normalizarDireccion(valor: String): String {
        val uri = runCatching { URI(valor.trim()) }.getOrNull()
        require(
            uri != null &&
                uri.scheme in listOf("http", "https") &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                uri.path.orEmpty() in listOf("", "/")
        ) {
            "Usa la dirección del servidor, por ejemplo http://10.0.2.2:8081/"
        }
        return valor.trim().trimEnd('/') + "/"
    }
}
