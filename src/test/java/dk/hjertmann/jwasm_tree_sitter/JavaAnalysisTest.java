package dk.hjertmann.jwasm_tree_sitter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaAnalysisTest {

    private static TreeSitterAnalyzer analyzer;
    private static ParsedFileResult result;

    @BeforeAll
    static void setUp() throws IOException {
        analyzer = TreeSitterAnalyzer.create();
        String source = loadFixture("Sample.java");
        result = analyzer.parse(source, "java");
    }

    @AfterAll
    static void tearDown() {
        if (analyzer != null) analyzer.close();
    }

    @Test
    void parseSucceeds() {
        assertTrue(result.success(), "Parse should succeed for valid Java");
        assertNull(result.errorMessage(), "No error message expected");
        assertEquals("java", result.language());
    }

    @Test
    void containsClassSymbol() {
        List<WasmSymbol> classes = result.symbols().stream()
            .filter(s -> s.type() == WasmSymbolType.CLASS)
            .toList();
        assertFalse(classes.isEmpty(), "Should find at least one CLASS symbol");

        WasmSymbol mainClass = classes.stream()
            .filter(s -> s.name().equals("UserService"))
            .findFirst()
            .orElse(null);
        assertNotNull(mainClass, "Should find UserService class");
    }

    @Test
    void containsMethodSymbols() {
        List<WasmSymbol> methods = result.symbols().stream()
            .filter(s -> s.type() == WasmSymbolType.METHOD)
            .toList();
        assertFalse(methods.isEmpty(), "Should find at least one METHOD symbol");

        List<String> methodNames = methods.stream().map(WasmSymbol::name).toList();
        assertTrue(methodNames.contains("findById"), "Should find method 'findById'");
        assertTrue(methodNames.contains("findAllActive"), "Should find method 'findAllActive'");
        assertTrue(methodNames.contains("save"), "Should find method 'save'");
        assertTrue(methodNames.contains("delete"), "Should find method 'delete'");
    }

    @Test
    void containsConstructorSymbol() {
        List<WasmSymbol> constructors = result.symbols().stream()
            .filter(s -> s.type() == WasmSymbolType.CONSTRUCTOR)
            .toList();
        assertFalse(constructors.isEmpty(), "Should find at least one CONSTRUCTOR symbol");
        assertTrue(constructors.stream().anyMatch(s -> s.name().equals("UserService")),
            "Should find constructor 'UserService'");
    }

    @Test
    void containsEnumSymbol() {
        List<WasmSymbol> enums = result.symbols().stream()
            .filter(s -> s.type() == WasmSymbolType.ENUM)
            .toList();
        assertFalse(enums.isEmpty(), "Should find at least one ENUM symbol");
        assertTrue(enums.stream().anyMatch(s -> s.name().equals("Status")),
            "Should find enum 'Status'");
    }

    @Test
    void importsAreExtracted() {
        assertFalse(result.imports().isEmpty(), "Imports should not be empty");
        assertTrue(result.imports().contains("java.util.List"),
            "Should find exact import 'java.util.List'");
        assertTrue(result.imports().contains("java.util.Optional"),
            "Should find exact import 'java.util.Optional'");
        assertTrue(result.imports().contains("org.springframework.stereotype.Service"),
            "Should find exact import 'org.springframework.stereotype.Service'");
    }

    @Test
    void packageNameIsCorrect() {
        assertEquals("com.example.service", result.packageName(),
            "Package name should match");
    }

    @Test
    void primaryTypeIsDetected() {
        assertEquals("UserService", result.primaryTypeName(),
            "Primary type name should be UserService");
        assertEquals(WasmSymbolType.CLASS, result.primaryType(),
            "Primary type should be CLASS");
    }

    @Test
    void symbolsHaveValidLineNumbers() {
        for (WasmSymbol sym : result.symbols()) {
            assertTrue(sym.startLine() >= 1, "Start line should be >= 1 for " + sym.name());
            assertTrue(sym.endLine() >= sym.startLine(),
                "End line should be >= start line for " + sym.name());
        }
    }

    @Test
    void symbolsHaveNames() {
        for (WasmSymbol sym : result.symbols()) {
            assertNotNull(sym.name(), "Symbol name should not be null");
            assertFalse(sym.name().isBlank(), "Symbol name should not be blank");
        }
    }

    private static String loadFixture(String name) throws IOException {
        try (InputStream is = JavaAnalysisTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(is, "Fixture not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
