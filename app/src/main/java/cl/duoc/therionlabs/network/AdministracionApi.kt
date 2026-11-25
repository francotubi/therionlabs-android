package cl.duoc.therionlabs.network

import retrofit2.Response
import retrofit2.http.*

interface AdministracionApi {
    @GET("api/admin/eventos/{eventoId}/prospectos")
    suspend fun prospectos(
        @Path("eventoId") eventoId: String,
        @Query("incluirEliminados") incluirEliminados: Boolean = true,
    ): Response<List<ProspectoRemoto>>

    @PUT("api/admin/prospectos/{id}")
    suspend fun actualizar(
        @Path("id") id: String,
        @Body datos: ProspectoSyncRequest,
    ): Response<ProspectoRemoto>

    @DELETE("api/admin/prospectos/{id}")
    suspend fun eliminar(@Path("id") id: String): Response<Unit>

    @GET("api/admin/prospectos/{id}/seguimientos")
    suspend fun seguimientos(@Path("id") id: String): Response<List<SeguimientoRemoto>>

    @POST("api/admin/prospectos/{id}/seguimientos")
    suspend fun guardarGestion(
        @Path("id") id: String,
        @Body datos: GestionSolicitud,
    ): Response<SeguimientoRemoto>
}

data class GestionSolicitud(
    val id: String,
    val responsable: String,
    val estado: String,
    val canal: String,
    val observacion: String,
)

data class SeguimientoRemoto(
    val id: String,
    val prospectoId: String,
    val responsable: String,
    val estado: String,
    val canal: String,
    val observacion: String,
    val fecha: String,
)
