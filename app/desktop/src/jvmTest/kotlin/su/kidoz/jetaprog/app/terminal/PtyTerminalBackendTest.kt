package su.kidoz.jetaprog.app.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PtyTerminalBackendTest {
    @Test
    fun slowCollectorReceivesEveryUtf8ChunkBeforeOneExit() =
        runTest {
            val expected =
                buildString {
                    repeat(4_000) { index -> append("line-").append(index).append("-✅\u001b[31m\n") }
                    append("final-🙂-é-\u001b[0m")
                }
            val process = FakeTerminalProcess(ChunkedInputStream(expected.encodeToByteArray(), 37), exitCode = 7)
            val backend = PtyTerminalBackend(process)
            val events = mutableListOf<TerminalBackendOutput>()

            withContext(Dispatchers.Default) {
                withTimeout(10_000) {
                    backend.output.collect { event ->
                        events += event
                        if (event is TerminalBackendOutput.Text) delay(1)
                    }
                }
            }

            val text = events.filterIsInstance<TerminalBackendOutput.Text>().joinToString("") { it.value }
            assertEquals(expected, text)
            assertFalse(text.contains('\ufffd'))
            assertEquals(listOf(7), events.filterIsInstance<TerminalBackendOutput.Exited>().map { it.exitCode })
            assertTrue(events.last() is TerminalBackendOutput.Exited)
            assertTrue(events.filterIsInstance<TerminalBackendOutput.Error>().isEmpty())
            assertTrue(process.allStreamsClosed)
            backend.dispose()
        }

    @Test
    fun readFailureIsEmittedBeforeTerminalExit() =
        runTest {
            val process = FakeTerminalProcess(FailingInputStream("before".encodeToByteArray()), exitCode = 9)
            val backend = PtyTerminalBackend(process)

            val events = mutableListOf<TerminalBackendOutput>()
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { backend.output.toList(events) }
            }

            assertEquals("before", events.filterIsInstance<TerminalBackendOutput.Text>().joinToString("") { it.value })
            val errors = events.filterIsInstance<TerminalBackendOutput.Error>()
            assertEquals(1, errors.size)
            assertTrue(errors.single().message.contains("Failed to read terminal output"))
            assertTrue(errors.single().message.contains("broken input"))
            assertEquals(listOf(9), events.filterIsInstance<TerminalBackendOutput.Exited>().map { it.exitCode })
            assertTrue(events.last() is TerminalBackendOutput.Exited)
            assertTrue(process.allStreamsClosed)
            backend.dispose()
        }

    @Test
    fun writeFailureIsReportedAndCleansUpProcess() =
        runTest {
            val output = RecordingOutputStream(failure = IOException("broken pipe"))
            val process =
                FakeTerminalProcess(
                    input = BlockingInputStream(),
                    output = output,
                    exitCode = 1,
                    waitForDestroy = true,
                )
            val backend = PtyTerminalBackend(process)

            val error = assertFailsWith<IOException> { backend.write("input".encodeToByteArray()) }

            assertTrue(error.message.orEmpty().contains("Failed to write terminal input"))
            assertTrue(error.message.orEmpty().contains("broken pipe"))
            assertEquals(1, process.destroyCount.get())
            assertTrue(process.allStreamsClosed)
            backend.dispose()
            assertEquals(1, process.destroyCount.get())
        }

    @Test
    fun cancellingWriteIsNotSwallowedOrReportedAsFailure() =
        runTest {
            val output = BlockingOutputStream()
            val process =
                FakeTerminalProcess(
                    input = BlockingInputStream(),
                    output = output,
                    waitForDestroy = true,
                )
            val backend = PtyTerminalBackend(process)
            val writeJob = launch(Dispatchers.Default) { backend.write("input".encodeToByteArray()) }
            assertTrue(output.writeStarted.await(5, TimeUnit.SECONDS))

            writeJob.cancel()
            output.releaseWrites()
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { writeJob.join() }
            }

            assertTrue(writeJob.isCancelled)
            assertEquals(0, process.destroyCount.get())
            backend.dispose()
        }

    @Test
    fun killAndDisposeAreIdempotentAndStillDeliverExit() =
        runTest {
            val process =
                FakeTerminalProcess(
                    input = BlockingInputStream(),
                    exitCode = 137,
                    waitForDestroy = true,
                )
            val backend = PtyTerminalBackend(process)

            backend.kill()
            backend.kill()
            val events = mutableListOf<TerminalBackendOutput>()
            withTimeout(5_000) { backend.output.toList(events) }
            backend.dispose()
            backend.dispose()

            assertEquals(1, process.destroyCount.get())
            assertEquals<List<TerminalBackendOutput>>(listOf(TerminalBackendOutput.Exited(137)), events)
            assertTrue(process.allStreamsClosed)
        }

    @Test
    fun disposeDuringReadClosesStreamsAndStopsCollection() =
        runTest {
            val input = BlockingInputStream()
            val process = FakeTerminalProcess(input = input, waitForDestroy = true)
            val backend = PtyTerminalBackend(process)
            val collection =
                launch(Dispatchers.Default) {
                    backend.output.collect { }
                }
            assertTrue(input.readStarted.await(5, TimeUnit.SECONDS))

            backend.dispose()
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { collection.join() }
            }

            assertTrue(collection.isCompleted)
            assertEquals(1, process.destroyCount.get())
            assertTrue(process.allStreamsClosed)
        }

    @Test
    fun realPtyPreservesEscapeAndUtf8Output() =
        runTest {
            if (!File("/bin/sh").canExecute()) return@runTest
            val expected = "\u001b[31mA🙂é\u001b[0m"
            val result =
                PtyTerminalBackend.start(
                    command = listOf("/bin/sh", "-c", "printf '\\033[31mA🙂é\\033[0m'; exit 7"),
                    workingDirectory = System.getProperty("user.dir"),
                )
            val backend = result.getOrThrow()

            try {
                val events = mutableListOf<TerminalBackendOutput>()
                withContext(Dispatchers.Default) {
                    withTimeout(10_000) { backend.output.toList(events) }
                }
                assertEquals(
                    expected,
                    events.filterIsInstance<TerminalBackendOutput.Text>().joinToString("") { it.value },
                )
                assertEquals(listOf(7), events.filterIsInstance<TerminalBackendOutput.Exited>().map { it.exitCode })
            } finally {
                backend.dispose()
            }
        }
}

private class FakeTerminalProcess(
    input: InputStream,
    output: RecordingOutputStream = RecordingOutputStream(),
    private val exitCode: Int = 0,
    private val waitForDestroy: Boolean = false,
) : TerminalProcessHandle {
    override val inputStream: TrackingInputStream = TrackingInputStream(input)
    override val outputStream: RecordingOutputStream = output
    override val errorStream: TrackingInputStream = TrackingInputStream(ByteArrayInputStream(ByteArray(0)))
    private val destroyed = CountDownLatch(1)
    private val alive = AtomicBoolean(true)

    val destroyCount = AtomicInteger()

    override val isAlive: Boolean
        get() = alive.get()

    val allStreamsClosed: Boolean
        get() = inputStream.closed && outputStream.closed && errorStream.closed

    override fun waitFor(): Int {
        if (waitForDestroy) destroyed.await()
        alive.set(false)
        return exitCode
    }

    override fun destroyForcibly() {
        destroyCount.incrementAndGet()
        alive.set(false)
        destroyed.countDown()
    }

    override fun setWinSize(
        columns: Int,
        rows: Int,
    ) = Unit
}

private class ChunkedInputStream(
    bytes: ByteArray,
    private val chunkSize: Int,
) : InputStream() {
    private val delegate = ByteArrayInputStream(bytes)

    override fun read(): Int = delegate.read()

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = delegate.read(buffer, offset, minOf(length, chunkSize))
}

private class FailingInputStream(
    bytes: ByteArray,
) : InputStream() {
    private val delegate = ByteArrayInputStream(bytes)

    override fun read(): Int = delegate.read().also { if (it < 0) throw IOException("broken input") }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val read = delegate.read(buffer, offset, minOf(length, 2))
        if (read < 0) throw IOException("broken input")
        return read
    }
}

private class BlockingInputStream : InputStream() {
    private val released = CountDownLatch(1)
    private val closed = AtomicBoolean()

    val readStarted = CountDownLatch(1)

    override fun read(): Int = blockRead()

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = blockRead()

    override fun close() {
        closed.set(true)
        released.countDown()
    }

    private fun blockRead(): Int {
        if (closed.get()) return -1
        readStarted.countDown()
        try {
            released.await()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(interrupted)
        }
        return -1
    }
}

private class TrackingInputStream(
    private val delegate: InputStream,
) : InputStream() {
    @Volatile
    var closed: Boolean = false
        private set

    override fun read(): Int = delegate.read()

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = delegate.read(buffer, offset, length)

    override fun close() {
        closed = true
        delegate.close()
    }
}

private open class RecordingOutputStream(
    private val failure: IOException? = null,
) : OutputStream() {
    private val delegate = ByteArrayOutputStream()

    @Volatile
    var closed: Boolean = false
        private set

    override fun write(value: Int) {
        failure?.let { throw it }
        delegate.write(value)
    }

    override fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ) {
        failure?.let { throw it }
        delegate.write(buffer, offset, length)
    }

    override fun close() {
        closed = true
        delegate.close()
    }
}

private class BlockingOutputStream : RecordingOutputStream() {
    private val released = CountDownLatch(1)

    val writeStarted = CountDownLatch(1)

    override fun write(value: Int) = blockWrite()

    override fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ) = blockWrite()

    override fun close() {
        releaseWrites()
        super.close()
    }

    fun releaseWrites() {
        released.countDown()
    }

    private fun blockWrite() {
        writeStarted.countDown()
        try {
            released.await()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(interrupted)
        }
    }
}
