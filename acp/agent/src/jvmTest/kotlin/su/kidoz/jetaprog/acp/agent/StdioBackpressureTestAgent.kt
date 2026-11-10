package su.kidoz.jetaprog.acp.agent

/** Subprocess fixture for pipe shutdown and frame-size limits. */
public object StdioBackpressureTestAgent {
    @JvmStatic
    public fun main(args: Array<String>) {
        if (args.single() == "oversized") {
            repeat(9_000) { print("x".repeat(1_000)) }
            println()
            System.out.flush()
        } else {
            println("ready")
            System.out.flush()
            Thread.sleep(60_000)
        }
    }
}
