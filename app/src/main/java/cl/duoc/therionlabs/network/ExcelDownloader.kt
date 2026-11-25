package cl.duoc.therionlabs.network

import android.content.Context
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExcelDownloader {
    suspend fun descargarYGuardar(
        contexto: Context,
        eventoId: String,
        destino: Uri,
    ) =
        withContext(Dispatchers.IO) {
            val respuesta = RetrofitClient.excelApi.descargarExcel(eventoId = eventoId)
            if (!respuesta.isSuccessful) {
                val mensaje =
                    when (respuesta.code()) {
                        401,
                        403 -> "Vuelve a iniciar sesión con una cuenta administrativa."
                        404 -> "No se encontró el evento."
                        else -> "Error al descargar Excel: HTTP ${respuesta.code()}"
                    }
                throw IOException(mensaje)
            }
            val cuerpo =
                respuesta.body() ?: throw IOException("El servidor devolvió un archivo vacío.")
            cuerpo.use { archivo ->
                val salida =
                    contexto.contentResolver.openOutputStream(destino)
                        ?: throw IOException("No se pudo abrir el archivo de destino.")
                salida.use { destinoArchivo ->
                    archivo.byteStream().copyTo(destinoArchivo)
                    destinoArchivo.flush()
                }
            }
        }
}
