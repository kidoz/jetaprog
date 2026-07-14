package su.kidoz.jetaprog.dap.jvm

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream

/**
 * Entry point for the standalone JVM debug adapter process.
 *
 * Speaks DAP over stdin/stdout. Anything third-party code prints via
 * `System.out` is redirected to stderr so it cannot corrupt the protocol
 * stream.
 */
public fun main() {
    val dapOutput = FileOutputStream(FileDescriptor.out)
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.err), true))

    val connection = DapServerConnection(System.`in`, dapOutput)
    JvmDebugAdapter(connection).run()
}
