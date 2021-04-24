package io.github.mattpvaughn.chronicle.features.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.math.roundToInt

class MoveSyncLocationWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    private val notificationManager = NotificationManagerCompat.from(applicationContext)
    private val prefsRepo = Injector.get().prefsRepo()

    /** Moves all previously downloaded files to [PrefsRepo.cachedMediaDir] */
    override suspend fun doWork() = withContext(Dispatchers.IO) {
        // Create a notification channel if possible
        createChannel()

        val activeDownloadDir = prefsRepo.cachedMediaDir
        val inactiveSyncLocations = Injector.get().externalDeviceDirs().filter {
            it.path != activeDownloadDir.path
        }

        val fileMoveFailures = inactiveSyncLocations.flatMap { inactiveDir ->
            moveFilesBetweenDirectories(inactiveDir, activeDownloadDir)
        }.filter { it.isFailure }

        notificationManager.cancelAll()

        if (fileMoveFailures.isNotEmpty()) {
            showFailureNotification(fileMoveFailures)
        }

        return@withContext Result.success()
    }

    private fun showFailureNotification(fileMoveFailures: List<kotlin.Result<Unit>>) {
        val notificationTitle = applicationContext.getString(
            R.string.moving_files_failed_title
        )
        val errorReasons = fileMoveFailures.mapNotNull {
            it.exceptionOrNull()?.localizedMessage
        }.joinToString(separator = ", ")

        val notif = NotificationCompat.Builder(applicationContext, TRANSFER_CHANNEL)
            .setContentTitle(notificationTitle)
            .setContentText(errorReasons)
            .setSmallIcon(R.drawable.ic_sync)
            .build()

        notificationManager.notify(TRANSFER_ERROR_NOTIF_ID, notif)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                TRANSFER_CHANNEL,
                applicationContext.getString(R.string.moving_files_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationChannel.description =
                applicationContext.getString(R.string.download_channel_description)
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    private fun moveFilesBetweenDirectories(from: File, to: File): List<kotlin.Result<Unit>> {
        val toMove = from.listFiles { cachedFile ->
            MediaItemTrack.cachedFilePattern.matches(cachedFile.name)
        } ?: emptyArray<File>()
        return toMove.mapIndexed { i, cachedFile ->
            showFileTransferNotification(i, toMove.size)
            val destFile = File(to, cachedFile.name)
            Timber.i("Moving file ${cachedFile.absolutePath} to ${destFile.absolutePath}")
            try {
                moveFile(cachedFile, destFile)
                kotlin.Result.success(Unit)
            } catch (io: IOException) {
                Timber.i("Failed to move file: $io")
                kotlin.Result.failure<Unit>(io)
            }
        }
    }

    private fun moveFile(cachedFile: File, destFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.move(cachedFile.toPath(), destFile.toPath())
        } else {
            val copied = cachedFile.copyTo(
                destFile,
                overwrite = true
            )
            if (copied.exists()) {
                cachedFile.delete()
            }
            Timber.i("Moved file ${cachedFile.name}? ${copied.exists()}")
        }
    }

    /** * Creates a [Notification] for a book download */
    private fun showFileTransferNotification(filesTransferred: Int, totalFiles: Int) {
        val notificationTitle = applicationContext.getString(
            R.string.moving_files_title,
            filesTransferred,
            totalFiles
        )

        val notif = NotificationCompat.Builder(applicationContext, TRANSFER_CHANNEL)
            .setContentTitle(notificationTitle)
            .setTicker(notificationTitle)
            .setProgress(100, (filesTransferred * 100f / totalFiles).roundToInt(), false)
            .setContentText(notificationTitle)
            .setSmallIcon(R.drawable.ic_sync)
            .setOngoing(true)
            .build()

        show(notif)
    }

    // Add additional metadata about foreground service type if available
    private fun show(notif: Notification) {
        setForegroundAsync(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(TRANSFER_NOTIF_ID, notif, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(TRANSFER_NOTIF_ID, notif)
            }
        )
    }

    companion object {
        const val WORKER_ID: String =
            "io.github.mattpvaughn.chronicle.features.download\$WORKER_ID"
        const val TRANSFER_CHANNEL: String =
            "io.github.mattpvaughn.chronicle.features.download\$TRANSFER_CHANNEL"
        const val TRANSFER_NOTIF_ID = 1012
        const val TRANSFER_ERROR_NOTIF_ID = 1013
    }
}
