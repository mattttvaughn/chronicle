package io.github.mattpvaughn.chronicle.views

import android.content.Context
import android.util.AttributeSet
import com.facebook.drawee.generic.GenericDraweeHierarchy
import com.facebook.drawee.generic.GenericDraweeHierarchyInflater
import com.facebook.drawee.view.DraweeView
import com.facebook.imagepipeline.systrace.FrescoSystrace

/**
 * Thin replacement for Fresco's deprecated [com.facebook.drawee.view.GenericDraweeView].
 * It inflates a [GenericDraweeHierarchy] from XML attributes without extending the deprecated class.
 */
class ChronicleDraweeView : DraweeView<GenericDraweeHierarchy> {
    constructor(context: Context, hierarchy: GenericDraweeHierarchy) : super(context) {
        setHierarchy(hierarchy)
    }

    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        defStyleRes: Int = 0,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        inflateHierarchy(context, attrs)
    }

    private fun inflateHierarchy(
        context: Context,
        attrs: AttributeSet?,
    ) {
        val traceTag = "ChronicleDraweeView#inflateHierarchy"
        val tracing = FrescoSystrace.isTracing()
        if (tracing) {
            FrescoSystrace.beginSection(traceTag)
        }
        val builder = GenericDraweeHierarchyInflater.inflateBuilder(context, attrs)
        aspectRatio = builder.desiredAspectRatio
        hierarchy = builder.build()
        if (tracing) {
            FrescoSystrace.endSection()
        }
    }
}
