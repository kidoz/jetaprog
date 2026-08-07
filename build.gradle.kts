group = "su.kidoz.jetaprog"
version = "1.0.0-SNAPSHOT"

val projectTaskPaths: (String) -> List<String> = { taskName ->
    subprojects
        .filter { project -> project.buildFile.isFile }
        .map { project -> "${project.path}:$taskName" }
}

tasks.register("test") {
    group = "verification"
    description = "Runs tests for every JetaProg module."
    dependsOn(projectTaskPaths("allTests"))
}

tasks.register("verify") {
    group = "verification"
    description = "Runs the complete JetaProg build and quality gate."
    dependsOn(projectTaskPaths("build"))
}
