package dk.hjertmann.jwasm_tree_sitter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single captured node from a Tree-sitter query match.
 *
 * <p>Line numbers are 1-based (consistent with {@link WasmSymbol}); column numbers are
 * 0-based (Tree-sitter convention).
 */
public record QueryCapture(
    /** Capture name without the leading {@code @}, dots preserved — e.g. {@code "class.name"}. */
    String name,

    /** Source text of the matched node. */
    String text,

    /** 1-based start line. */
    int startLine,

    /** 0-based start column. */
    int startColumn,

    /** 1-based end line. */
    int endLine,

    /** 0-based end column. */
    int endColumn
) {
    @JsonCreator
    public QueryCapture(
        @JsonProperty("name")         String name,
        @JsonProperty("text")         String text,
        @JsonProperty("start_line")   int startLine,
        @JsonProperty("start_column") int startColumn,
        @JsonProperty("end_line")     int endLine,
        @JsonProperty("end_column")   int endColumn
    ) {
        this.name        = name        != null ? name : "";
        this.text        = text        != null ? text : "";
        this.startLine   = startLine;
        this.startColumn = startColumn;
        this.endLine     = endLine;
        this.endColumn   = endColumn;
    }
}
