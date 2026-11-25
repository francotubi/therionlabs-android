package cl.duoc.therionlabs.network

import retrofit2.Response
import retrofit2.http.GET

interface EstadisticasAdminApi {

    @GET("api/admin/estadisticas/prospectos-por-evento")
    suspend fun obtenerProspectosPorEvento(): Response<Map<String, Long>>
}
