package cl.duoc.therionlabs.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

object DireccionUbicacion {

    suspend fun buscar(contexto: Context, latitud: Double, longitud: Double): String? {
        if (!Geocoder.isPresent()) return null

        return withTimeoutOrNull(8000L) {
            val geocoder = Geocoder(contexto, Locale.getDefault())
            val direcciones = consultar(geocoder, latitud, longitud)
            direcciones?.firstOrNull()?.let(::formatear)
        }
    }

    private suspend fun consultar(
        geocoder: Geocoder,
        latitud: Double,
        longitud: Double,
    ): List<Address>? = suspendCancellableCoroutine { continuacion ->
        val terminado = AtomicBoolean(false)

        fun completar(resultado: List<Address>?) {
            if (terminado.compareAndSet(false, true) && continuacion.isActive) {
                continuacion.resume(resultado)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                geocoder.getFromLocation(
                    latitud,
                    longitud,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            completar(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            completar(null)
                        }
                    },
                )
            } catch (_: Exception) {
                completar(null)
            }
        } else {

            val trabajo = Thread {
                try {
                    @Suppress("DEPRECATION")
                    val resultado = geocoder.getFromLocation(latitud, longitud, 1)
                    completar(resultado)
                } catch (_: Exception) {
                    completar(null)
                }
            }
                .apply {
                    name = "DireccionUbicacion"
                    isDaemon = true
                }

            continuacion.invokeOnCancellation {
                terminado.set(true)
                trabajo.interrupt()
            }
            trabajo.start()
        }
    }

    private fun formatear(direccion: Address): String? {
        val linea = if (direccion.maxAddressLineIndex >= 0) direccion.getAddressLine(0) else null
        if (!linea.isNullOrBlank()) return linea.trim()

        return listOfNotNull(
                direccion.thoroughfare,
                direccion.subThoroughfare,
                direccion.subLocality,
                direccion.locality,
                direccion.adminArea,
                direccion.countryName,
            )
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
    }
}
