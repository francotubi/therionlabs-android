package cl.duoc.therionlabs.network

import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface ParticipacionApi {

    @POST("api/participaciones/unirse")
    suspend fun unirseEvento(
        @Header("X-Device-Token") token: String,
        @Query("captadorId") captadorId: String,
        @Query("eventoId") eventoId: String,
    ): Response<ParticipacionResponse>
}

data class ParticipacionResponse(
    val participacionId: String,
    val activo: Boolean,
)
