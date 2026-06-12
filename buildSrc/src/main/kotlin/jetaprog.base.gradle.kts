import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(JvmTarget.JVM_25)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "25"
    targetCompatibility = "25"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

ktlint {
    version.set(
        extensions
            .getByType<VersionCatalogsExtension>()
            .named("libs")
            .findVersion("ktlintEngine")
            .get()
            .requiredVersion,
    )
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/generated/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
}
