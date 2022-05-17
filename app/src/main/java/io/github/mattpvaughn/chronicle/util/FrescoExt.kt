package io.github.mattpvaughn.chronicle.util

import android.graphics.Bitmap
import com.facebook.common.executors.UiThreadImmediateExecutorService
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun DataSource<CloseableReference<CloseableImage>>.getImage() =
    suspendCoroutine<Bitmap?> { cont ->
        subscribe(
            object : BaseBitmapDataSubscriber() {
                override fun onNewResultImpl(bitmap: Bitmap?) = cont.resume(bitmap)
                override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>) =
                    cont.resume(null)
            },
            UiThreadImmediateExecutorService.getInstance()
        )
    }
