package dk.hjertmann.jwasm_tree_sitter;

import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analyzes source code using Tree-sitter grammars compiled to WebAssembly.
 *
 * <p>This is expensive to construct — create once and reuse. It is thread-safe
 * after construction (parse calls are synchronized).
 */
public final class TreeSitterAnalyzer implements AutoCloseable {

    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
        Map.entry(".java",   "java"),
        Map.entry(".py",     "python"),
        Map.entry(".js",     "javascript"),
        Map.entry(".mjs",    "javascript"),
        Map.entry(".cjs",    "javascript"),
        Map.entry(".ts",     "typescript"),
        Map.entry(".tsx",    "tsx"),
        Map.entry(".go",     "go"),
        Map.entry(".rs",     "rust"),
        Map.entry(".c",      "c"),
        Map.entry(".h",      "c"),
        Map.entry(".cpp",    "cpp"),
        Map.entry(".cc",     "cpp"),
        Map.entry(".cxx",    "cpp"),
        Map.entry(".hpp",    "cpp"),
        Map.entry(".cs",     "csharp"),
        Map.entry(".kt",     "kotlin"),
        Map.entry(".kts",    "kotlin"),
        Map.entry(".rb",     "ruby"),
        Map.entry(".swift",  "swift"),
        Map.entry(".scala",  "scala"),
        Map.entry(".php",    "php"),
        Map.entry(".sh",     "bash"),
        Map.entry(".bash",   "bash"),
        Map.entry(".md",     "markdown"),
        Map.entry(".html",   "html"),
        Map.entry(".htm",    "html"),
        Map.entry(".css",    "css"),
        Map.entry(".scss",   "css"),
        Map.entry(".json",   "json"),
        Map.entry(".yml",    "yaml"),
        Map.entry(".yaml",   "yaml"),
        Map.entry(".toml",   "toml"),
        Map.entry(".sql",    "sql")
    );

    private final Instance instance;
    private final WasiPreview1 wasi;
    private final ObjectMapper mapper;

    private TreeSitterAnalyzer(Instance instance, WasiPreview1 wasi) {
        this.instance = instance;
        this.wasi = wasi;
        this.mapper = new ObjectMapper();
    }

    /**
     * Create a new analyzer, loading the bundled WASM from the classpath.
     * Calls warmup() to initialize tree-sitter internals. This is expensive — create once.
     */
    public static TreeSitterAnalyzer create() {
        byte[] wasmBytes;
        try (InputStream is = TreeSitterAnalyzer.class.getResourceAsStream("/wasm/tree-sitter-analysis.wasm")) {
            if (is == null) {
                throw new IllegalStateException("WASM resource not found: /wasm/tree-sitter-analysis.wasm");
            }
            wasmBytes = is.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load WASM resource", e);
        }

        var wasiOpts = WasiOptions.builder().inheritSystem().build();
        var wasi = WasiPreview1.builder().withOptions(wasiOpts).build();
        var imports = ImportValues.builder().addFunction(wasi.toHostFunctions()).build();

        var wasmModule = com.dylibso.chicory.wasm.Parser.parse(wasmBytes);
        var instance = Instance.builder(wasmModule).withImportValues(imports).build();

        var analyzer = new TreeSitterAnalyzer(instance, wasi);
        analyzer.warmup();
        return analyzer;
    }

    /** Detect language from file extension. Returns "unknown" if not recognized. */
    public String detectLanguage(Path filePath) {
        String name = filePath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "unknown";
        String ext = name.substring(dot).toLowerCase();
        return EXTENSION_TO_LANGUAGE.getOrDefault(ext, "unknown");
    }

    /** Returns true if this analyzer supports the given language name. */
    public boolean supportsLanguage(String language) {
        return EXTENSION_TO_LANGUAGE.containsValue(language);
    }

    /** Returns all supported language names. */
    public Set<String> supportedLanguages() {
        return Set.copyOf(EXTENSION_TO_LANGUAGE.values());
    }

    /**
     * Parse source code for the given language.
     * On unsupported language or error: returns a fallback result (never throws).
     */
    public ParsedFileResult parse(String sourceCode, String language) {
        if (sourceCode == null) sourceCode = "";
        if (language == null) language = "unknown";

        try {
            String json = callAnalyze(sourceCode, language);
            return deserializeResult(json, sourceCode, language);
        } catch (Exception e) {
            return ParsedFileResult.fallback(language, sourceCode, language);
        }
    }

    /** Convenience: read file, detect language from extension, then parse. */
    public ParsedFileResult parseFile(Path filePath) throws IOException {
        String language = detectLanguage(filePath);
        String source = java.nio.file.Files.readString(filePath, StandardCharsets.UTF_8);
        return parse(source, language);
    }

    // ─── Raw Query API ────────────────────────────────────────────────────────

    /**
     * Run an arbitrary Tree-sitter S-expression query against source code and return
     * the matches, each containing the captures that fired together.
     *
     * <p>Use this when you need to know which captures belong to the same match —
     * for example, a query with both {@code @name} and {@code @body} captures.
     *
     * <p>Example query for Java public classes:
     * <pre>{@code
     * (class_declaration
     *   name: (identifier) @class.name) @class.body
     * }</pre>
     *
     * @param source   source code to analyze
     * @param language language name (e.g. {@code "java"}, {@code "python"})
     * @param query    Tree-sitter S-expression query string
     * @return list of matches, empty if no matches found
     * @throws TreeSitterQueryException if the query is invalid or the language is unsupported
     */
    public List<QueryMatch> queryMatches(String source, String language, String query) {
        if (source == null) source = "";
        if (language == null || language.isBlank()) {
            throw new TreeSitterQueryException("Language must not be null or blank", language, query);
        }
        if (query == null || query.isBlank()) {
            throw new TreeSitterQueryException("Query must not be null or blank", language, query);
        }
        try {
            String json = callQuerySource(source, language, query);
            return deserializeQueryResult(json, language, query);
        } catch (TreeSitterQueryException e) {
            throw e;
        } catch (Exception e) {
            throw new TreeSitterQueryException(e.getMessage(), language, query, e);
        }
    }

    /**
     * Run an arbitrary Tree-sitter S-expression query against source code and return
     * a flat list of all captured nodes across all matches.
     *
     * <p>Use this when each capture is independent — for example, a query that
     * captures all import paths or all method names.
     *
     * <p>Example query for Java imports:
     * <pre>{@code
     * (import_declaration (scoped_identifier) @import.path)
     * }</pre>
     *
     * @param source   source code to analyze
     * @param language language name (e.g. {@code "java"}, {@code "python"})
     * @param query    Tree-sitter S-expression query string
     * @return flat list of all captures, empty if no matches found
     * @throws TreeSitterQueryException if the query is invalid or the language is unsupported
     */
    public List<QueryCapture> queryCaptures(String source, String language, String query) {
        return queryMatches(source, language, query).stream()
            .flatMap(m -> m.captures().stream())
            .toList();
    }

    private String callQuerySource(String source, String language, String query) {
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        byte[] langBytes   = language.getBytes(StandardCharsets.UTF_8);
        byte[] queryBytes  = query.getBytes(StandardCharsets.UTF_8);

        synchronized (instance) {
            var memory = instance.memory();

            long srcPtrRaw   = instance.export("wasm_malloc").apply(sourceBytes.length)[0];
            int  srcPtr      = (int) srcPtrRaw;
            memory.write(srcPtr, sourceBytes);

            long langPtrRaw  = instance.export("wasm_malloc").apply(langBytes.length)[0];
            int  langPtr     = (int) langPtrRaw;
            memory.write(langPtr, langBytes);

            long queryPtrRaw = instance.export("wasm_malloc").apply(queryBytes.length)[0];
            int  queryPtr    = (int) queryPtrRaw;
            memory.write(queryPtr, queryBytes);

            long packed = instance.export("query_source").apply(
                srcPtr,   sourceBytes.length,
                langPtr,  langBytes.length,
                queryPtr, queryBytes.length
            )[0];

            instance.export("wasm_free").apply(srcPtr,   sourceBytes.length);
            instance.export("wasm_free").apply(langPtr,  langBytes.length);
            instance.export("wasm_free").apply(queryPtr, queryBytes.length);

            int resultPtr = (int) ((packed >>> 32) & 0xFFFFFFFFL);
            int resultLen = (int) (packed & 0xFFFFFFFFL);
            byte[] resultBytes = memory.readBytes(resultPtr, resultLen);
            instance.export("wasm_free").apply(resultPtr, resultLen);

            return new String(resultBytes, StandardCharsets.UTF_8);
        }
    }

    private List<QueryMatch> deserializeQueryResult(String json, String language, String query) {
        try {
            var node = mapper.readTree(json);
            boolean success = node.path("success").asBoolean(false);
            if (!success) {
                String error = node.path("error").asText("Query failed");
                throw new TreeSitterQueryException(error, language, query);
            }
            var matchesNode = node.path("matches");
            if (matchesNode.isMissingNode()) return List.of();
            return List.of(mapper.treeToValue(matchesNode, QueryMatch[].class));
        } catch (TreeSitterQueryException e) {
            throw e;
        } catch (Exception e) {
            throw new TreeSitterQueryException("Failed to deserialize query result: " + e.getMessage(), language, query, e);
        }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private void warmup() {
        synchronized (instance) {
            instance.export("warmup").apply();
        }
    }

    private String callAnalyze(String sourceCode, String language) {
        byte[] sourceBytes = sourceCode.getBytes(StandardCharsets.UTF_8);
        byte[] langBytes = language.getBytes(StandardCharsets.UTF_8);

        synchronized (instance) {
            var memory = instance.memory();

            // Allocate source buffer
            long srcPtrRaw = instance.export("wasm_malloc").apply(sourceBytes.length)[0];
            int srcPtr = (int) srcPtrRaw;
            memory.write(srcPtr, sourceBytes);

            // Allocate language buffer
            long langPtrRaw = instance.export("wasm_malloc").apply(langBytes.length)[0];
            int langPtr = (int) langPtrRaw;
            memory.write(langPtr, langBytes);

            // Call analyze — returns packed i64: upper 32 = result ptr, lower 32 = result len
            long packed = instance.export("analyze").apply(
                srcPtr, sourceBytes.length,
                langPtr, langBytes.length
            )[0];

            // Free input buffers
            instance.export("wasm_free").apply(srcPtr, sourceBytes.length);
            instance.export("wasm_free").apply(langPtr, langBytes.length);

            // Unpack result pointer and length
            int resultPtr = (int) ((packed >>> 32) & 0xFFFFFFFFL);
            int resultLen = (int) (packed & 0xFFFFFFFFL);

            // Read result bytes
            byte[] resultBytes = memory.readBytes(resultPtr, resultLen);

            // Free result buffer
            instance.export("wasm_free").apply(resultPtr, resultLen);

            return new String(resultBytes, StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private ParsedFileResult deserializeResult(String json, String sourceCode, String language) {
        try {
            var node = mapper.readTree(json);

            boolean success = node.path("success").asBoolean(false);
            String lang = node.path("language").asText(language);
            String error = node.path("error").asText(null);

            if (!success) {
                return ParsedFileResult.fallback(lang, sourceCode, language);
            }

            var symbolsNode = node.path("symbols");
            List<WasmSymbol> symbols = symbolsNode.isMissingNode()
                ? List.of()
                : List.of(mapper.treeToValue(symbolsNode, WasmSymbol[].class));

            var importsNode = node.path("imports");
            List<String> imports = importsNode.isMissingNode()
                ? List.of()
                : List.of(mapper.treeToValue(importsNode, String[].class));

            String packageName = node.path("package_name").asText(null);
            if (packageName != null && packageName.isEmpty()) packageName = null;

            String primaryTypeName = node.path("primary_type_name").asText(null);
            if (primaryTypeName != null && primaryTypeName.isEmpty()) primaryTypeName = null;

            WasmSymbolType primaryType = null;
            var ptNode = node.path("primary_type");
            if (!ptNode.isMissingNode() && !ptNode.isNull()) {
                try {
                    primaryType = WasmSymbolType.valueOf(ptNode.asText().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                }
            }

            return new ParsedFileResult(success, lang, symbols, imports,
                packageName, primaryTypeName, primaryType, error);

        } catch (Exception e) {
            return ParsedFileResult.fallback(language, sourceCode, language);
        }
    }

    @Override
    public void close() {
        if (wasi != null) {
            wasi.close();
        }
    }
}
