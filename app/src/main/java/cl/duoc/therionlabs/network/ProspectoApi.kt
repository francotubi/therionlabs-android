package cl.duoc.therionlabs.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ProspectoApi {

    @POST("api/prospectos/sync")
    suspend fun sincronizarProspecto(
        @Header("X-Device-Token") token: String,
        @Body prospecto: ProspectoSyncRequest
    ): Response<RespuestaSync>
}

data class RespuestaSync(
    val prospectoId: String?,
    val estado: String,
    val mensaje: String
)
