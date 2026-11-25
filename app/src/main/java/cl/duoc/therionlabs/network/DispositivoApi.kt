package cl.duoc.therionlabs.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface DispositivoApi {

    @GET("api/dispositivos/verificar")
    suspend fun verificarDispositivo(
        @Header("X-Device-Token") token: String
    ): Response<VerificacionDispositivoResponse>
}

data class VerificacionDispositivoResponse(
    val dispositivoId: String,
    val captadorId: String,
    val identificadorLocal: String,
    val autorizado: Boolean,
)
