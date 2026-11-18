package cl.duoc.therionlabs.network

import android.content.Context

class DispositivoTokenStore(context: Context) {

    private val preferencias = context.applicationContext
        .getSharedPreferences(
            "therionlabs_dispositivo",
            Context.MODE_PRIVATE
        )

    fun guardarToken(usuarioId: String, token: String) {
        require(usuarioId.isNotBlank())
        require(token.isNotBlank())

        preferencias.edit()
            .putString("device_token_$usuarioId", token.trim())
            .apply()
    }

    fun obtenerToken(usuarioId: String): String? {
        return preferencias
            .getString("device_token_$usuarioId", null)
            ?.takeIf { it.isNotBlank() }
    }

    fun eliminarToken(usuarioId: String) {
        preferencias.edit()
            .remove("device_token_$usuarioId")
            .apply()
    }
}
