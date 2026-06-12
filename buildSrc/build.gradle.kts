plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    val kotlinVersion = libs.versions.kotlin.get()
    val composeVersion = libs.versions.compose.get()
    val ktlintVersion = libs.versions.ktlint.plugin.get()
    val detektVersion = libs.versions.detekt.get()

    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.compose:compose-gradle-plugin:$composeVersion")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:$ktlintVersion")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:$detektVersion")
}
