package su.kidoz.jetaprog.configuration.execution

import kotlin.test.Test
import kotlin.test.assertEquals

class GoTestJsonAccumulatorTest {
    @Test
    fun `aggregates terminal test events and package failures`() {
        val accumulator = GoTestJsonAccumulator()

        accumulator.accept("not json")
        accumulator.accept("""{"Action":"run","Package":"example.com/app","Test":"TestPass"}""")
        accumulator.accept("""{"Action":"pass","Package":"example.com/app","Test":"TestPass"}""")
        accumulator.accept("""{"Action":"fail","Package":"example.com/app","Test":"TestFail"}""")
        accumulator.accept("""{"Action":"skip","Package":"example.com/app","Test":"TestSkip"}""")
        accumulator.accept("""{"Action":"fail","Package":"example.com/broken"}""")

        assertEquals(
            GoTestSummary(
                passed = 1,
                failed = 1,
                skipped = 1,
                failedPackages = setOf("example.com/broken"),
            ),
            accumulator.summary(),
        )
    }
}
