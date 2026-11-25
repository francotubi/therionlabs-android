package cl.duoc.therionlabs.network

import android.content.Context
import cl.duoc.therionlabs.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VinculadorDispositivo(context: Context) {
    private val tokenStore = DispositivoTokenStore(context)
    private val database = AppDatabase.getDatabase(context.applicationContext)

    suspend fun vincular(
        usuarioId: String,
        token: String,
    ): String {

        require(usuarioId.isNotBlank()) {
            "No hay un usuario identificado."
        }

        require(token.isNotBlank()) {
            "Debes ingresar el token del dispositivo."
        }

        val respuesta = RetrofitClient.dispositivoApi.verificarDispositivo(token.trim())

        if (!respuesta.isSuccessful) {
            throw IllegalArgumentException(
                if (respuesta.code() == 401) "El token no es válido."
                else "No se pudo verificar el dispositivo."
            )
        }

        val dispositivo =
            requireNotNull(respuesta.body()) {
                "El servidor no devolvió la información del dispositivo."
            }

        require(
            dispositivo.autorizado &&
                dispositivo.captadorId.isNotBlank() &&
                dispositivo.identificadorLocal.isNotBlank()
        ) {
            "El dispositivo no está autorizado."
        }

        val captadorAnterior = tokenStore.obtenerCaptadorId(usuarioId)
        val codigoAnterior = tokenStore.obtenerCodigoCaptador(usuarioId)

        if (captadorAnterior != null && captadorAnterior != dispositivo.captadorId) {

            require(!codigoAnterior.isNullOrBlank()) {
                "No se pudo identificar al captador anterior."
            }

            val pendientes = database.prospectoDao().contarPendientes(codigoAnterior)

            require(pendientes == 0) {
                "No puedes cambiar de captador. " +
                    "$codigoAnterior tiene $pendientes prospectos " +
                    "pendientes de sincronización."
            }
        }

        tokenStore.guardarVinculacion(
            usuarioId = usuarioId,
            token = token.trim(),
            captadorId = dispositivo.captadorId,
            codigoCaptador = dispositivo.identificadorLocal,
        )

        return dispositivo.captadorId
    }

    suspend fun desvincular(usuarioId: String) {
        require(usuarioId.isNotBlank()) {
            "No hay un usuario identificado."
        }

        withContext(Dispatchers.IO) {
            val codigoCaptador = tokenStore.obtenerCodigoCaptador(usuarioId)

            require(!codigoCaptador.isNullOrBlank()) {
                "Este dispositivo no tiene un captador vinculado."
            }

            val pendientes = database.prospectoDao().contarPendientes(codigoCaptador)

            require(pendientes == 0) {
                "No puedes desvincular el dispositivo.\n" +
                    "$codigoCaptador tiene $pendientes prospectos " +
                    "pendientes de sincronización.\n\n" +
                    "Sincroniza los registros antes de continuar."
            }

            tokenStore.eliminarToken(usuarioId)
        }
    }
}
