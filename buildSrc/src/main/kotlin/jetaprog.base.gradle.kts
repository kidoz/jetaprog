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
        jvmTarget.set(JvmTarget.JVM_26)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "26"
    targetCompatibility = "26"
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
    // Multiplatform sources live under commonMain, jvmMain and matching test sets.
    source.setFrom(
        fileTree("src") {
            include("**/*.kt", "**/*.kts")
            exclude("**/generated/**")
        },
    )
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    // Detekt 1.23.8's embedded compiler supports targets through 22. These
    // source-only checks need no JDK classpath and cannot inspect the Java 26
    // toolchain automatically supplied by Gradle. Application compilation stays on 26.
    jvmTarget = "22"
    jdkHome.unset()
    jdkHome.unsetConvention()
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
}
