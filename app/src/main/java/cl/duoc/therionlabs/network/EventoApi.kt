package cl.duoc.therionlabs.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface EventoApi {

    @GET("api/eventos")
    suspend fun obtenerEventos(): Response<List<EventoResponse>>

    @POST("api/eventos")
    suspend fun crearEvento(
        @Body evento: CrearEventoRequest
    ): Response<EventoResponse>
}

data class CrearEventoRequest(
    val nombre: String,
    val colegio: String,
    val fecha: String,
    val actividad: String,
    val comuna: String,
    val activo: Boolean = true
)

data class EventoResponse(
    val id: String,
    val nombre: String,
    val colegio: String,
    val fecha: String,
    val actividad: String,
    val comuna: String?,
    val activo: Boolean
)
