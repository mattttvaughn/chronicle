plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    alias(libs.plugins.ktlint)
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // Add this part: configure KAPT task arguments reflectively to avoid compile-time
    // dependency on Kotlin Gradle plugin types in the root script.
    // This finds tasks whose implementation class is org.jetbrains.kotlin.gradle.tasks.KaptTask
    // and invokes the kaptArgs.arg(...) method via reflection.
    tasks.matching { it.javaClass.name == "org.jetbrains.kotlin.gradle.tasks.KaptTask" }
        .configureEach {
            try {
                val kaptArgsMethod = this.javaClass.methods.firstOrNull { m -> m.name == "kaptArgs" }
                if (kaptArgsMethod != null) {
                    val kaptArgs = kaptArgsMethod.invoke(this)
                    val argMethod = kaptArgs.javaClass.methods.firstOrNull { m -> m.name == "arg" && m.parameterTypes.size == 2 }
                    if (argMethod != null) {
                        val addOpens =
                            listOf(
                                "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                                "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                                "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
                                "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
                                "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
                                "jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
                                "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
                                "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
                                "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                                "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                            )
                        addOpens.forEach { value ->
                            argMethod.invoke(kaptArgs, "--add-opens", value)
                        }
                    }
                }
            } catch (e: Exception) {
                // Swallow any reflection errors here; if reflection fails then the
                // specific Gradle/Kotlin plugin version may not expose the same API.
                logger.debug("Failed to configure kaptArgs via reflection: ${e.message}")
            }
        }
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
