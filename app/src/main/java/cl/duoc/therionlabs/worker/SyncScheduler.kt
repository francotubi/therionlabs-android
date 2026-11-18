package cl.duoc.therionlabs.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object SyncScheduler {

    private const val TRABAJO_SYNC = "therionlabs_sync_prospectos"

    fun solicitarSincronizacion(context: Context) {

        // Ejecutar solamente cuando exista internet
        val restricciones = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Crear el trabajo de sincronización
        val solicitud =
            OneTimeWorkRequestBuilder<ProspectoSyncWorker>()
                .setConstraints(restricciones)
                .build()

        // Evitar acumular trabajos duplicados
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                TRABAJO_SYNC,
                ExistingWorkPolicy.KEEP,
                solicitud
            )
    }
}
