plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    id("kotlin-kapt")
    id("com.google.android.gms.oss-licenses-plugin")
}

android {
    namespace = "io.github.mattpvaughn.chronicle"
    compileSdk = 34

    lint {
        abortOnError = false
        baseline = file("lint-baseline.xml")
        checkReleaseBuilds = true
        checkAllWarnings = true
    }

    defaultConfig {
        applicationId = "io.github.mattpvaughn.chronicle"
        minSdk = 27
        targetSdk = 34
        versionCode = 27
        versionName = "0.55.0"

        testInstrumentationRunner = "io.github.mattpvaughn.chronicle.application.ChronicleTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"

        freeCompilerArgs += "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
    }
    buildFeatures {
        dataBinding = true
        buildConfig = true
    }

    // Using kapt instead of KSP in the root project-level build to avoid plugin
    // resolution issues while upgrading Kotlin. KAPT is applied via the
    // 'kotlin-kapt' plugin above.
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.glide)
    implementation(libs.timber)
    implementation(libs.iapwrapper)
    implementation(libs.fetch)
    implementation(libs.work)
    implementation(libs.result)
    implementation(libs.swiperefresh)
    implementation(libs.seismic)
    implementation(libs.browserx)
    implementation(libs.oss)
    implementation(libs.appcompat)
    implementation(libs.annotation)
    implementation(libs.coroutines)
    compileOnly(libs.facebook.infer.annotation)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter)

    implementation(libs.okhttp3)
    implementation(libs.okhttp3.logging)

    implementation(libs.moshi)
    // Removed moshi-codegen KAPT processor - deprecated for Kotlin 2.x
    // Moshi will use reflection-based adapters instead

    implementation(libs.fresco)
    implementation(libs.fresco.imagepipeline)

    implementation(libs.room.runtime)
    kapt(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.dagger)
    kapt(libs.dagger.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.media3.cast)

    /*
     * Local Tests
     */
    testImplementation(libs.dagger)
    kaptTest(libs.dagger.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.hamcrest)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)

    /*
     * Instrumented Tests
     */
    androidTestImplementation(libs.dagger)
    kaptAndroidTest(libs.dagger.compiler)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.mockk)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.contrib)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
}

tasks.matching { it.name.contains("DebugAndroidTest") && !it.name.contains("Lint") }.configureEach {
    enabled = false
}
