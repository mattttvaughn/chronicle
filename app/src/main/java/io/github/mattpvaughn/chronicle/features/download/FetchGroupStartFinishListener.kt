package io.github.mattpvaughn.chronicle.features.download

import com.tonyodev.fetch2.*

abstract class FetchGroupStartFinishListener : AbstractFetchGroupListener() {

    /** A group of downloads with group id [groupId] has been added to Fetch */
    abstract fun onStarted(groupId: Int, fetchGroup: FetchGroup)

    /**
     * All downloads with group id [groupId] have finished (by error, completion, or other means)
     */
    abstract fun onFinished(groupId: Int, fetchGroup: FetchGroup)

    private val finishedStatuses = listOf(
        Status.CANCELLED,
        Status.COMPLETED,
        Status.FAILED,
        Status.DELETED,
        Status.REMOVED
    )

    override fun onAdded(groupId: Int, download: Download, fetchGroup: FetchGroup) {
        onStarted(groupId, fetchGroup)
        super.onAdded(groupId, download, fetchGroup)
    }

    override fun onCancelled(groupId: Int, download: Download, fetchGroup: FetchGroup) {
        checkCompletion(groupId, fetchGroup)
        super.onCancelled(groupId, download, fetchGroup)
    }

    override fun onDeleted(groupId: Int, download: Download, fetchGroup: FetchGroup) {
        checkCompletion(groupId, fetchGroup)
        super.onDeleted(groupId, download, fetchGroup)
    }

    override fun onError(
        groupId: Int,
        download: Download,
        error: Error,
        throwable: Throwable?,
        fetchGroup: FetchGroup
    ) {
        checkCompletion(groupId, fetchGroup)
        super.onError(groupId, download, error, throwable, fetchGroup)
    }

    override fun onRemoved(groupId: Int, download: Download, fetchGroup: FetchGroup) {
        checkCompletion(groupId, fetchGroup)
        super.onRemoved(groupId, download, fetchGroup)
    }

    override fun onCompleted(groupId: Int, download: Download, fetchGroup: FetchGroup) {
        checkCompletion(groupId, fetchGroup)
        super.onCompleted(groupId, download, fetchGroup)
    }

    private fun checkCompletion(groupId: Int, fetchGroup: FetchGroup) {
        val statuses = fetchGroup.downloads.map { it.status }
        if (statuses.all { it in finishedStatuses } && statuses.isNotEmpty()) {
            // All downloads finished (due to error, removed, deleted, or completed)
            onFinished(groupId, fetchGroup)
        }
    }
}
