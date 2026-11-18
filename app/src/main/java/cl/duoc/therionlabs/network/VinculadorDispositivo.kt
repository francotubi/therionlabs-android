package cl.duoc.therionlabs.network

import android.content.Context

class VinculadorDispositivo(context: Context) {

    private val tokenStore = DispositivoTokenStore(context)

    suspend fun vincular(
        usuarioId: String,
        token: String
    ): String {

        require(usuarioId.isNotBlank()) {
            "No hay un usuario identificado."
        }

        require(token.isNotBlank()) {
            "Debes ingresar el token del dispositivo."
        }

        // Verificar la credencial con Spring Boot
        val respuesta = RetrofitClient.dispositivoApi
            .verificarDispositivo(token.trim())

        if (!respuesta.isSuccessful) {
            throw IllegalArgumentException(
                if (respuesta.code() == 401)
                    "El token no es válido."
                else
                    "No se pudo verificar el dispositivo."
            )
        }

        val dispositivo = requireNotNull(respuesta.body()) {
            "El servidor no devolvió la información del dispositivo."
        }

        // Comprobar que el token pertenece al usuario local
        require(dispositivo.autorizado &&
                dispositivo.identificadorLocal == usuarioId) {
            "Este token no corresponde al usuario $usuarioId."
        }

        // Guardar únicamente después de verificar
        tokenStore.guardarToken(usuarioId, token.trim())

        // UUID del captador en MySQL
        return dispositivo.captadorId
    }
}
