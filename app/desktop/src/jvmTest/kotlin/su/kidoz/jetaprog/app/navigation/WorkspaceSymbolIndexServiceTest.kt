package su.kidoz.jetaprog.app.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Saving a file under build output or a hidden directory used to add its symbols
 * to the index even though the workspace walk skips those directories.
 */
class WorkspaceSymbolIndexServiceTest {
    @Test
    fun excludesBuildOutputAndHiddenDirectoriesRelativeToTheRoot() {
        val root = "/Users/dev/.projects/app"

        assertTrue(WorkspaceSymbolIndexService.isExcluded(root, "$root/build/generated/Gen.java"))
        assertTrue(WorkspaceSymbolIndexService.isExcluded(root, "$root/.git/hooks/Hook.java"))
        assertTrue(WorkspaceSymbolIndexService.isExcluded(root, "$root/web/node_modules/x/index.js"))
        assertFalse(WorkspaceSymbolIndexService.isExcluded(root, "$root/src/Main.java"))
        assertFalse(WorkspaceSymbolIndexService.isExcluded(root, "$root/src/.hidden.java"))
    }
}
