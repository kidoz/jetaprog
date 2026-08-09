package su.kidoz.jetaprog.app.ui.database

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.database.DatabaseIntent
import su.kidoz.jetaprog.app.database.DatabaseOperation
import su.kidoz.jetaprog.app.database.DatabaseState
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.components.ToolWindow
import su.kidoz.jetaprog.app.ui.components.ToolWindowButton
import su.kidoz.jetaprog.app.ui.dialogs.IntelliJDialog
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.database.DatabaseColumn
import su.kidoz.jetaprog.database.DatabaseConnectionProfile
import su.kidoz.jetaprog.database.DatabaseCredential
import su.kidoz.jetaprog.database.DatabaseDialect
import su.kidoz.jetaprog.database.DatabaseQueryResult
import su.kidoz.jetaprog.database.DatabaseSchema
import su.kidoz.jetaprog.database.DatabaseSslMode
import su.kidoz.jetaprog.database.DatabaseTable

/** Database connection navigator with profile actions and an expandable schema tree. */
@Composable
public fun DatabaseToolWindow(
    state: DatabaseState,
    dispatch: (DatabaseIntent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingProfile by remember { mutableStateOf<DatabaseConnectionProfile?>(null) }

    ToolWindow(
        title = "Database",
        isVisible = true,
        onClose = onClose,
        modifier = modifier,
        icon = Icons.Default.Storage,
        actions = {
            ToolWindowButton(
                icon = Icons.Default.Add,
                onClick = { editingProfile = DatabaseConnectionProfile.create() },
                contentDescription = "Add connection",
            )
            ToolWindowButton(
                icon = Icons.Default.Edit,
                onClick = { editingProfile = state.selectedProfile },
                contentDescription = "Edit connection",
                enabled = state.selectedProfile != null && !state.isBusy,
            )
            ToolWindowButton(
                icon = Icons.Default.Refresh,
                onClick = {
                    if (state.selectedProfile != null && !state.isBusy) dispatch(DatabaseIntent.RefreshSchema)
                },
                contentDescription = "Refresh schema",
                enabled = state.selectedProfile != null && !state.isBusy,
            )
            ToolWindowButton(
                icon = Icons.Default.Delete,
                onClick = {
                    state.selectedProfileId?.let { dispatch(DatabaseIntent.DeleteProfile(it)) }
                },
                contentDescription = "Delete connection",
                enabled = state.selectedProfile != null && !state.isBusy,
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ConnectionList(state = state, dispatch = dispatch)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(Dimensions.editorGuideWidth.dp)
                        .background(IntelliJColors.borderSubtle),
            )
            ConnectionToolbar(state = state, dispatch = dispatch)
            DatabaseSchemaTree(
                schemas = state.schemas,
                isLoading = state.operation == DatabaseOperation.INTROSPECTING,
                modifier = Modifier.weight(1f),
            )
            state.error?.let { InlineStatus(it, IntelliJColors.error) }
        }
    }

    editingProfile?.let { profile ->
        DatabaseConnectionDialog(
            initialProfile = profile,
            onSave = { savedProfile, credential ->
                dispatch(DatabaseIntent.SaveProfile(savedProfile, credential))
                editingProfile = null
            },
            onDismiss = { editingProfile = null },
        )
    }
}

@Composable
private fun ConnectionList(
    state: DatabaseState,
    dispatch: (DatabaseIntent) -> Unit,
) {
    when {
        state.isLoadingProfiles -> {
            EmptyNavigatorMessage("Loading connections…")
        }

        state.profiles.isEmpty() -> {
            EmptyNavigatorMessage("No connections. Use + to add PostgreSQL or StarRocks.")
        }

        else -> {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(Dimensions.databaseConnectionListHeight.dp)) {
                items(state.profiles, key = { it.id }) { profile ->
                    val selected = profile.id == state.selectedProfileId
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(Dimensions.treeNodeHeight.dp)
                                .background(if (selected) IntelliJColors.treeSelectionBackground else Color.Transparent)
                                .clickable { dispatch(DatabaseIntent.SelectProfile(profile.id)) }
                                .padding(horizontal = Spacing.sm.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = if (selected) IntelliJColors.accent else IntelliJColors.textSecondary,
                            modifier = Modifier.size(Dimensions.iconSm.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                color = IntelliJColors.textPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${profile.dialect.displayName} · ${profile.host}:${profile.port}",
                                color = IntelliJColors.textMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionToolbar(
    state: DatabaseState,
    dispatch: (DatabaseIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.sm.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IntelliJButton(
            text = "Test",
            onClick = { dispatch(DatabaseIntent.TestConnection) },
            enabled = state.selectedProfile != null && !state.isBusy,
        )
        IntelliJButton(
            text = "Load schema",
            onClick = { dispatch(DatabaseIntent.RefreshSchema) },
            enabled = state.selectedProfile != null && !state.isBusy,
        )
        if (state.isBusy) {
            ToolWindowButton(
                icon = Icons.Default.Cancel,
                onClick = { dispatch(DatabaseIntent.CancelOperation) },
                contentDescription = "Cancel database operation",
            )
        }
    }
    state.connectionInfo?.let { info ->
        InlineStatus("${info.productName} ${info.productVersion}", IntelliJColors.success)
    }
}

@Composable
private fun DatabaseSchemaTree(
    schemas: List<DatabaseSchema>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val nodes = remember(schemas, expanded) { flattenSchemaTree(schemas, expanded) }
    when {
        isLoading -> {
            EmptyNavigatorMessage("Loading schemas…", modifier)
        }

        schemas.isEmpty() -> {
            EmptyNavigatorMessage("Select a connection and load its schema.", modifier)
        }

        else -> {
            LazyColumn(modifier = modifier.fillMaxWidth()) {
                items(nodes, key = { it.key }) { node ->
                    SchemaTreeRow(
                        node = node,
                        expanded = node.key in expanded,
                        onToggle = {
                            expanded =
                                if (node.key in expanded) expanded - node.key else expanded + node.key
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SchemaTreeRow(
    node: SchemaTreeNode,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.databaseSchemaRowHeight.dp)
                .clickable(enabled = node.expandable, onClick = onToggle)
                .padding(start = (Spacing.sm + node.depth * Spacing.md).dp, end = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        Text(
            text =
                if (node.expandable) {
                    if (expanded) {
                        "▾"
                    } else {
                        "▸"
                    }
                } else {
                    ""
                },
            color = IntelliJColors.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(Dimensions.iconXs.dp),
        )
        Text(
            text = node.label,
            color = if (node is SchemaTreeNode.ColumnNode) IntelliJColors.textSecondary else IntelliJColors.textPrimary,
            fontSize = 11.sp,
            fontFamily = JetaProgFonts.codeFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Full-width SQL editor and bounded query result grid for the bottom tool window. */
@Composable
public fun DatabaseQueryResultsPanel(
    state: DatabaseState,
    dispatch: (DatabaseIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(IntelliJColors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IntelliJTextField(
                value = state.query,
                onValueChange = { dispatch(DatabaseIntent.UpdateQuery(it)) },
                label = state.selectedProfile?.let { "SQL · ${it.name}" } ?: "SQL",
                placeholder = "Enter one SQL statement",
                singleLine = false,
                modifier = Modifier.weight(1f).height(Dimensions.databaseQueryEditorHeight.dp),
            )
            IntelliJButton(
                text = if (state.operation == DatabaseOperation.EXECUTING) "Running…" else "Run",
                onClick = { dispatch(DatabaseIntent.ExecuteQuery) },
                style = ButtonStyle.PRIMARY,
                enabled = state.selectedProfile != null && !state.isBusy && state.query.isNotBlank(),
                icon = Icons.Default.PlayArrow,
            )
            IntelliJButton(
                text = "Stop",
                onClick = { dispatch(DatabaseIntent.CancelOperation) },
                enabled = state.isBusy,
                icon = Icons.Default.Cancel,
            )
            IntelliJButton(
                text = "Clear",
                onClick = { dispatch(DatabaseIntent.ClearResult) },
                enabled = state.result != null || state.error != null,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.editorGuideWidth.dp)
                    .background(IntelliJColors.border),
        )
        state.error?.let { InlineStatus(it, IntelliJColors.error) }
        state.result?.let { result ->
            QueryResultGrid(result = result, modifier = Modifier.weight(1f))
        } ?: Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = if (state.isBusy) "Executing query…" else "Run a query to see results.",
                color = IntelliJColors.textMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun QueryResultGrid(
    result: DatabaseQueryResult,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val status =
            if (result.columns.isEmpty()) {
                "${result.affectedRows ?: 0} rows affected · ${result.elapsedMillis} ms"
            } else {
                "${result.rows.size} rows${if (result.truncated) " (limited)" else ""} · ${result.elapsedMillis} ms"
            }
        InlineStatus(status, IntelliJColors.textSecondary)
        result.warnings.forEach { warning -> InlineStatus(warning, IntelliJColors.warning) }
        if (result.columns.isNotEmpty()) {
            val horizontalScroll = rememberScrollState()
            LazyColumn(modifier = Modifier.fillMaxSize().horizontalScroll(horizontalScroll)) {
                item(key = "header") {
                    Row(modifier = Modifier.background(IntelliJColors.surfaceContainer)) {
                        result.columns.forEach { column ->
                            ResultCell(
                                value = "${column.label}\n${column.typeName}",
                                header = true,
                            )
                        }
                    }
                }
                items(result.rows) { row ->
                    Row {
                        row.forEach { value -> ResultCell(value = value ?: "NULL", nullValue = value == null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCell(
    value: String,
    header: Boolean = false,
    nullValue: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .width(Dimensions.databaseResultCellWidth.dp)
                .height(
                    if (header) {
                        Dimensions.databaseResultHeaderHeight.dp
                    } else {
                        Dimensions.databaseResultRowHeight.dp
                    },
                ).border(Dimensions.editorGuideWidth.dp, IntelliJColors.borderSubtle)
                .padding(horizontal = Spacing.sm.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = value.replace('\n', ' '),
            color = if (nullValue) IntelliJColors.textMuted else IntelliJColors.textPrimary,
            fontSize = if (header) 11.sp else 12.sp,
            fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
            fontFamily = JetaProgFonts.codeFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DatabaseConnectionDialog(
    initialProfile: DatabaseConnectionProfile,
    onSave: (DatabaseConnectionProfile, DatabaseCredential?) -> Unit,
    onDismiss: () -> Unit,
) {
    var profile by remember(initialProfile.id) { mutableStateOf(initialProfile) }
    var password by remember(initialProfile.id) { mutableStateOf("") }
    val errors = profile.validationErrors()

    IntelliJDialog(
        onDismissRequest = onDismiss,
        minWidth = Dimensions.databaseConnectionDialogMinWidth.dp,
        maxWidth = Dimensions.databaseConnectionDialogMaxWidth.dp,
    ) {
        Column(modifier = Modifier.padding(Spacing.lg.dp)) {
            Text(
                text = if (initialProfile.database.isBlank()) "New database connection" else "Edit database connection",
                color = IntelliJColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(Spacing.md.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
                DatabaseDialect.entries.forEach { dialect ->
                    IntelliJButton(
                        text = dialect.displayName,
                        onClick = {
                            val oldDefaultPort = profile.dialect.defaultPort
                            profile =
                                profile.copy(
                                    dialect = dialect,
                                    port = if (profile.port == oldDefaultPort) dialect.defaultPort else profile.port,
                                    catalog =
                                        if (dialect ==
                                            DatabaseDialect.STARROCKS
                                        ) {
                                            profile.catalog
                                        } else {
                                            "default_catalog"
                                        },
                                )
                        },
                        style = if (profile.dialect == dialect) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY,
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.md.dp))
            IntelliJTextField(
                value = profile.name,
                onValueChange = { profile = profile.copy(name = it) },
                label = "Connection name",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
                IntelliJTextField(
                    value = profile.host,
                    onValueChange = { profile = profile.copy(host = it) },
                    label = "Host",
                    modifier = Modifier.weight(1f),
                )
                IntelliJTextField(
                    value = profile.port.toString(),
                    onValueChange = { value -> value.toIntOrNull()?.let { profile = profile.copy(port = it) } },
                    label = "Port",
                    modifier = Modifier.width(Dimensions.databasePortFieldWidth.dp),
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            if (profile.dialect == DatabaseDialect.STARROCKS) {
                IntelliJTextField(
                    value = profile.catalog,
                    onValueChange = { profile = profile.copy(catalog = it) },
                    label = "Catalog",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
            }
            IntelliJTextField(
                value = profile.database,
                onValueChange = { profile = profile.copy(database = it) },
                label = "Database",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
                IntelliJTextField(
                    value = profile.username,
                    onValueChange = { profile = profile.copy(username = it) },
                    label = "Username",
                    modifier = Modifier.weight(1f),
                )
                IntelliJTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    placeholder =
                        if (initialProfile.database.isBlank()) {
                            "Optional"
                        } else {
                            "Leave blank to keep session value"
                        },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Text(
                text = "Passwords are kept in memory for this app session and are never written to project files.",
                color = IntelliJColors.textMuted,
                fontSize = 11.sp,
            )
            if (profile.dialect == DatabaseDialect.POSTGRESQL) {
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                IntelliJButton(
                    text = if (profile.sslMode == DatabaseSslMode.REQUIRE) "TLS required" else "TLS disabled",
                    onClick = {
                        profile =
                            profile.copy(
                                sslMode =
                                    if (profile.sslMode == DatabaseSslMode.REQUIRE) {
                                        DatabaseSslMode.DISABLE
                                    } else {
                                        DatabaseSslMode.REQUIRE
                                    },
                            )
                    },
                )
            }
            if (errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                InlineStatus(errors.first(), IntelliJColors.error)
            }
            Spacer(modifier = Modifier.height(Spacing.lg.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp, Alignment.End),
            ) {
                IntelliJButton(text = "Cancel", onClick = onDismiss)
                IntelliJButton(
                    text = "Save",
                    onClick = {
                        onSave(
                            profile.copy(
                                name = profile.name.trim(),
                                host = profile.host.trim(),
                                catalog = profile.catalog.trim(),
                                database = profile.database.trim(),
                                username = profile.username.trim(),
                            ),
                            password.takeIf { it.isNotEmpty() }?.let(DatabaseCredential::fromPassword),
                        )
                    },
                    style = ButtonStyle.PRIMARY,
                    enabled = errors.isEmpty(),
                )
            }
        }
    }
}

@Composable
private fun EmptyNavigatorMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(Spacing.md.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = IntelliJColors.textMuted, fontSize = 11.sp)
    }
}

@Composable
private fun InlineStatus(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
    )
}

private sealed interface SchemaTreeNode {
    val key: String
    val label: String
    val depth: Int
    val expandable: Boolean

    data class SchemaNode(
        val schema: DatabaseSchema,
    ) : SchemaTreeNode {
        override val key: String = "schema:${schema.catalog}:${schema.name}"
        override val label: String = schema.name
        override val depth: Int = 0
        override val expandable: Boolean = schema.tables.isNotEmpty()
    }

    data class TableNode(
        val schemaKey: String,
        val table: DatabaseTable,
    ) : SchemaTreeNode {
        override val key: String = "$schemaKey/table:${table.name}"
        override val label: String = "${table.name}  ${table.type.lowercase()}"
        override val depth: Int = 1
        override val expandable: Boolean = table.columns.isNotEmpty()
    }

    data class ColumnNode(
        val tableKey: String,
        val column: DatabaseColumn,
    ) : SchemaTreeNode {
        override val key: String = "$tableKey/column:${column.name}"
        override val label: String = "${column.name}: ${column.typeName}${if (column.nullable) "?" else ""}"
        override val depth: Int = 2
        override val expandable: Boolean = false
    }
}

private fun flattenSchemaTree(
    schemas: List<DatabaseSchema>,
    expanded: Set<String>,
): List<SchemaTreeNode> =
    buildList {
        schemas.forEach { schema ->
            val schemaNode = SchemaTreeNode.SchemaNode(schema)
            add(schemaNode)
            if (schemaNode.key in expanded) {
                schema.tables.forEach { table ->
                    val tableNode = SchemaTreeNode.TableNode(schemaNode.key, table)
                    add(tableNode)
                    if (tableNode.key in expanded) {
                        table.columns.forEach { column -> add(SchemaTreeNode.ColumnNode(tableNode.key, column)) }
                    }
                }
            }
        }
    }
