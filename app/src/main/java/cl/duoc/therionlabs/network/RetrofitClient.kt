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
                                val token = ConfiguracionConexion.credencialSesion
                                cadena.proceed(
                                    if (
                                        token.isNotBlank() &&
                                            solicitud.header("Authorization") == null
                                    )
                                        solicitud
                                            .newBuilder()
                                            .header("Authorization", "Bearer $token")
                                            .build()
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

    val accesoApi
        get() = conexion().create(AccesoApi::class.java)

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
