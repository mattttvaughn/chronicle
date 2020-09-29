package io.github.mattpvaughn.chronicle.features.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.tonyodev.fetch2.*
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import kotlin.time.minutes

class DownloadWorker(
    private val context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    private val REGEX_H1_CONTENTS = Regex("<h1>(.*)</h1>")

    private val trackRepository: ITrackRepository = Injector.get().trackRepo()
    private val fetch: Fetch = Injector.get().fetch()
    private val prefsRepo: PrefsRepo = Injector.get().prefsRepo()
    private val plexConfig: PlexConfig = Injector.get().plexConfig()

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as
            NotificationManager

    /** Adds a download for all tracks in book with [Audiobook.id] == [bookId] */
    override suspend fun doWork(): Result {
        val bookId = inputData.getInt(KEY_BOOK_ID, NO_AUDIOBOOK_FOUND_ID)
            .takeIf { it != NO_AUDIOBOOK_FOUND_ID } ?: return Result.failure()
        val bookTitle = inputData.getString(KEY_BOOK_TITLE) ?: return Result.failure()
        val startingNotificationTitle =
            applicationContext.getString(R.string.download_starting, bookTitle, 0, 0)

        return withContext(Dispatchers.IO) {
            setForeground(createForegroundInfo(startingNotificationTitle, 0f))
            val requests = makeRequests(bookId)
            var completedCount = 0
            // map download ids to download completion (Int in 0..100)
            var errorMessage = ""
            val progressMap = requests.map { Pair(it.id, 0) }.toMap().toMutableMap()
            updateProgress(bookTitle, 0, progressMap)
            val downloadListener = object : FetchFinishedListener() {
                override fun onFinished(download: Download) {
                    completedCount++
                }

                // Make a special note of errors so we know what went wrong
                override fun onError(download: Download, error: Error, throwable: Throwable?) {
                    errorMessage = REGEX_H1_CONTENTS.find(
                        error.httpResponse?.errorResponse ?: ""
                    )?.groupValues?.getOrNull(0)?.replace("</h1>", "") ?: ""
                    super.onError(download, error, throwable)
                }

                override fun onProgress(
                    download: Download,
                    etaInMilliSeconds: Long,
                    downloadedBytesPerSecond: Long
                ) {
                    Timber.i("Download progress updated! ${download.progress}")
                    progressMap[download.id] = download.progress
                    GlobalScope.launch {
                        updateProgress(bookTitle, completedCount, progressMap)
                    }
                    super.onProgress(download, etaInMilliSeconds, downloadedBytesPerSecond)
                }
            }
            fetch.addListener(downloadListener)
            fetch.enqueue(requests)
            val checkResultsFrequencyMillis = 500L
            val maxDownloadDuration = 8.minutes.toLongMilliseconds()
            withTimeoutOrNull(maxDownloadDuration) {
                while (completedCount < requests.size) {
                    Timber.i("Download requests completed: $completedCount")
                    delay(checkResultsFrequencyMillis)
                }
                null
            }

            val successfulDownloads = requests.filter { File(it.file).exists() }
            val failedDownloads = requests.minus(successfulDownloads)
            successfulDownloads.forEach {
                handleSuccessfulDownload(it.id, it.groupId, File(it.file))
            }
            Timber.i("Files downloaded: ${successfulDownloads.size} successes, ${failedDownloads.size} failures")
            fetch.removeListener(downloadListener)

            if (failedDownloads.isNotEmpty()) {
                Timber.i("Download failed: $errorMessage")
            }

            val downloadSuccessful = successfulDownloads.size == requests.size
            val outputData = Data.Builder()
                .putBoolean(ARG_DOWNLOAD_SUCCEEDED_BOOLEAN, downloadSuccessful)
                .putString(ARG_ERROR_MESSAGE_STRING, errorMessage)
                .build()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, errorMessage, LENGTH_SHORT).show()
            }

            return@withContext if (downloadSuccessful) {
                Result.success()
            } else {
                Result.failure(outputData)
            }
        }
    }

    private suspend fun handleSuccessfulDownload(trackId: Int, bookId: Int, it: File) {
        withContext(Dispatchers.IO) {
            Timber.i("Track downloaded ($trackId): cache status updated")
            trackRepository.updateCachedStatus(trackId, true)
        }

        // check if book has finished
        fetch.getDownloadsInGroup(bookId) { downloads ->
            Timber.i("Files downloaded: $downloads")
            // Don't update cache status until all tracks in book downloaded
            if (downloads.isNotEmpty() && !downloads.all { it.status == Status.COMPLETED }) {
                return@getDownloadsInGroup
            }
        }
    }

    private suspend fun updateProgress(title: String, completed: Int, progressMap: Map<Int, Int>) {
        val notificationTitle = applicationContext.getString(
            R.string.download_starting,
            title,
            completed,
            progressMap.size
        )

        val percentCompleted = progressMap.map { it.value / 100.0f }.sum() / progressMap.size
        setForeground(createForegroundInfo(notificationTitle, percentCompleted))
    }

    /**
     * Creates [Request]s for all missing files associated with [bookId]
     *
     * @return the number of files to be downloaded
     */
    private suspend fun makeRequests(bookId: Int): List<Request> {
        // Gets all tracks for album id
        val tracks = trackRepository.getTracksForAudiobookAsync(bookId)

        val cachedFilesDir = prefsRepo.cachedMediaDir
        Timber.i("Caching tracks to: ${cachedFilesDir.path}")
        Timber.i("Tracks to cache: $tracks")
        val requests = tracks.mapNotNull { track ->
            // File exists but is not marked as cached in the database- more likely than not
            // this means that it has failed to fully download
            val destFile = File(cachedFilesDir, track.getCachedFileName())
            val trackCached = track.cached
            val destFileExists = destFile.exists()

            // No need to make a new request, the file is already downloaded
            if (trackCached && destFileExists) {
                return@mapNotNull null
            }

            // File exists but is not marked as cached in the database- probably means a download
            // has failed. Delete it and try again
            if (!trackCached && destFileExists) {
                val deleted = destFile.delete()
                if (!deleted) {
                    Timber.e("Failed to delete previously cached file. Download will fail!")
                } else {
                    Timber.e("Succeeding in deleting cached file")
                }
            }

            return@mapNotNull makeTrackDownloadRequest(
                track,
                bookId,
                "file://${destFile.absolutePath}"
            )
        }

        return requests
    }

    // Creates an instance of ForegroundInfo which can be used to update the
    // ongoing notification.
    private fun createForegroundInfo(
        downloadTitle: String,
        percentCompleted: Float
    ): ForegroundInfo {
        val id = applicationContext.getString(R.string.download_notification_channel_id)
        val cancel = applicationContext.getString(R.string.download_notification_cancel)
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(getId())

        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(id)
        }

        val notification = NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(downloadTitle)
            .setTicker(downloadTitle)
            .setProgress(100, (percentCompleted * 100).toInt(), false)
            .setContentText(downloadTitle)
            .setSmallIcon(R.drawable.ic_cloud_download_white)
            .setOngoing(true)
            // Add the cancel action to the notification which can
            // be used to cancel the worker
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }


    /** Create a [Request] for a track download with the proper metadata */
    private fun makeTrackDownloadRequest(
        track: MediaItemTrack,
        bookId: Int,
        dest: String
    ): Request {
        return plexConfig.makeDownloadRequest(track.media, bookId, dest)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(channelId: String) {
        val notificationChannel = NotificationChannel(
            channelId,
            applicationContext.getString(R.string.download_notification_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = applicationContext.getString(R.string.download_channel_description)
        }

        notificationManager.createNotificationChannel(notificationChannel)
    }

    companion object {
        const val ARG_DOWNLOAD_SUCCEEDED_BOOLEAN = "ARG_DOWNLOAD_SUCCEEDED"
        const val ARG_ERROR_MESSAGE_STRING = "ARG_DOWNLOAD_SUCCEEDED"

        const val DOWNLOAD_CHANNEL: String =
            "io.github.mattpvaughn.chronicle.features.download\$DOWNLOAD_CHANNEL"
        const val NOTIFICATION_ID = 9
        const val KEY_BOOK_ID = "KEY_BOOK_ID"
        const val KEY_BOOK_TITLE = "KEY_BOOK_TITLE"

        fun makeWorkerData(bookId: Int, bookTitle: String): Data {
            require(bookId != NO_AUDIOBOOK_FOUND_ID)
            return workDataOf(KEY_BOOK_ID to bookId, KEY_BOOK_TITLE to bookTitle)
        }
    }
}
