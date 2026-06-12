package su.kidoz.jetaprog.editor.treesitter

/**
 * Represents a node in the Tree-sitter syntax tree.
 *
 * This is an abstraction over the native Tree-sitter node type,
 * allowing platform-independent code to work with syntax trees.
 */
public interface TreeSitterNode {
    /**
     * The type of this node (e.g., "function_declaration", "identifier").
     */
    public val type: String

    /**
     * The start byte offset of this node in the source text.
     */
    public val startByte: Int

    /**
     * The end byte offset of this node in the source text.
     */
    public val endByte: Int

    /**
     * The start position (row and column).
     */
    public val startPoint: TreeSitterPoint

    /**
     * The end position (row and column).
     */
    public val endPoint: TreeSitterPoint

    /**
     * Whether this node is named (as opposed to anonymous/literal).
     */
    public val isNamed: Boolean

    /**
     * The number of child nodes.
     */
    public val childCount: Int

    /**
     * Get a child node by index.
     */
    public fun child(index: Int): TreeSitterNode?

    /**
     * Get all named children.
     */
    public fun namedChildren(): List<TreeSitterNode>

    /**
     * Get a child by field name (e.g., "name" in a function declaration).
     */
    public fun childByFieldName(fieldName: String): TreeSitterNode?

    /**
     * The text content of this node.
     */
    public val text: String
}

/**
 * A point (row, column) in the source text.
 */
public data class TreeSitterPoint(
    val row: Int,
    val column: Int,
)

/**
 * Represents a Tree-sitter syntax tree.
 */
public interface TreeSitterTree {
    /**
     * The root node of the syntax tree.
     */
    public val rootNode: TreeSitterNode

    /**
     * Edit the tree to reflect changes in the source text.
     */
    public fun edit(edit: TreeSitterEdit)

    /**
     * Release resources associated with this tree.
     */
    public fun close()
}

/**
 * Describes an edit to the source text for incremental parsing.
 */
public data class TreeSitterEdit(
    val startByte: Int,
    val oldEndByte: Int,
    val newEndByte: Int,
    val startPoint: TreeSitterPoint,
    val oldEndPoint: TreeSitterPoint,
    val newEndPoint: TreeSitterPoint,
)
