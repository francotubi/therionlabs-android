package cl.duoc.therionlabs.network

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URI

object ConfiguracionConexion {
    var direccion by mutableStateOf("https://184.192.154.68/")
        private set

    var credencialSesion by mutableStateOf("")
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

    var rolSesion by mutableStateOf("")
        private set

    fun autorizar(token: String, rol: String) {
        credencialSesion = token
        rolSesion = rol
    }

    fun exigirSesion(): String =
        credencialSesion.takeIf { it.isNotBlank() && rolSesion == "ADMINISTRATIVO" }
            ?: throw IllegalArgumentException(
                "Vuelve a iniciar sesión con internet para administrar el servidor."
            )

    fun cerrarSesion() {
        credencialSesion = ""
        rolSesion = ""
    }

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
            "Usa la dirección del servidor, por ejemplo https://184.192.154.68/"
        }
        return valor.trim().trimEnd('/') + "/"
    }
}
