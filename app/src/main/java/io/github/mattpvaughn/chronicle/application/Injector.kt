package io.github.mattpvaughn.chronicle.application

import io.github.mattpvaughn.chronicle.injection.components.AppComponent

class Injector private constructor() {
    companion object {
        fun get() : AppComponent = ChronicleApplication.get().appComponent
    }
}