package dk.hjertmann.jwasm_tree_sitter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A single match from a Tree-sitter query — one firing of a query pattern.
 *
 * <p>A query with multiple captures per pattern (e.g. {@code @name} and {@code @body})
 * groups them here so callers can pair them without correlating by position.
 */
public record QueryMatch(
    /** Sequential 0-based index of this match across all matches returned. */
    int matchIndex,

    /** All captures that fired in this match, in source order. */
    List<QueryCapture> captures
) {
    @JsonCreator
    public QueryMatch(
        @JsonProperty("match_index") int matchIndex,
        @JsonProperty("captures")    List<QueryCapture> captures
    ) {
        this.matchIndex = matchIndex;
        this.captures   = captures != null ? captures : List.of();
    }
}
