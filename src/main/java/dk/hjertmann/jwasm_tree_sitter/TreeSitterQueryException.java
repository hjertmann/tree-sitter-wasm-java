package dk.hjertmann.jwasm_tree_sitter;

/**
 * Thrown when a Tree-sitter query fails — either because the query string is
 * syntactically invalid for the given language's grammar, or because the language
 * is not supported by this library.
 */
public final class TreeSitterQueryException extends RuntimeException {

    private final String language;
    private final String query;

    public TreeSitterQueryException(String message, String language, String query) {
        super(message);
        this.language = language;
        this.query    = query;
    }

    public TreeSitterQueryException(String message, String language, String query, Throwable cause) {
        super(message, cause);
        this.language = language;
        this.query    = query;
    }

    /** The language name that was passed to the query call. */
    public String getLanguage() { return language; }

    /** The query string that was passed to the query call. */
    public String getQuery() { return query; }
}
