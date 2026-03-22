package dk.hjertmann.jwasm_tree_sitter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryApiTest {

    private static TreeSitterAnalyzer analyzer;
    private static String javaSample;

    @BeforeAll
    static void setUp() throws IOException {
        analyzer = TreeSitterAnalyzer.create();
        javaSample = loadFixture("Sample.java");
    }

    @AfterAll
    static void tearDown() {
        if (analyzer != null) analyzer.close();
    }

    // ─── queryMatches ──────────────────────────────────────────────────────────

    @Test
    void queryMatchesFindsClasses() {
        List<QueryMatch> matches = analyzer.queryMatches(
            javaSample, "java",
            "(class_declaration name: (identifier) @class.name) @class.body"
        );

        assertFalse(matches.isEmpty(), "Should find class matches");

        // Each match should have two captures: @class.name and @class.body
        QueryMatch first = matches.get(0);
        assertEquals(2, first.captures().size(), "Match should have two captures");

        QueryCapture nameCapture = first.captures().stream()
            .filter(c -> c.name().equals("class.name"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No @class.name capture in match"));
        assertEquals("UserService", nameCapture.text());
    }

    @Test
    void queryMatchesGroupsCapturesTogether() {
        List<QueryMatch> matches = analyzer.queryMatches(
            javaSample, "java",
            "(method_declaration name: (identifier) @method.name) @method.body"
        );

        assertFalse(matches.isEmpty(), "Should find method matches");

        // Every match must have both captures
        for (QueryMatch m : matches) {
            boolean hasName = m.captures().stream().anyMatch(c -> c.name().equals("method.name"));
            boolean hasBody = m.captures().stream().anyMatch(c -> c.name().equals("method.body"));
            assertTrue(hasName, "Each match should have @method.name");
            assertTrue(hasBody, "Each match should have @method.body");
        }

        List<String> methodNames = matches.stream()
            .flatMap(m -> m.captures().stream())
            .filter(c -> c.name().equals("method.name"))
            .map(QueryCapture::text)
            .toList();
        assertTrue(methodNames.contains("findById"));
        assertTrue(methodNames.contains("save"));
    }

    @Test
    void queryMatchesIndexIsSequential() {
        List<QueryMatch> matches = analyzer.queryMatches(
            javaSample, "java",
            "(method_declaration name: (identifier) @name)"
        );
        for (int i = 0; i < matches.size(); i++) {
            assertEquals(i, matches.get(i).matchIndex(), "Match index should be sequential");
        }
    }

    @Test
    void queryMatchesReturnsEmptyForNoMatches() {
        List<QueryMatch> matches = analyzer.queryMatches(
            javaSample, "java",
            "(record_declaration name: (identifier) @name)"  // no records in Sample.java
        );
        assertTrue(matches.isEmpty());
    }

    // ─── queryCaptures ─────────────────────────────────────────────────────────

    @Test
    void queryCapturesReturnsFlatList() {
        List<QueryCapture> captures = analyzer.queryCaptures(
            javaSample, "java",
            "(import_declaration (scoped_identifier) @import.path)"
        );

        assertFalse(captures.isEmpty(), "Should find import captures");
        List<String> texts = captures.stream().map(QueryCapture::text).toList();
        assertTrue(texts.contains("java.util.List"));
        assertTrue(texts.contains("java.util.Optional"));
        assertTrue(texts.contains("org.springframework.stereotype.Service"));
    }

    @Test
    void queryCaptureNamesPreserveDots() {
        List<QueryCapture> captures = analyzer.queryCaptures(
            javaSample, "java",
            "(class_declaration name: (identifier) @class.name)"
        );
        assertTrue(captures.stream().allMatch(c -> c.name().equals("class.name")),
            "Capture name should preserve the dot in 'class.name'");
    }

    @Test
    void queryCapturesHasCorrectLineNumbers() {
        List<QueryCapture> captures = analyzer.queryCaptures(
            javaSample, "java",
            "(class_declaration name: (identifier) @name)"
        );
        assertFalse(captures.isEmpty());
        for (QueryCapture c : captures) {
            assertTrue(c.startLine() >= 1, "start_line should be >= 1");
            assertTrue(c.endLine() >= c.startLine(), "end_line should be >= start_line");
            assertTrue(c.startColumn() >= 0, "start_column should be >= 0");
        }
    }

    // ─── Error handling ────────────────────────────────────────────────────────

    @Test
    void invalidQueryThrowsException() {
        assertThrows(TreeSitterQueryException.class, () ->
            analyzer.queryMatches(javaSample, "java", "(this_node_does_not_exist @bad)")
        );
    }

    @Test
    void unsupportedLanguageThrowsException() {
        TreeSitterQueryException ex = assertThrows(TreeSitterQueryException.class, () ->
            analyzer.queryMatches(javaSample, "cobol", "(anything @cap)")
        );
        assertEquals("cobol", ex.getLanguage());
    }

    @Test
    void blankQueryThrowsException() {
        assertThrows(TreeSitterQueryException.class, () ->
            analyzer.queryMatches(javaSample, "java", "   ")
        );
    }

    @Test
    void emptySourceReturnsNoMatches() {
        List<QueryMatch> matches = analyzer.queryMatches(
            "", "java",
            "(class_declaration name: (identifier) @name)"
        );
        assertTrue(matches.isEmpty());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private static String loadFixture(String name) throws IOException {
        try (InputStream is = QueryApiTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(is, "Fixture not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
