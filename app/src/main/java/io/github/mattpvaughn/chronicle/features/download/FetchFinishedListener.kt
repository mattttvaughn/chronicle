package io.github.mattpvaughn.chronicle.features.download

import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error

abstract class FetchFinishedListener : AbstractFetchListener() {

    abstract fun onFinished(download: Download)

    override fun onRemoved(download: Download) {
        onFinished(download)
        super.onRemoved(download)
    }

    override fun onDeleted(download: Download) {
        onFinished(download)
        super.onDeleted(download)
    }

    override fun onCancelled(download: Download) {
        onFinished(download)
        super.onCancelled(download)
    }

    override fun onError(download: Download, error: Error, throwable: Throwable?) {
        onFinished(download)
        super.onError(download, error, throwable)
    }

    override fun onCompleted(download: Download) {
        onFinished(download)
        super.onCompleted(download)
    }
}
