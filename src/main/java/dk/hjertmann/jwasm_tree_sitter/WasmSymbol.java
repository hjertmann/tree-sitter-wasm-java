package dk.hjertmann.jwasm_tree_sitter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record WasmSymbol(
    String name,
    WasmSymbolType type,
    String signature,
    String content,
    int startLine,
    int endLine
) {
    @JsonCreator
    public WasmSymbol(
        @JsonProperty("name") String name,
        @JsonProperty("type") WasmSymbolType type,
        @JsonProperty("signature") String signature,
        @JsonProperty("content") String content,
        @JsonProperty("start_line") int startLine,
        @JsonProperty("end_line") int endLine
    ) {
        this.name = name;
        this.type = type != null ? type : WasmSymbolType.UNKNOWN;
        this.signature = signature != null ? signature : "";
        this.content = content != null ? content : "";
        this.startLine = startLine;
        this.endLine = endLine;
    }
}
