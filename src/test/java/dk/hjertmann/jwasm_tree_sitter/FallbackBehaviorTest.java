package dk.hjertmann.jwasm_tree_sitter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FallbackBehaviorTest {

    private static TreeSitterAnalyzer analyzer;

    @BeforeAll
    static void setUp() {
        analyzer = TreeSitterAnalyzer.create();
    }

    @AfterAll
    static void tearDown() {
        if (analyzer != null) analyzer.close();
    }

    @Test
    void unsupportedLanguageReturnsFallback() {
        ParsedFileResult result = analyzer.parse("SELECT 1", "cobol");
        assertNotNull(result, "Result should not be null");
        // Java wraps unsupported language as fallback — succeeds with UNKNOWN symbol
        assertFalse(result.symbols().isEmpty(), "Should have fallback UNKNOWN symbol");
        assertEquals("cobol", result.language());
    }

    @Test
    void emptySourceReturnsSuccess() {
        ParsedFileResult result = analyzer.parse("", "java");
        assertNotNull(result, "Result should not be null for empty source");
        assertTrue(result.success(), "Should succeed for empty source");
        assertTrue(result.symbols().isEmpty(), "Should have no symbols for empty source");
        assertNull(result.errorMessage(), "No error for empty source");
    }

    @Test
    void blankSourceReturnsSuccess() {
        ParsedFileResult result = analyzer.parse("   \n\t  ", "python");
        assertNotNull(result, "Result should not be null for blank source");
        assertTrue(result.success(), "Should succeed for blank source");
    }

    @Test
    void nullSourceNeverThrows() {
        assertDoesNotThrow(() -> analyzer.parse(null, "java"),
            "Should not throw for null source");
    }

    @Test
    void nullLanguageNeverThrows() {
        assertDoesNotThrow(() -> analyzer.parse("class Foo {}", null),
            "Should not throw for null language");
    }

    @Test
    void htmlReturnsWholeFileFallback() {
        String html = "<html><body><h1>Hello</h1></body></html>";
        ParsedFileResult result = analyzer.parse(html, "html");
        assertNotNull(result);
        assertTrue(result.success());
        assertFalse(result.symbols().isEmpty(), "HTML should return at least one UNKNOWN symbol");
        assertEquals(WasmSymbolType.UNKNOWN, result.symbols().getFirst().type());
    }

    @Test
    void jsonReturnsWholeFileFallback() {
        String json = "{\"key\": \"value\"}";
        ParsedFileResult result = analyzer.parse(json, "json");
        assertNotNull(result);
        assertTrue(result.success());
        assertFalse(result.symbols().isEmpty(), "JSON should return at least one UNKNOWN symbol");
    }

    @Test
    void tomlReturnsWholeFileFallback() {
        String toml = "[section]\nkey = \"value\"";
        ParsedFileResult result = analyzer.parse(toml, "toml");
        assertNotNull(result);
        assertTrue(result.success());
        assertFalse(result.symbols().isEmpty(), "TOML should return at least one UNKNOWN symbol");
    }

    @Test
    void kotlinReturnsWholeFileFallback() {
        String kotlin = "data class Foo(val x: Int)";
        ParsedFileResult result = analyzer.parse(kotlin, "kotlin");
        assertNotNull(result);
        assertTrue(result.success());
        assertFalse(result.symbols().isEmpty(), "Kotlin should return at least one UNKNOWN symbol");
    }

    @Test
    void detectLanguageFromExtension() {
        assertEquals("java",       analyzer.detectLanguage(java.nio.file.Path.of("Foo.java")));
        assertEquals("python",     analyzer.detectLanguage(java.nio.file.Path.of("script.py")));
        assertEquals("typescript", analyzer.detectLanguage(java.nio.file.Path.of("app.ts")));
        assertEquals("go",         analyzer.detectLanguage(java.nio.file.Path.of("main.go")));
        assertEquals("unknown",    analyzer.detectLanguage(java.nio.file.Path.of("readme.xyz")));
    }

    @Test
    void multipleCallsAreSafe() {
        for (int i = 0; i < 5; i++) {
            ParsedFileResult r = analyzer.parse("class Test {}", "java");
            assertNotNull(r);
            assertTrue(r.success());
        }
    }
}
