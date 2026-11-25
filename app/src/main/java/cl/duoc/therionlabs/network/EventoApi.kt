package cl.duoc.therionlabs.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface EventoApi {
    @PUT("api/eventos/{id}")
    suspend fun actualizarEvento(
        @Path("id") id: String,
        @Body evento: CrearEventoRequest,
    ): Response<EventoResponse>

    @DELETE("api/eventos/{id}") suspend fun eliminarEvento(@Path("id") id: String): Response<Unit>

    @GET("api/eventos")
    suspend fun obtenerEventos(
        @Query("incluirEliminados") incluirEliminados: Boolean = true
    ): Response<List<EventoResponse>>

    @POST("api/eventos")
    suspend fun crearEvento(@Body evento: CrearEventoRequest): Response<EventoResponse>
}

data class CrearEventoRequest(
    val nombre: String,
    val colegio: String,
    val fecha: String,
    val actividad: String,
    val comuna: String,
    val activo: Boolean = true,
)

data class EventoResponse(
    val id: String,
    val nombre: String,
    val colegio: String,
    val fecha: String,
    val actividad: String,
    val comuna: String?,
    val activo: Boolean,
    val eliminado: Boolean = false,
)
