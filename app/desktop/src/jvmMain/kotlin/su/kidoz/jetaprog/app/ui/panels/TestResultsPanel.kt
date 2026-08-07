package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.build.gradle.test.GradleTestCase
import su.kidoz.jetaprog.build.gradle.test.GradleTestRun
import su.kidoz.jetaprog.build.gradle.test.GradleTestStatus
import su.kidoz.jetaprog.build.gradle.test.GradleTestSuite

/** Displays structured Gradle test suites and supports focused test reruns. */
@Composable
public fun TestResultsPanel(
    testRun: GradleTestRun?,
    onRerunTest: (taskPath: String, pattern: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(IntelliJColors.toolWindowBackground)) {
        TestSummary(testRun)
        if (testRun == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Run a Gradle test task to see structured results",
                    color = IntelliJColors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                testRun.suites.forEach { suite ->
                    item(key = suite.reportPath) { TestSuiteHeader(suite) }
                    items(
                        items = suite.cases,
                        key = { testCase -> "${suite.reportPath}:${testCase.suiteName}:${testCase.name}" },
                    ) { testCase ->
                        TestCaseRow(
                            testCase = testCase,
                            onRerun = {
                                onRerunTest(suite.taskPath, "${testCase.suiteName}.${testCase.name}")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TestSummary(testRun: GradleTestRun?) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.toolWindowHeader)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "TEST RESULTS",
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        testRun?.let { run ->
            SummaryItem("${run.passedCount} passed", IntelliJColors.success)
            SummaryItem("${run.failedCount} failed", IntelliJColors.error)
            SummaryItem("${run.skippedCount} skipped", IntelliJColors.warning)
            Text(
                text = formatTestDuration(run.durationMs),
                color = IntelliJColors.textSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SummaryItem(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(text = text, color = color, fontSize = 12.sp)
}

@Composable
private fun TestSuiteHeader(suite: GradleTestSuite) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
    ) {
        Text(
            text = suite.name,
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = suite.taskPath,
            color = IntelliJColors.textSecondary,
            fontSize = 11.sp,
            fontFamily = JetaProgFonts.codeFont,
        )
    }
}

@Composable
private fun TestCaseRow(
    testCase: GradleTestCase,
    onRerun: () -> Unit,
) {
    var expanded by remember(testCase) { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = testCase.status == GradleTestStatus.FAILED) { expanded = !expanded }
                .padding(start = Spacing.lg.dp, end = Spacing.sm.dp, top = Spacing.xs.dp, bottom = Spacing.xs.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = testCase.status.icon(),
                contentDescription = testCase.status.name.lowercase(),
                tint = testCase.status.color(),
                modifier = Modifier.size(Dimensions.iconSm.dp),
            )
            Text(
                text = testCase.name,
                color = IntelliJColors.textPrimary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatTestDuration(testCase.durationMs),
                color = IntelliJColors.textSecondary,
                fontSize = 11.sp,
                fontFamily = JetaProgFonts.codeFont,
            )
            IconButton(onClick = onRerun, modifier = Modifier.size(Dimensions.toolbarIcon.dp)) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Rerun ${testCase.name}",
                    tint = IntelliJColors.textSecondary,
                    modifier = Modifier.size(Dimensions.iconMd.dp),
                )
            }
        }
        if (expanded) {
            Text(
                text = testCase.failureMessage ?: testCase.failureDetails.orEmpty(),
                color = IntelliJColors.error,
                fontSize = 11.sp,
                fontFamily = JetaProgFonts.codeFont,
                modifier = Modifier.padding(start = (Dimensions.iconSm + Spacing.sm).dp, bottom = Spacing.sm.dp),
            )
        }
    }
}

private fun GradleTestStatus.icon() =
    when (this) {
        GradleTestStatus.PASSED -> Icons.Default.CheckCircle
        GradleTestStatus.FAILED -> Icons.Default.Error
        GradleTestStatus.SKIPPED -> Icons.Default.RemoveCircle
    }

private fun GradleTestStatus.color() =
    when (this) {
        GradleTestStatus.PASSED -> IntelliJColors.success
        GradleTestStatus.FAILED -> IntelliJColors.error
        GradleTestStatus.SKIPPED -> IntelliJColors.warning
    }

private fun formatTestDuration(durationMs: Long): String =
    if (durationMs >= MILLIS_PER_SECOND) {
        "${durationMs / MILLIS_PER_SECOND}.${(durationMs % MILLIS_PER_SECOND) / 100}s"
    } else {
        "${durationMs}ms"
    }

private const val MILLIS_PER_SECOND = 1_000
