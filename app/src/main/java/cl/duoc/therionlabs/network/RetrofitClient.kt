package cl.duoc.therionlabs.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private var direccionActual = ""
    private var instancia: Retrofit? = null

    @Synchronized
    private fun conexion(): Retrofit {
        if (instancia == null || direccionActual != ConfiguracionConexion.direccion) {
            direccionActual = ConfiguracionConexion.direccion
            instancia =
                Retrofit.Builder()
                    .baseUrl(direccionActual)
                    .client(
                        OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .addInterceptor { cadena ->
                                val solicitud = cadena.request()
                                val ruta = solicitud.url().encodedPath()
                                val clave = ConfiguracionConexion.claveAdministrativa
                                val admin =
                                    ruta.startsWith("/api/admin/") ||
                                        (ruta.startsWith("/api/eventos") &&
                                            solicitud.method() !in listOf("GET", "HEAD"))
                                cadena.proceed(
                                    if (
                                        admin &&
                                            clave.isNotBlank() &&
                                            solicitud.header("X-Admin-Key").isNullOrBlank()
                                    )
                                        solicitud.newBuilder().header("X-Admin-Key", clave).build()
                                    else solicitud
                                )
                            }
                            .build()
                    )
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
        }
        return instancia!!
    }

    val prospectoApi
        get() = conexion().create(ProspectoApi::class.java)

    val eventoApi
        get() = conexion().create(EventoApi::class.java)

    val participacionApi
        get() = conexion().create(ParticipacionApi::class.java)

    val dispositivoApi
        get() = conexion().create(DispositivoApi::class.java)

    val excelApi
        get() = conexion().create(ExcelApi::class.java)

    val captadorAdminApi
        get() = conexion().create(CaptadorAdminApi::class.java)

    val estadisticasAdminApi
        get() = conexion().create(EstadisticasAdminApi::class.java)

    val administracionApi
        get() = conexion().create(AdministracionApi::class.java)
}
