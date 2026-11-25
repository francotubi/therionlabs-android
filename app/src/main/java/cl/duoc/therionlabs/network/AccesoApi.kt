package cl.duoc.therionlabs.network

import retrofit2.Response
import retrofit2.http.*

data class SolicitudAcceso(val usuario: String, val clave: String)

data class CuentaRemota(
    val id: String,
    val usuario: String,
    val nombre: String,
    val rol: String,
    val captadorId: String?,
    val codigoCaptador: String?,
    val activo: Boolean,
)

data class RespuestaAcceso(
    val cuenta: CuentaRemota,
    val token: String,
    val vence: Long,
    val tokenCaptura: String?,
)

data class NuevaCuenta(
    val usuario: String,
    val clave: String,
    val nombre: String,
    val rol: String,
    val codigoCaptador: String?,
)

interface AccesoApi {
    @POST("api/acceso/ingresar")
    suspend fun ingresar(@Body datos: SolicitudAcceso): Response<RespuestaAcceso>

    @POST("api/acceso/salir")
    suspend fun salir(@Header("Authorization") autorizacion: String): Response<Unit>

    @GET("api/admin/cuentas") suspend fun cuentas(): Response<List<CuentaRemota>>

    @POST("api/admin/cuentas") suspend fun crear(@Body datos: NuevaCuenta): Response<CuentaRemota>
}
