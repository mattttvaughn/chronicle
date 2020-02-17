package io.github.mattpvaughn.chronicle.application

import android.app.Application
import android.app.DownloadManager
import android.app.Service
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import io.github.mattpvaughn.chronicle.data.local.*
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.ICachedFileManager
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.SharedPreferencesPlexPrefsRepo
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.ProgressUpdater
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import io.github.mattpvaughn.chronicle.features.settings.SharedPreferencesPrefsRepo
import io.github.mattpvaughn.chronicle.injection.components.DaggerAppComponent
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.inject.Named
import javax.inject.Singleton


class MainActivityTest {
    @get:Rule
    var activityScenarioRule = activityScenarioRule<MainActivity>()

    @Rule
    var activityRule = ActivityTestRule(
        MainActivity::class.java,
        true,  // initialTouchMode
        false
    )

    @Test
    fun testInjection() {
        val activity = activityRule.launchActivity(null)

    }

}