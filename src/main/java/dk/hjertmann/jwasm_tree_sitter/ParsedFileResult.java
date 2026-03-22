package dk.hjertmann.jwasm_tree_sitter;

import java.util.List;

public record ParsedFileResult(
    boolean success,
    String language,
    List<WasmSymbol> symbols,
    List<String> imports,
    String packageName,
    String primaryTypeName,
    WasmSymbolType primaryType,
    String errorMessage
) {
    /**
     * Fallback: wraps entire file content as one UNKNOWN symbol.
     */
    public static ParsedFileResult fallback(String language, String content, String fileName) {
        String name = fileName != null ? fileName : "file";
        var symbol = new WasmSymbol(name, WasmSymbolType.UNKNOWN, "", content != null ? content : "", 1,
            content != null ? (int) content.lines().count() : 1);
        return new ParsedFileResult(
            true, language, List.of(symbol), List.of(), null, null, null, null
        );
    }
}
