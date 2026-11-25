package cl.duoc.therionlabs.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface ClimaApi {
    @GET("v1/forecast")
    suspend fun consultar(
        @Query("latitude") latitud: Double,
        @Query("longitude") longitud: Double,
        @Query("current") variables: String = "temperature_2m,weather_code",
        @Query("timezone") zona: String = "America/Santiago",
    ): RespuestaClima
}

data class RespuestaClima(val current: ClimaActual?)

data class ClimaActual(val temperature_2m: Double, val weather_code: Int, val time: String)

object ServicioClima {
    val api: ClimaApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ClimaApi::class.java)
    }

    fun descripcion(codigo: Int): String =
        when (codigo) {
            0 -> "Despejado"
            1,
            2,
            3 -> "Nublado"
            45,
            48 -> "Niebla"
            in 51..67,
            in 80..82 -> "Lluvia"
            in 71..77,
            85,
            86 -> "Nieve"
            95,
            96,
            99 -> "Tormenta"
            else -> "Condición variable"
        }
}
