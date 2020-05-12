package io.github.mattpvaughn.chronicle.application

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class ChronicleTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, TestChronicleApplication::class.java.name, context)
    }
}