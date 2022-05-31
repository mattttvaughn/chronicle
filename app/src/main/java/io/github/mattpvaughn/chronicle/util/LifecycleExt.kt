package io.github.mattpvaughn.chronicle.util

import androidx.annotation.MainThread
import androidx.lifecycle.*
import kotlinx.coroutines.*

inline fun <T> LiveData<Event<T>>.observeEvent(
    owner: LifecycleOwner,
    crossinline onEventUnhandledContent: (T) -> Unit
) {
    observe(owner) { it.getContentIfNotHandled()?.let(onEventUnhandledContent) }
}

fun <T> MutableLiveData<Event<T>>.postEvent(value: T) {
    postValue(Event(value))
}

/**
 * An in-place alternative to [Transformations.map] with [mapFunction] dispatched via
 * [Dispatchers.IO]
 */
@MainThread
fun <X, Y> mapAsync(
    source: LiveData<X>,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    mapFunction: suspend (X) -> Y
): LiveData<Y> {
    val result = MediatorLiveData<Y>()
    result.addSource(source) { x ->
        scope.launch {
            // TODO: why does compiler think this can be nullable?
            result.value = withContext(dispatcher) { mapFunction(x) }!!
        }
    }
    return result
}

/** A wrapper for data exposed via [LiveData] representing an event */
open class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set // Allow external read but not write

    /** Returns the content and prevents its use again. */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /** Returns the content, even if it's already been handled. */
    fun peekContent(): T = content
}

/** A [MediatorLiveData] implementation with typed in-place declaration on two [LiveData] */
class DoubleLiveData<T, K, S>(
    source1: LiveData<T>,
    source2: LiveData<K>,
    private val combine: (data1: T?, data2: K?) -> S
) : MediatorLiveData<S>() {

    private var data1: T? = null
    private var data2: K? = null

    init {
        super.addSource(source1) {
            data1 = it
            value = combine(data1, data2)
        }
        super.addSource(source2) {
            data2 = it
            value = combine(data1, data2)
        }
    }

    override fun <T : Any?> addSource(source: LiveData<T>, onChanged: Observer<in T>) {
        throw UnsupportedOperationException()
    }

    override fun <T : Any?> removeSource(toRemote: LiveData<T>) {
        throw UnsupportedOperationException()
    }
}

/** A [MediatorLiveData] implementation with typed in-place declaration on three [LiveData] */
class TripleLiveData<T, K, S, R>(
    source1: LiveData<T>,
    source2: LiveData<K>,
    source3: LiveData<S>,
    private val combine: (data1: T?, data2: K?, data3: S?) -> R
) : MediatorLiveData<R>() {

    private var data1: T? = null
    private var data2: K? = null
    private var data3: S? = null

    init {
        super.addSource(source1) {
            data1 = it
            value = combine(data1, data2, data3)
        }
        super.addSource(source2) {
            data2 = it
            value = combine(data1, data2, data3)
        }
        super.addSource(source3) {
            data3 = it
            value = combine(data1, data2, data3)
        }
    }

    override fun <T : Any?> addSource(source: LiveData<T>, onChanged: Observer<in T>): Unit =
        throw UnsupportedOperationException()

    override fun <T : Any?> removeSource(toRemote: LiveData<T>): Unit =
        throw UnsupportedOperationException()
}

/** A [MediatorLiveData] implementation with typed in-place declaration on four [LiveData] */
class QuadLiveData<T, K, S, Q, R>(
    source1: LiveData<T>,
    source2: LiveData<K>,
    source3: LiveData<S>,
    source4: LiveData<Q>,
    private val combine: (data1: T?, data2: K?, data3: S?, data4: Q?) -> R
) : MediatorLiveData<R>() {

    private var data1: T? = null
    private var data2: K? = null
    private var data3: S? = null
    private var data4: Q? = null

    init {
        super.addSource(source1) {
            data1 = it
            value = combine(data1, data2, data3, data4)
        }
        super.addSource(source2) {
            data2 = it
            value = combine(data1, data2, data3, data4)
        }
        super.addSource(source3) {
            data3 = it
            value = combine(data1, data2, data3, data4)
        }
        super.addSource(source4) {
            data4 = it
            value = combine(data1, data2, data3, data4)
        }
    }

    override fun <T : Any?> addSource(source: LiveData<T>, onChanged: Observer<in T>): Unit =
        throw UnsupportedOperationException()

    override fun <T : Any?> removeSource(toRemote: LiveData<T>): Unit =
        throw UnsupportedOperationException()
}

/**
 * A wrapper around [MediatorLiveData] which allows for typed in-place declaration on four
 * [LiveData] combined via a suspend function on [Dispatchers.IO]
 */
class QuadLiveDataAsync<T, K, S, Q, R>(
    private val scope: CoroutineScope,
    source1: LiveData<T>,
    source2: LiveData<K>,
    source3: LiveData<S>,
    source4: LiveData<Q>,
    private val combine: suspend (data1: T?, data2: K?, data3: S?, data4: Q?) -> R
) : MediatorLiveData<R>() {

    private var data1: T? = null
    private var data2: K? = null
    private var data3: S? = null
    private var data4: Q? = null

    init {
        super.addSource(source1) {
            data1 = it
            computeValue()
        }
        super.addSource(source2) {
            data2 = it
            computeValue()
        }
        super.addSource(source3) {
            data3 = it
            computeValue()
        }
        super.addSource(source4) {
            data4 = it
            computeValue()
        }
    }

    private fun computeValue() {
        scope.launch {
            value = withContext(Dispatchers.IO) { combine(data1, data2, data3, data4) }
        }
    }

    override fun <T : Any?> addSource(source: LiveData<T>, onChanged: Observer<in T>): Unit =
        throw UnsupportedOperationException()

    override fun <T : Any?> removeSource(toRemote: LiveData<T>): Unit =
        throw UnsupportedOperationException()
}

/**
 * A wrapper around [MediatorLiveData] which allows for typed in-place declaration on five
 * [LiveData] combined via a suspend function on [Dispatchers.IO]
 */
class QuintLiveDataAsync<T, K, S, Q, P, R>(
    private val scope: CoroutineScope,
    source1: LiveData<T>,
    source2: LiveData<K>,
    source3: LiveData<S>,
    source4: LiveData<Q>,
    source5: LiveData<P>,
    private val combine: suspend (data1: T?, data2: K?, data3: S?, data4: Q?, data5: P?) -> R
) : MediatorLiveData<R>() {

    private var data1: T? = null
    private var data2: K? = null
    private var data3: S? = null
    private var data4: Q? = null
    private var data5: P? = null

    init {
        super.addSource(source1) {
            data1 = it
            computeValue()
        }
        super.addSource(source2) {
            data2 = it
            computeValue()
        }
        super.addSource(source3) {
            data3 = it
            computeValue()
        }
        super.addSource(source4) {
            data4 = it
            computeValue()
        }
        super.addSource(source5) {
            data5 = it
            computeValue()
        }
    }

    private fun computeValue() {
        scope.launch {
            value = withContext(Dispatchers.IO) { combine(data1, data2, data3, data4, data5) }
        }
    }

    override fun <T : Any?> addSource(source: LiveData<T>, onChanged: Observer<in T>): Unit =
        throw UnsupportedOperationException()

    override fun <T : Any?> removeSource(toRemote: LiveData<T>): Unit =
        throw UnsupportedOperationException()
}

/**
 * [MediatorLiveData] implementation for in-place declaration of arbitrary number of [LiveData].
 *
 * Useful for combining large numbers of [LiveData], but disadvantaged by the lack of type-safety
 * caused by storing data as a [List<Any>]
 */
class CombinedLiveData<R>(
    vararg liveData: LiveData<*>,
    private val combine: (data: List<Any?>) -> R
) : MediatorLiveData<R>() {

    private val data: MutableList<Any?> = MutableList(liveData.size) { null }

    init {
        for (i in liveData.indices) {
            super.addSource(liveData[i]) {
                data[i] = it
                value = combine(data)
            }
        }
    }
}
