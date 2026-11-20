import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

val ktlintVersion = libs.versions.ktlint.get()
val detektConfig = layout.projectDirectory.file("config/detekt/detekt.yml")

// ktlint's function-naming rule does not model Compose's PascalCase conventions.
// detekt's FunctionNaming rule enforces naming instead and ignores @Composable functions.
val ktlintOverrides = mapOf("ktlint_standard_function-naming" to "disabled")

spotless {
    kotlinGradle {
        target("*.gradle.kts", "gradle/*.gradle.kts")
        ktlint(ktlintVersion)
            .setEditorConfigPath(file(".editorconfig"))
            .editorConfigOverride(ktlintOverrides)
    }
    format("misc") {
        target("*.md", ".gitignore", ".editorconfig")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(ktlintVersion)
                .setEditorConfigPath(rootProject.file(".editorconfig"))
                .editorConfigOverride(ktlintOverrides)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
                .setEditorConfigPath(rootProject.file(".editorconfig"))
                .editorConfigOverride(ktlintOverrides)
        }
    }

    configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(detektConfig)
        parallel = true
        source.setFrom(files("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin"))
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
    }

    listOf("com.android.application", "com.android.library", "com.android.lint").forEach { pluginId ->
        plugins.withId(pluginId) {
            tasks.named("check").configure { dependsOn("lint") }
        }
    }
}
