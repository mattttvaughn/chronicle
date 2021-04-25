package io.github.mattpvaughn.chronicle.features.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import androidx.work.NetworkType
import com.tonyodev.fetch2.*
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.application.MainActivity.Companion.FLAG_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_BOOK_ID
import io.github.mattpvaughn.chronicle.application.MainActivity.Companion.REQUEST_CODE_PREFIX_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

/**
 * A [Worker] responsible for displaying download notifications for all actively
 * downloading books
 *
 * TODO: write extension functions to turn fetch calls into suspend functions
 */
class DownloadNotificationWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    private val fetch: Fetch = Injector.get().fetch()
    private val notificationManager = NotificationManagerCompat.from(applicationContext)
    private val bookRepository = Injector.get().bookRepo()

    private val cancelAllDesc =
        applicationContext.getString(R.string.download_notification_cancel_all)
    private val cancelAllIntent = Intent(ACTION_CANCEL_ALL_DOWNLOADS)
    private val cancelAllPendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        ACTION_CANCEL_ALL_DOWNLOADS_ID,
        cancelAllIntent,
        0
    )
    private val actionCancelAll = NotificationCompat.Action.Builder(
        R.drawable.fetch_notification_cancel,
        cancelAllDesc,
        cancelAllPendingIntent
    ).build()

    private val activeDownloadStatuses =
        listOf(Status.QUEUED, Status.DOWNLOADING, Status.NONE, Status.ADDED)

    private val refreshNotifFrequencyMS = 500L
    private val maxWaitToStartDurationMs = 10_000L

    /** Adds a download for all tracks in book with [Audiobook.id] == [bookId] */
    override suspend fun doWork() = withContext(Dispatchers.IO) {
        createNotificationChannelAsNeeded()

        var hasActiveDownloads = false
        val startedTimeStamp = System.currentTimeMillis()
        // Wait for at least [maxWaitToStartDurationMs] to ensure downloads have started
        while (hasActiveDownloads || System.currentTimeMillis() - startedTimeStamp < maxWaitToStartDurationMs) {
            fetch.getDownloads { allDownloads ->
                val activeBooks = allDownloads.groupBy { it.group }
                    .filter { (_, trackDownloads) ->
                        trackDownloads.any { it.status in activeDownloadStatuses }
                    }
                hasActiveDownloads = activeBooks.isNotEmpty()
                updateNotifications(activeBooks)
            }
            delay(refreshNotifFrequencyMS)
        }

        notificationManager.cancelAll()

        val workerContext = coroutineContext

        fetch.getDownloads { downloads ->
            CoroutineScope(workerContext).launch {
                withContext(Dispatchers.IO) {
                    // Mark successful downloads as cached
                    val successfulGroupIds = downloads.groupBy { it.group }
                        .filter { group ->
                            group.value.all { it.status == Status.COMPLETED }
                        }
                        .map { it.key }
                    successfulGroupIds.forEach { groupId ->
                        Timber.i("Book download success for ($groupId)")
                        bookRepository.updateCachedStatus(groupId, true)
                    }

                    // Show notifications for finished/failed downloads, then remove them from Fetch
                    showDownloadsCompleteNotification(downloads)
                }
            }
        }


        return@withContext Result.success()
    }

    /**
     * Show notifications for completed/failed downloads, allowing the user to retry failed
     * downloads if they wish to
     */
    private fun showDownloadsCompleteNotification(downloads: List<Download>) {
        val bookDownloads = downloads.groupBy { it.group }
        Timber.i("Downloads: $bookDownloads")
        val bookStatuses = bookDownloads.map { bookDownload ->
            // Don't show a notification for a cancelled download, users don't need to be
            // informed that they cancelled a download
            val statuses = bookDownload.value.filter {
                it.status != Status.CANCELLED
            }.map { it.status }
            val bookName = bookDownload.value.firstOrNull()?.tag ?: ""
            val bookId = bookDownload.key
            DownloadResult(
                bookName = bookName,
                bookId = bookId,
                status = when {
                    Status.FAILED in statuses -> Status.FAILED
                    Status.COMPLETED in statuses -> Status.COMPLETED
                    else -> Status.NONE
                },
                errors = bookDownload.value.filter { it.error != Error.NONE }.map {
                    it.error.name
                }.distinct()
            )
        }.filter {
            it.bookName.isNotEmpty()
                    && it.bookId != NO_AUDIOBOOK_FOUND_ID
                    && (it.status in listOf(Status.FAILED, Status.COMPLETED))
        }

        if (bookStatuses.isNotEmpty()) {
            val showInGroup = bookStatuses.size > 1
            bookStatuses.forEach { result ->
                val notification = makeFinishedNotification(result, showInGroup)
                if (notification != null) {
                    notificationManager.notify(result.bookName.hashCode(), notification)
                }
            }
            if (showInGroup) {
                val summary = makeFinishedSummary(bookStatuses)
                if (summary != null) {
                    notificationManager.notify(DOWNLOADS_FINISHED_NOTIF_SUMMARY_ID, summary)
                }
            }
        }

        // Remove all downloads from download manager after completion
        for ((bookId, _) in bookDownloads) {
            fetch.removeGroup(bookId)
        }
    }

    internal data class DownloadResult(
        val bookName: String,
        val bookId: Int,
        val status: Status,
        val errors: List<String>
    )


    /** Creates a notification channel if required by the given version of Android SDK */
    private fun createNotificationChannelAsNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                DOWNLOAD_CHANNEL,
                applicationContext.getString(R.string.download_notification_title),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationChannel.description =
                applicationContext.getString(R.string.download_channel_description)

            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    /** Make a group summary for all completed downloads */
    private fun makeFinishedSummary(bookStatuses: List<DownloadResult>): Notification? {
        val failCount = bookStatuses.count { it.status == Status.FAILED }
        val successCount = bookStatuses.count { it.status == Status.COMPLETED }
        if (failCount + successCount == 0) {
            // Don't make a notification for zero book statuses
            return null
        }

        // For one download, show name + status
        val res = applicationContext.resources
        val downloadFailed = bookStatuses.any { it.status == Status.FAILED }
        val finishedTitle = if (downloadFailed) {
            res.getString(R.string.download_failed_notification_title)
        } else {
            res.getString(R.string.download_successful_notification_title)
        }

        val finishedContent = when {
            bookStatuses.all { it.status == Status.FAILED } -> res.getQuantityString(
                R.plurals.downloads_failed_summary,
                failCount
            )
            bookStatuses.all { it.status == Status.COMPLETED } -> res.getQuantityString(
                R.plurals.downloads_complete_summary,
                successCount
            )
            else -> {
                applicationContext.getString(
                    R.string.downloads_completed_summary_mixed,
                    bookStatuses.count { it.status == Status.COMPLETED },
                    bookStatuses.count { it.status == Status.FAILED }
                )
            }
        }

        val downloadSummaries = bookStatuses.map { (bookTitle, _, status) ->
            when (status) {
                Status.COMPLETED -> applicationContext.getString(
                    R.string.download_successful_notification_content,
                    bookTitle.take(30)
                )
                Status.FAILED -> applicationContext.getString(
                    R.string.download_failed_notification_content,
                    bookTitle.take(30)
                )
                else -> return null
            }
        }

        val finishedDownloadList = NotificationCompat.InboxStyle()
            .setBigContentTitle(finishedTitle)
        downloadSummaries.forEach { line -> finishedDownloadList.addLine(line) }

        val resultIcon = if (downloadFailed) {
            R.drawable.ic_cloud_download_failed
        } else {
            R.drawable.ic_cloud_done_white
        }

        return NotificationCompat.Builder(applicationContext, DOWNLOAD_CHANNEL)
            .setContentTitle(finishedTitle)
            //set content text to support devices running API level < 24
            .setContentText(finishedContent)
            .setSmallIcon(resultIcon)
            .setStyle(finishedDownloadList)
            .setOnlyAlertOnce(true)
            .setGroup(DOWNLOADS_FINISHED_NOTIF_GROUP)
            .setGroupSummary(true)
            .build()
    }

    /**
     * Makes a notification indicating that a book with [Audiobook] == [bookId] has finished
     * downloading
     */
    private fun makeFinishedNotification(
        downloadResult: DownloadResult,
        showInGroup: Boolean
    ): Notification? {
        val status = downloadResult.status
        val bookName = downloadResult.bookName

        val title = applicationContext.getString(
            when (status) {
                Status.FAILED -> R.string.download_failed_notification_content
                Status.COMPLETED -> R.string.download_successful_notification_content
                else -> return null
            }, bookName
        )

        val content = if (downloadResult.errors.isEmpty()) {
            null
        } else {
            downloadResult.errors.joinToString { it }
        }
        val icon = when (status) {
            Status.FAILED -> R.drawable.ic_cloud_download_failed
            Status.COMPLETED -> R.drawable.ic_cloud_done_white
            else -> return null
        }

        val openBookPendingIntent = makeOpenBookPendingIntent(downloadResult.bookId)
        val builder = NotificationCompat.Builder(applicationContext, DOWNLOAD_CHANNEL)
            .setContentTitle(title)
            .setContentIntent(openBookPendingIntent)
            .setContentText(content)
            .setSmallIcon(icon)
            .setGroup(if (showInGroup) DOWNLOADS_FINISHED_NOTIF_GROUP else null)

        return builder.build()
    }

    private fun updateNotifications(bookDownloadGroups: Map<Int, List<Download>>) {
        if (bookDownloadGroups.isEmpty()) {
            return
        }

        val bookNotifications = bookDownloadGroups.mapNotNull { (bookId, trackDownloads) ->
            val bookTitle = trackDownloads.firstOrNull()?.tag ?: return@mapNotNull null
            val avgCompletion = trackDownloads.sumBy {
                min(100, max(0, it.progress))
            } / (trackDownloads.size)

            bookId to createDownloadNotificationForBook(
                bookId = bookId,
                bookTitle = bookTitle,
                avgCompletion = avgCompletion,
                showInGroup = bookDownloadGroups.size > 1
            )
        }
        val summaryNotification = makeActiveDownloadsSummary(bookDownloadGroups)
        showDownloadNotifications(bookNotifications, summaryNotification)
    }

    private fun showDownloadNotifications(
        downloadNotifications: List<Pair<Int, Notification>>,
        summaryNotification: Notification
    ) {
        val size = downloadNotifications.size
        when {
            size == 0 -> notificationManager.cancelAll()
            size == 1 -> showNotificationForeground(
                downloadNotifications[0].second,
                DOWNLOAD_NOTIF_SUMMARY_ID
            )
            size >= 2 -> {
                showNotificationForeground(summaryNotification, DOWNLOAD_NOTIF_SUMMARY_ID)
                downloadNotifications.forEach { (bookId, notification) ->
                    notificationManager.notify(bookId, notification)
                }
            }
        }
    }

    /** Adds additional metadata about foreground service type if available */
    private fun showNotificationForeground(notification: Notification, notificationId: Int) {
        setForegroundAsync(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(notificationId, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(notificationId, notification)
            }
        )
    }

    private fun makeActiveDownloadsSummary(bookGroups: Map<Int, List<Download>>): Notification {
        // Show up to 5 downloads on legacy devices
        val downloadsToShow = bookGroups.toList().sortedBy { (_, b) ->
            b.firstOrNull()?.created ?: System.currentTimeMillis()
        }.take(5).mapNotNull { (_, downloads) ->
            val bookName = downloads.getOrNull(0)?.tag
            val progress = min(max(downloads.sumBy { it.progress } / (downloads.size), 0), 100)
            if (downloads.isNotEmpty() && bookName != null) {
                applicationContext.getString(
                    R.string.download_starting,
                    bookName.take(30),
                    progress.toString()
                )
            } else {
                null
            }
        }
        val downloadSummary = applicationContext.resources.getQuantityString(
            R.plurals.download_books_summary,
            bookGroups.size,
            bookGroups.size
        )
        // build summary info into InboxStyle template
        val downloadsList = NotificationCompat.InboxStyle()
            .setBigContentTitle(downloadSummary)
        downloadsToShow.forEach { line -> downloadsList.addLine(line) }

        return NotificationCompat.Builder(applicationContext, DOWNLOAD_CHANNEL)
            .setContentTitle(downloadSummary)
            //set content text to support devices running API level < 24
            .setContentText(downloadsToShow.firstOrNull() ?: "")
            .setSmallIcon(R.drawable.ic_cloud_download_white)
            .setStyle(downloadsList)
            .setGroup(DOWNLOAD_NOTIF_GROUP)
            .addAction(actionCancelAll)
            .setGroupSummary(true)
            .build()
    }

    /** Creates a [Notification] for a book download */
    private fun createDownloadNotificationForBook(
        bookId: Int,
        bookTitle: String,
        avgCompletion: Int,
        showInGroup: Boolean
    ): Notification {

        val notificationTitle = applicationContext.getString(
            R.string.download_starting,
            bookTitle,
            avgCompletion.toString()
        )

        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            ACTION_CANCEL_BOOK_DOWNLOAD_ID + bookId,
            Intent(ACTION_CANCEL_BOOK_DOWNLOAD).apply {
                putExtra(KEY_BOOK_ID, bookId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openBookPendingIntent = makeOpenBookPendingIntent(bookId)

        val cancel = applicationContext.getString(R.string.download_notification_cancel)
        return NotificationCompat.Builder(applicationContext, DOWNLOAD_CHANNEL)
            .setContentTitle(notificationTitle)
            .setContentIntent(openBookPendingIntent)
            .setProgress(100, avgCompletion, false)
            .setSmallIcon(R.drawable.ic_cloud_download_white)
            .setGroup(if (showInGroup) DOWNLOAD_NOTIF_GROUP else null)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, cancelPendingIntent)
            .build()
    }

    private fun makeOpenBookPendingIntent(bookId: Int): PendingIntent? {
        val intent = Intent()
        val activity = applicationContext.packageManager.getPackageInfo(
            applicationContext.packageName,
            PackageManager.GET_ACTIVITIES
        ).activities.find { it.name.contains("MainActivity") }
        intent.setPackage(applicationContext.packageName)
        intent.putExtra(FLAG_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_BOOK_ID, bookId)
        intent.component = ComponentName(applicationContext.packageName, activity?.name ?: "")
        return PendingIntent.getActivity(
            applicationContext,
            REQUEST_CODE_PREFIX_OPEN_ACTIVITY_TO_AUDIOBOOK_WITH_ID + bookId,
            intent,
            0
        )
    }

    companion object {
        const val DOWNLOAD_WORKER_ID: String =
            "io.github.mattpvaughn.chronicle.features.download\$DOWNLOAD_WORKER_ID"
        const val DOWNLOAD_CHANNEL: String =
            "io.github.mattpvaughn.chronicle.features.download\$DOWNLOAD_CHANNEL"
        const val KEY_BOOK_ID = "KEY_BOOK_ID"

        const val DOWNLOAD_NOTIF_GROUP =
            "io.github.mattpvaughn.chronicle.features.download\$DOWNLOAD_NOTIF_GROUP"
        const val DOWNLOAD_NOTIF_SUMMARY_ID = 999

        const val DOWNLOADS_FINISHED_NOTIF_GROUP =
            "io.github.mattpvaughn.chronicle.features.download\$DOWNLOADS_FINISHED_NOTIF_GROUP"
        const val DOWNLOADS_FINISHED_NOTIF_SUMMARY_ID = 1024

        const val ACTION_CANCEL_BOOK_DOWNLOAD =
            "io.github.mattpvaughn.chronicle.features.download\$ACTION_CANCEL_BOOK_DOWNLOAD"
        const val ACTION_CANCEL_BOOK_DOWNLOAD_ID = 79211

        const val ACTION_CANCEL_ALL_DOWNLOADS =
            "io.github.mattpvaughn.chronicle.features.download\$ACTION_CANCEL_ALL"
        const val ACTION_CANCEL_ALL_DOWNLOADS_ID = 9212

        /** Start [DownloadNotificationWorker] if it is not already running */
        fun start() {
            val syncWorkerConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val worker = OneTimeWorkRequestBuilder<DownloadNotificationWorker>()
                .setConstraints(syncWorkerConstraints)
                .build()

            Injector.get().workManager().beginUniqueWork(
                DOWNLOAD_WORKER_ID,
                ExistingWorkPolicy.KEEP,
                worker
            ).enqueue()
        }

    }

}
