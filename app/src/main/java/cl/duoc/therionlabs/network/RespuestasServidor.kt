package cl.duoc.therionlabs.network

import com.google.gson.Gson
import retrofit2.Response

object RespuestasServidor {
    fun mensaje(cuerpo: String?, codigo: Int): String {
        val mensaje = runCatching {
            Gson().fromJson(cuerpo, ErrorServidor::class.java)?.let {
                it.mensaje ?: it.message ?: it.detail
            }
        }
            .getOrNull()
        return mensaje?.takeIf { it.isNotBlank() }
            ?: when (codigo) {
                401 -> "La credencial del dispositivo no es válida. Vuelve a vincularlo."
                403 -> "No tienes autorización. Revisa la clave administrativa."
                404 -> "El registro ya no está disponible en el servidor."
                409 -> "El servidor detectó un conflicto. Actualiza los registros."
                else -> "No se pudo completar la operación. HTTP $codigo"
            }
    }

    fun <T> exigir(respuesta: Response<T>): T {
        if (!respuesta.isSuccessful)
            throw IllegalArgumentException(
                mensaje(respuesta.errorBody()?.string(), respuesta.code())
            )
        return requireNotNull(respuesta.body()) { "El servidor devolvió una respuesta vacía." }
    }

    fun confirmar(respuesta: Response<*>) {
        if (!respuesta.isSuccessful)
            throw IllegalArgumentException(
                mensaje(respuesta.errorBody()?.string(), respuesta.code())
            )
    }

    private data class ErrorServidor(
        val mensaje: String?,
        val message: String?,
        val detail: String?,
    )
}
