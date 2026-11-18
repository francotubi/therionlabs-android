package cl.duoc.therionlabs.network

import android.content.Context
import java.io.IOException

class ParticipacionEventoClient(context: Context) {

    private val tokenStore = DispositivoTokenStore(context)

    suspend fun unirseEvento(
        usuarioId: String,
        eventoId: String
    ) {
        val token = tokenStore.obtenerToken(usuarioId)
            ?: throw IllegalArgumentException(
                "Primero vincula tu dispositivo en Mi cuenta."
            )

        val verificacion = RetrofitClient.dispositivoApi
            .verificarDispositivo(token)

        if (!verificacion.isSuccessful) {
            val codigo = verificacion.code()
            if (codigo == 429 || codigo in 500..599) {
                throw IOException(
                    "Error temporal al verificar dispositivo. HTTP $codigo"
                )
            }

            throw IllegalArgumentException(
                "No se pudo verificar el dispositivo. HTTP $codigo"
            )
        }


        val dispositivo = requireNotNull(verificacion.body()) {
            "El servidor no devolvió la identidad del captador."
        }

        require(
            dispositivo.autorizado &&
                    dispositivo.identificadorLocal == usuarioId
        ) {
            "El dispositivo no corresponde a $usuarioId."
        }

        // Registrar la participación en Spring Boot
        val respuesta = RetrofitClient.participacionApi.unirseEvento(
            captadorId = dispositivo.captadorId,
            eventoId = eventoId
        )

        if (!respuesta.isSuccessful) {
            val codigo = respuesta.code()
            if (codigo == 429 || codigo in 500..599) {
                throw IOException(
                    "Error temporal al registrar participación. HTTP $codigo"
                )
            }
            throw IllegalArgumentException(
                "No se pudo registrar la participación. HTTP $codigo"
            )
        }

    }
}
