# tree-sitter-wasm-java

Multi-language code analysis for the JVM — symbol extraction, import detection, and
package/module declarations for 20+ languages — powered by
[Tree-sitter](https://tree-sitter.github.io/tree-sitter/) grammars compiled to WebAssembly
and executed by [Chicory](https://github.com/dylibso/chicory), a pure-Java WASM runtime.

**Zero native dependencies.** No `.so`, no `.dylib`, no JNI, no `--enable-native-access` flag.
Just a JAR (~3 MB), usable on any standard JVM.

---

## Supported languages

Java · Python · JavaScript · TypeScript · TSX · Go · Rust · C · C++ · C# ·
Kotlin · Ruby · Swift · Scala · PHP · Bash · Markdown · HTML · CSS · JSON · YAML · TOML · SQL

---

## Requirements

- Java 21 or later

---

## Installation

The library is not yet published to Maven Central. Install it to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Then add it as a dependency:

**Gradle (Kotlin DSL)**
```kotlin
implementation("dk.hjertmann:jtreesitter:0.1.0-SNAPSHOT")
```

**Gradle (Groovy DSL)**
```groovy
implementation 'dk.hjertmann:jtreesitter:0.1.0-SNAPSHOT'
```

**Maven**
```xml
<dependency>
    <groupId>dk.hjertmann</groupId>
    <artifactId>jtreesitter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

---

## Usage

### Basic parsing

```java
import dk.hjertmann.jwasm_tree_sitter.*;

// Create once — initialization loads and JIT-compiles the WASM binary (~100ms cold start).
// Reuse across your application (e.g. as a Spring @Bean singleton).
try (var analyzer = TreeSitterAnalyzer.create()) {

    ParsedFileResult result = analyzer.parse(sourceCode, "java");

    System.out.println(result.packageName());      // "com.example.service"
    System.out.println(result.primaryTypeName());  // "UserService"
    System.out.println(result.imports());          // ["java.util.List", ...]

    for (WasmSymbol symbol : result.symbols()) {
        System.out.printf("%s %s (%d–%d)%n",
            symbol.type(), symbol.name(), symbol.startLine(), symbol.endLine());
    }
}
```

### Parse a file directly

```java
try (var analyzer = TreeSitterAnalyzer.create()) {
    // Language is detected automatically from the file extension.
    ParsedFileResult result = analyzer.parseFile(Path.of("src/main/java/MyService.java"));
}
```

### Check language support

```java
analyzer.supportsLanguage("kotlin");   // true
analyzer.detectLanguage(Path.of("main.go")); // "go"
analyzer.supportedLanguages();         // Set of all supported language names
```

### ParsedFileResult fields

| Field | Description |
|---|---|
| `success` | `true` if parsing succeeded |
| `language` | Language name used for parsing |
| `symbols` | List of extracted symbols (classes, methods, functions, etc.) |
| `imports` | Fully-qualified import strings (empty list if not applicable) |
| `packageName` | Package/namespace declaration, or `null` |
| `primaryTypeName` | Name of the main type in the file, or `null` |
| `primaryType` | `WasmSymbolType` of the main type, or `null` |
| `errorMessage` | Error description, or `null` on success |

### WasmSymbol fields

| Field | Description |
|---|---|
| `name` | Symbol name |
| `type` | `CLASS`, `INTERFACE`, `ENUM`, `RECORD`, `METHOD`, `CONSTRUCTOR`, `FUNCTION`, `FIELD`, `VARIABLE`, `ANNOTATION`, or `UNKNOWN` |
| `signature` | Declaration signature (e.g. `public Result process(Request req)`) |
| `content` | Full body text of the symbol |
| `startLine` | 1-based start line |
| `endLine` | 1-based end line |

### Thread safety

`parse()` and `parseFile()` are thread-safe after construction. The analyzer can be shared
across virtual threads without additional synchronization.

---

## Tree-sitter queries

For cases where the built-in symbol extraction isn't enough, you can run arbitrary
[Tree-sitter S-expression queries](https://tree-sitter.github.io/tree-sitter/using-parsers/queries)
directly against source code.

### queryCaptures — flat list of matched nodes

Use this when each capture is independent (e.g. collect all class names or all imports):

```java
try (var analyzer = TreeSitterAnalyzer.create()) {  // import dk.hjertmann.jwasm_tree_sitter.*;

    String source = """
        package com.example;
        import java.util.List;
        public class UserService {
            public void findAll() {}
        }
        """;

    // Find all public class names
    List<QueryCapture> classes = analyzer.queryCaptures(source, "java",
        "(class_declaration name: (identifier) @class.name)");

    for (QueryCapture c : classes) {
        System.out.printf("%-20s  line %d, col %d%n",
            c.name() + "=" + c.text(), c.startLine(), c.startColumn());
        // class.name=UserService  line 3, col 13
    }

    // Find all imports
    List<QueryCapture> imports = analyzer.queryCaptures(source, "java",
        "(import_declaration (scoped_identifier) @import.path)");
    // → "java.util.List"
}
```

### queryMatches — captures grouped per match

Use this when captures in a single match must be paired together (e.g. a method name
with its return type, or a class name with its body):

```java
// Each match contains both @method.name and @method.body together
List<QueryMatch> matches = analyzer.queryMatches(source, "java",
    "(method_declaration name: (identifier) @method.name) @method.body");

for (QueryMatch match : matches) {
    String name = match.captures().stream()
        .filter(c -> c.name().equals("method.name"))
        .map(QueryCapture::text)
        .findFirst().orElse("?");
    System.out.println("Method: " + name);  // Method: findAll
}
```

### QueryCapture fields

| Field | Type | Description |
|---|---|---|
| `name()` | `String` | Capture name without `@`, dots preserved — e.g. `"class.name"` |
| `text()` | `String` | Source text of the matched node |
| `startLine()` | `int` | 1-based start line |
| `startColumn()` | `int` | 0-based start column (Tree-sitter convention) |
| `endLine()` | `int` | 1-based end line |
| `endColumn()` | `int` | 0-based end column |

### Error handling

Both methods throw `TreeSitterQueryException` (a `RuntimeException`) if:
- The query string is syntactically invalid for the language's grammar
- The language is not supported

```java
try {
    analyzer.queryCaptures(source, "java", "(bad_node_type @x)");
} catch (TreeSitterQueryException e) {
    System.out.println(e.getMessage());   // "Invalid query: ..."
    System.out.println(e.getLanguage());  // "java"
}
```

---

## Testing query results

If you want to verify that the library extracts the symbols and imports you expect from your
own source files, write assertions directly against the `ParsedFileResult`:

```java
// Given a Java file with this content:
//
//   package com.example.service;
//
//   import java.util.List;
//
//   public class UserService {
//       public List<User> findAll() { ... }
//       public enum Status { ACTIVE, INACTIVE }
//   }

try (var analyzer = TreeSitterAnalyzer.create()) {  // import dk.hjertmann.jwasm_tree_sitter.*;
    ParsedFileResult result = analyzer.parse(source, "java");

    // Verify the class query matched
    assertTrue(result.symbols().stream()
        .anyMatch(s -> s.type() == WasmSymbolType.CLASS && s.name().equals("UserService")));

    // Verify the method query matched specific method names
    List<String> methodNames = result.symbols().stream()
        .filter(s -> s.type() == WasmSymbolType.METHOD)
        .map(WasmSymbol::name)
        .toList();
    assertTrue(methodNames.contains("findAll"));

    // Verify the enum query matched
    assertTrue(result.symbols().stream()
        .anyMatch(s -> s.type() == WasmSymbolType.ENUM && s.name().equals("Status")));

    // Verify the import query captured fully-qualified names
    assertTrue(result.imports().contains("java.util.List"));

    // Verify the package query matched
    assertEquals("com.example.service", result.packageName());
}
```

The same pattern applies to other languages — use the appropriate `WasmSymbolType` values
(`FUNCTION`, `INTERFACE`, etc.) and the language name string accepted by `parse()`.

---

## Building from source

### Prerequisites

- Java 21+
- [Rust](https://rustup.rs/) with the `wasm32-wasip1` target:
  ```bash
  rustup target add wasm32-wasip1
  ```
- [WASI SDK](https://github.com/WebAssembly/wasi-sdk/releases) (for C grammar compilation).
  Either set `WASI_SDK_PATH` or place the SDK at `rust/wasi-sdk-32.0-arm64-macos/`.
- *(Optional)* [Binaryen](https://github.com/WebAssembly/binaryen) (`wasm-opt`) for WASM
  size optimization — install with `brew install binaryen`. The build falls back gracefully
  if it is absent.

### Build steps

```bash
# Clone the repository
git clone https://github.com/hjertmann/tree-sitter-wasm-java.git
cd tree-sitter-wasm-java

# Compile the Rust WASM module and run all tests
./gradlew test

# Build the WASM only (skips Java compilation)
./gradlew buildWasm optimizeWasm

# Run integration tests
./gradlew integrationTest

# Install to local Maven repository
./gradlew publishToMavenLocal
```

If the WASM binary is already present in `src/main/resources/wasm/`, the Rust build is
skipped automatically (inputs are tracked by Gradle).

### Project layout

```
tree-sitter-wasm-java/
├── rust/               # Rust source — Tree-sitter grammars compiled to WASM
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs          # WASM entry point and memory API
│       ├── languages.rs    # Language → grammar + queries dispatch
│       ├── extractor.rs    # Tree-sitter query execution
│       └── types.rs        # Output structs (JSON serialization)
└── src/
    ├── main/java/dk/hjertmann/jwasm_tree_sitter/
    │   ├── TreeSitterAnalyzer.java   # Public API
    │   ├── ParsedFileResult.java
    │   ├── WasmSymbol.java
    │   ├── WasmSymbolType.java
    │   ├── QueryCapture.java
    │   ├── QueryMatch.java
    │   └── TreeSitterQueryException.java
    └── test/
```

---

## Attribution

This library bundles a compiled WebAssembly binary that incorporates Tree-sitter and
numerous grammar crates, all MIT licensed. Full copyright notices are listed in
[THIRD-PARTY-LICENSES](THIRD-PARTY-LICENSES).

This library was inspired by [Lumis4J](https://github.com/roastedroot/lumis4j), which pioneered
the same pattern of compiling a Rust/Tree-sitter crate to `wasm32-wasip1` and running it on the
JVM via Chicory — applied there to syntax highlighting. The Java/WASM boundary (memory
allocation, packed `i64` result encoding, Chicory wiring) follows Lumis4J's approach directly.

Key upstream projects:

- [tree-sitter](https://github.com/tree-sitter/tree-sitter) — Max Brunsfeld
- [Lumis4J](https://github.com/roastedroot/lumis4j) — roastedroot (inspiration and WASM boundary pattern)
- [Chicory](https://github.com/dylibso/chicory) — Dylibso (Apache 2.0, runtime dependency)
- [Jackson](https://github.com/FasterXML/jackson) — FasterXML (Apache 2.0, runtime dependency)
- [serde](https://github.com/serde-rs/serde) — Erick Tryzelaar, David Tolnay

---

## Contributing

Contributions are welcome. Please follow the standard GitHub workflow:

1. **Open an issue** before starting significant work, to align on approach.
2. **Fork** the repository and create a branch from `main`.
3. **Make your changes** with tests covering new behavior.
4. **Ensure the full test suite passes**: `./gradlew test`
5. **Open a pull request** against `main` with a clear description of the change and why.

For grammar additions or updates, the relevant files are `rust/src/languages.rs` (language
dispatch) and `rust/Cargo.toml` (crate versions). Please verify that any new grammar crate
compiles to `wasm32-wasip1` before submitting.

Bug reports and feature requests are tracked via [GitHub Issues](../../issues).

---

## License

[MIT](LICENSE) — Copyright (c) 2026 Robert Hjertmann Christiansen
