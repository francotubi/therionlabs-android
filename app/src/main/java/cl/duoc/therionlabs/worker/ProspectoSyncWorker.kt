package cl.duoc.therionlabs.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cl.duoc.therionlabs.data.repository.SincronizacionRepository
import cl.duoc.therionlabs.network.ConfiguracionConexion
import java.io.IOException
import kotlinx.coroutines.CancellationException

class ProspectoSyncWorker(contexto: Context, parametros: WorkerParameters) :
    CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result =
        try {
            ConfiguracionConexion.inicializar(applicationContext)
            val repositorio = SincronizacionRepository(applicationContext)
            val prospectos = repositorio.enviarProspectos()
            val gestiones = repositorio.enviarGestiones()
            if (prospectos && gestiones) Result.success() else Result.retry()
        } catch (cancelacion: CancellationException) {
            throw cancelacion
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
}
