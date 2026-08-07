package su.kidoz.jetaprog.app.project

import su.kidoz.jetaprog.build.gradle.wrapper.GradleWrapperSpec

/** Version policy shared by the new-project wizard and generated build files. */
internal object ProjectTemplateVersions {
    const val KOTLIN = "2.4.10"
    const val GRADLE = "9.7.0"
    const val JVM_TOOLCHAIN = 25
    const val GRADLE_DISTRIBUTION_SHA256 = "84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae"

    val supportedKotlinVersions = listOf(KOTLIN, "2.3.21", "2.2.21")
    val supportedJavaVersions = listOf(JVM_TOOLCHAIN.toString(), "21", "17")

    val gradleWrapper =
        GradleWrapperSpec(
            gradleVersion = GRADLE,
            distributionSha256Sum = GRADLE_DISTRIBUTION_SHA256,
        )
}
