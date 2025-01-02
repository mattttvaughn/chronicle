plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "io.github.mattpvaughn.chronicle"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.mattpvaughn.chronicle"
        minSdk = 31
        targetSdk = 34
        versionCode = 27
        versionName = "0.55.0"

        testInstrumentationRunner = "io.github.mattpvaughn.chronicle.application.ChronicleTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
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

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter)

    implementation(libs.okhttp3)
    implementation(libs.okhttp3.logging)

    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    implementation(libs.fresco)
    implementation(libs.fresco.imagepipeline)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    annotationProcessor(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.dagger)
    annotationProcessor(libs.dagger.compiler)

    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.ui)
    implementation(libs.exoplayer.mediasession)
}