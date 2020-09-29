package io.github.mattpvaughn.chronicle.features.download

import com.tonyodev.fetch2.AbstractFetchGroupListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.FetchGroup

abstract class FetchGroupStartFinishListener : AbstractFetchGroupListener() {

    abstract fun onStarted(groupId: Int, fetchGroup: FetchGroup)
    abstract fun onFinished(groupId: Int, fetchGroup: FetchGroup)

    override fun onAdded(groupId: Int, download: Download, fetchGroup: FetchGroup) {
        onStarted(groupId, fetchGroup)
        super.onAdded(groupId, download, fetchGroup)
    }

    override fun onCancelled(groupId: Int, download: Download, fetchGroup: FetchGroup) {
        onFinished(groupId, fetchGroup)
        super.onCancelled(groupId, download, fetchGroup)
    }

    override fun onDeleted(groupId: Int, download: Download, fetchGroup: FetchGroup) {
        onFinished(groupId, fetchGroup)
        super.onDeleted(groupId, download, fetchGroup)
    }

    override fun onError(
        groupId: Int,
        download: Download,
        error: Error,
        throwable: Throwable?,
        fetchGroup: FetchGroup
    ) {
        onFinished(groupId, fetchGroup)
        super.onError(groupId, download, error, throwable, fetchGroup)
    }

    override fun onRemoved(groupId: Int, download: Download, fetchGroup: FetchGroup) {
        onFinished(groupId, fetchGroup)
        super.onRemoved(groupId, download, fetchGroup)
    }

    override fun onCompleted(groupId: Int, download: Download, fetchGroup: FetchGroup) {
        onFinished(groupId, fetchGroup)
        super.onCompleted(groupId, download, fetchGroup)
    }
}