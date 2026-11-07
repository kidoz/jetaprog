package su.kidoz.jetaprog.build.gradle

/**
 * Accepts a Gradle task path and rejects option-shaped strings.
 *
 * A task path is one or more name segments separated by `:`, as in `test` or
 * `:app:desktop:test`. Values such as `--init-script=file` are Gradle options and
 * must not be passed through as the task argument.
 */
public object GradleTaskPath {
    private val TASK_PATH = Regex("^:?[A-Za-z0-9_][A-Za-z0-9_.-]*(?::[A-Za-z0-9_][A-Za-z0-9_.-]*)*$")

    /**
     * Whether [taskPath] is a single task path.
     */
    public fun isValid(taskPath: String): Boolean = TASK_PATH.matches(taskPath)
}
