package cl.duoc.therionlabs.network

import android.content.Context

class DispositivoTokenStore(context: Context, nombreAlmacen: String = "therionlabs_dispositivo") {

    private val preferencias =
        context.applicationContext.getSharedPreferences(
            nombreAlmacen,
            Context.MODE_PRIVATE,
        )

    fun guardarToken(usuarioId: String, token: String) {
        require(usuarioId.isNotBlank())
        require(token.isNotBlank())

        preferencias
            .edit()
            .putString("device_token_$usuarioId", AlmacenCredenciales.cifrar(token.trim()))
            .apply()
    }

    fun obtenerToken(usuarioId: String): String? {
        return AlmacenCredenciales.leer(preferencias.getString("device_token_$usuarioId", null))
            ?.takeIf { it.isNotBlank() }
    }

    fun eliminarToken(usuarioId: String) {
        val codigoActual = obtenerCodigoCaptador(usuarioId)
        val editor =
            preferencias
                .edit()
                .remove("device_token_$usuarioId")
                .remove("captador_id_$usuarioId")
                .remove("captador_codigo_$usuarioId")
        if (codigoActual != null) {
            editor.remove("device_token_por_codigo_$codigoActual")
        }
        editor.apply()
    }

    fun guardarVinculacion(
        usuarioId: String,
        token: String,
        captadorId: String,
        codigoCaptador: String,
    ) {
        require(usuarioId.isNotBlank())
        require(token.isNotBlank())
        require(captadorId.isNotBlank())
        require(codigoCaptador.isNotBlank())

        val codigoAnterior = obtenerCodigoCaptador(usuarioId)
        val editor = preferencias.edit()

        if (codigoAnterior != null && codigoAnterior != codigoCaptador) {
            editor.remove("device_token_por_codigo_$codigoAnterior")
        }

        editor
            .putString("device_token_$usuarioId", AlmacenCredenciales.cifrar(token.trim()))
            .putString("captador_id_$usuarioId", captadorId)
            .putString("captador_codigo_$usuarioId", codigoCaptador)
            .putString(
                "device_token_por_codigo_$codigoCaptador",
                AlmacenCredenciales.cifrar(token.trim()),
            )
            .apply()
    }

    fun obtenerCaptadorId(usuarioId: String): String? {
        return preferencias.getString("captador_id_$usuarioId", null)?.takeIf { it.isNotBlank() }
    }

    fun obtenerCodigoCaptador(usuarioId: String): String? {
        return preferencias.getString("captador_codigo_$usuarioId", null)?.takeIf {
            it.isNotBlank()
        }
    }

    fun obtenerTokenPorCodigo(codigoCaptador: String): String? {
        return AlmacenCredenciales.leer(
                preferencias.getString("device_token_por_codigo_$codigoCaptador", null)
            )
            ?.takeIf {
                it.isNotBlank()
            }
    }
}
