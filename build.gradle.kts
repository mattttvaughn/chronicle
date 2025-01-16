plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    alias(libs.plugins.ktlint)
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}

buildscript {
    dependencies {
        classpath(libs.oss.plugin)
    }
}

ktlint {
    android.set(true)
}

tasks.register<Copy>("installGitHook") {
    from(rootProject.file("pre-commit"))
    into(rootProject.file(".git/hooks"))
}

tasks.getByPath(":app:preBuild").dependsOn(rootProject.tasks.named("installGitHook"))
