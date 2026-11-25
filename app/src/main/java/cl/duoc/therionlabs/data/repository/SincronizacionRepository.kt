package cl.duoc.therionlabs.data.repository

import android.content.Context
import cl.duoc.therionlabs.data.local.AppDatabase
import cl.duoc.therionlabs.network.*
import java.io.IOException
import kotlinx.coroutines.CancellationException

class SincronizacionRepository(private val contexto: Context) {
    private val db = AppDatabase.getDatabase(contexto)
    private val tokens = DispositivoTokenStore(contexto)

    suspend fun enviarProspectos(): Boolean {
        var reintentar = false
        for (prospecto in db.prospectoDao().obtenerPendientesSincronizacion()) {
            val token =
                tokens.obtenerTokenPorCodigo(prospecto.captadorId)
                    ?: tokens.obtenerToken(prospecto.captadorId)
                    ?: continue
            try {
                ParticipacionEventoClient(contexto)
                    .unirseEvento(prospecto.captadorId, prospecto.eventoId)
                val respuesta =
                    RetrofitClient.prospectoApi.sincronizarProspecto(
                        token,
                        prospecto.toSyncRequest(),
                    )
                when {
                    respuesta.isSuccessful -> {
                        val confirmacion = respuesta.body()
                        if (
                            confirmacion?.estado == "SINCRONIZADO" &&
                                confirmacion.prospectoId == prospecto.identificador
                        )
                            db.prospectoDao()
                                .marcarComoSincronizado(prospecto.id, prospecto.identificador)
                        else reintentar = true
                    }
                    respuesta.code() == 429 || respuesta.code() >= 500 -> reintentar = true
                    respuesta.code() in listOf(400, 404, 409, 422) ->
                        db.prospectoDao()
                            .rechazar(
                                prospecto.id,
                                RespuestasServidor.mensaje(
                                    respuesta.errorBody()?.string(),
                                    respuesta.code(),
                                ),
                            )
                }
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (_: IOException) {
                reintentar = true
            } catch (error: IllegalArgumentException) {
                if (error.message?.startsWith("El evento") == true)
                    db.prospectoDao().rechazar(prospecto.id, error.message.orEmpty())
            }
        }
        return !reintentar
    }

    suspend fun enviarGestiones(): Boolean {
        if (ConfiguracionConexion.claveAdministrativa.isBlank()) return true
        var completo = true
        for (gestion in db.operacionDao().gestionesPendientes()) {
            val prospecto = db.prospectoDao().obtenerPorId(gestion.prospectoId) ?: continue
            if (prospecto.sincronizado != 1 || prospecto.eventoId.isBlank()) continue
            try {
                val respuesta =
                    RetrofitClient.administracionApi.guardarGestion(
                        prospecto.identificador,
                        GestionSolicitud(
                            gestion.identificador,
                            gestion.administrativoNombre,
                            gestion.estado,
                            gestion.canal,
                            gestion.observacion,
                        ),
                    )
                if (respuesta.isSuccessful && respuesta.body()?.id == gestion.identificador)
                    db.operacionDao().confirmarGestion(gestion.identificador)
                else completo = false
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (_: IOException) {
                completo = false
            }
        }
        return completo
    }
}
