package io.github.mattpvaughn.chronicle.data.local

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.sources.SyncMediaWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

class DataManager {

    companion object {
        fun refreshData(forceSync: Boolean = false) {
            Timber.i("Refreshing data!")
            val workManager = Injector.get().workManager()
            val worker = OneTimeWorkRequestBuilder<SyncMediaWorker>()
                .setInputData(SyncMediaWorker.makeData(forceSync))
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    OneTimeWorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            workManager
                .beginUniqueWork(SYNC_MEDIA_ID, ExistingWorkPolicy.KEEP, worker)
                .enqueue()
        }

        const val SYNC_MEDIA_ID = "DataManager.SYNC_MEDIA_ID"
    }
}