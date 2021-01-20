package io.github.mattpvaughn.chronicle.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

class HorizontalChildReadySwipeRefreshLayout : SwipeRefreshLayout {

    private var touchSlop: Int = 0
    private var prevX: Float = 0F
    private var declined: Boolean = false

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    @Override
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        when (ev?.action) {
            ACTION_DOWN -> {
                val event = MotionEvent.obtain(ev)
                prevX = event.x
                event.recycle()
                declined = false
            }
            ACTION_MOVE -> {
                val eventX: Float = ev.x
                val xDiff: Float = abs(eventX - prevX)

                if (declined || xDiff > touchSlop) {
                    declined = true
                    return false
                }

            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}