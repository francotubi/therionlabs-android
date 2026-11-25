package cl.duoc.therionlabs.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private fun restricciones() =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun programar(contexto: Context) {
        WorkManager.getInstance(contexto)
            .enqueueUniquePeriodicWork(
                "sincronizacion_periodica",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ProspectoSyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(restricciones())
                    .build(),
            )
    }

    fun solicitarSincronizacion(contexto: Context) {
        WorkManager.getInstance(contexto)
            .enqueueUniqueWork(
                "sincronizacion_capturas",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<ProspectoSyncWorker>()
                    .setConstraints(restricciones())
                    .build(),
            )
    }

    fun forzarSincronizacion(contexto: Context) {
        WorkManager.getInstance(contexto)
            .enqueueUniqueWork(
                "sincronizacion_capturas",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ProspectoSyncWorker>()
                    .setConstraints(restricciones())
                    .build(),
            )
    }
}
