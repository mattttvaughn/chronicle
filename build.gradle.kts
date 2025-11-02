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

// Ensure the app preBuild depends on the git hook installer. Use matching/configureEach to avoid
// deprecated fileCollection/spec usage that can appear with getByPath on newer Gradle.
tasks.matching { it.path == ":app:preBuild" }.configureEach {
    dependsOn(rootProject.tasks.named("installGitHook"))
}
