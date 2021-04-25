package io.github.mattpvaughn.chronicle

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Singleton
class CoroutineDispatchers(
    val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
)