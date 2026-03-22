# tree-sitter-wasm-java — Project Guide

## What This Project Is

A standalone Java library that provides **multi-language code analysis** (symbol extraction,
import detection, package/module declarations) using **Tree-sitter grammars compiled to
WebAssembly**, executed by **Chicory** — a pure-Java WASM runtime with zero native dependencies.

The output of this project is a single Maven artifact:

```
groupId:    dk.hjertmann
artifactId: jtreesitter
version:    0.1.0-SNAPSHOT
```

This JAR bundles a pre-built `tree-sitter-analysis.wasm` as a classpath resource and exposes a
simple Java API. Users get structured code analysis for 20+ languages with no `.so`, no `.dylib`,
no JNI, no `--enable-native-access` flag — just a JAR.

---

## Motivation / Prior Art

Applications that load Tree-sitter grammars as native shared libraries (`.dylib` on macOS,
`.so` on Linux) face a platform matrix problem and require `--enable-native-access=ALL-UNNAMED`
at runtime. This library eliminates both constraints.

**Lumis4J** (`io.roastedroot:lumis4j`) solved an identical problem for *syntax highlighting*: it
wraps the [lumis](https://github.com/leandrocp/lumis) Rust crate (which uses Tree-sitter
internally) into a `wasm32-wasip1` binary and runs it via Chicory. The pattern works and is fast
(cold start ~100ms after WASM initialization, subsequent calls ~20ms).

This project applies the same pattern but for **code analysis** instead of highlighting.

---

## Tech Stack

| Layer | Technology |
|---|---|
| WASM module | Rust, `wasm32-wasip1` target, `tree-sitter` + per-language grammar crates |
| WASM runtime | [Chicory](https://github.com/dylibso/chicory) 1.4.x (pure Java, WASIp1) |
| Java wrapper | Java 24 toolchain → Java 21 bytecode, Gradle (Kotlin DSL) |
| JSON boundary | Jackson (parse WASM output) or hand-written minimal deserializer |
| Testing | JUnit 5 |

---

## Project Structure

```
tree-sitter-wasm-java/
├── rust/                                  # Rust WASM module
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs                         # WASM entry point, memory API
│       ├── languages.rs                   # Language name → grammar + queries
│       ├── extractor.rs                   # Tree-sitter query execution
│       └── types.rs                       # Output structs (serde_json serialization)
├── src/
│   ├── main/
│   │   ├── java/dk/hjertmann/parser/wasm/
│   │   │   ├── TreeSitterAnalyzer.java    # Main public API (AutoCloseable)
│   │   │   ├── ParsedFileResult.java      # Output record
│   │   │   ├── WasmSymbol.java            # Symbol record (JSON → Java)
│   │   │   └── WasmSymbolType.java        # Enum of symbol types
│   │   └── resources/
│   │       └── wasm/
│   │           └── tree-sitter-analysis.wasm   # Built or downloaded at build time
│   └── test/
│       └── java/dk/hjertmann/parser/wasm/
│           ├── JavaAnalysisTest.java
│           ├── MultiLanguageAnalysisTest.java
│           └── FallbackBehaviorTest.java
├── build.gradle.kts
├── gradle/
│   └── wrapper/
├── CLAUDE.md
└── README.md
```

---

## Architecture

```
Java caller
    │
    │  analyzer.parse(sourceCode, "java")
    ▼
TreeSitterAnalyzer.java
    │  ① write source bytes into WASM linear memory via wasm_malloc
    │  ② call analyze(src_ptr, src_len, lang_ptr, lang_len)
    │  ③ read result bytes from WASM memory
    │  ④ call wasm_free on both input and output buffers
    ▼
Chicory WASM runner  (loads tree-sitter-analysis.wasm from classpath once at startup)
    │
    ▼
Rust WASM module  (tree-sitter + all grammar crates, compiled to wasm32-wasip1)
    │  runs Tree-sitter queries per language
    │  extracts symbols, imports, package/module name
    │  serializes result to JSON bytes
    ▼
Java JSON deserialization  (Jackson ObjectMapper)
    │
    ▼
ParsedFileResult (Java record returned to caller)
```

---

## WASM Binary API Contract

The Rust module **must** export exactly these four functions:

```rust
// Allocate `size` bytes in WASM linear memory. Returns pointer.
#[no_mangle]
pub extern "C" fn wasm_malloc(size: usize) -> *mut u8;

// Free `size` bytes at `ptr`.
#[no_mangle]
pub extern "C" fn wasm_free(ptr: *mut u8, size: usize);

// Main analysis entry point.
// source_ptr / source_len: UTF-8 source code in WASM memory
// lang_ptr / lang_len:     language name string (e.g. "java") in WASM memory
// Returns: packed i64 — upper 32 bits = result ptr, lower 32 bits = result len
// Result is a UTF-8 JSON string. Caller must free with wasm_free.
#[no_mangle]
pub extern "C" fn analyze(
    source_ptr: i32, source_len: i32,
    lang_ptr:   i32, lang_len:   i32,
) -> i64;

// Warm-up / pre-initialize call with no-op input. Called once at startup.
#[no_mangle]
pub extern "C" fn warmup();
```

Memory ownership: the Java caller allocates input buffers (via `wasm_malloc`), calls `analyze`,
reads the output, then frees **both** input buffers and the output buffer.

---

## Output JSON Schema

### Success

```json
{
  "success": true,
  "language": "java",
  "symbols": [
    {
      "name": "MyService",
      "type": "CLASS",
      "signature": "public class MyService",
      "content": "public class MyService {\n  ...\n}",
      "start_line": 5,
      "end_line": 80
    },
    {
      "name": "process",
      "type": "METHOD",
      "signature": "public Result process(Request req)",
      "content": "public Result process(Request req) {\n  ...\n}",
      "start_line": 20,
      "end_line": 45
    }
  ],
  "imports": [
    "java.util.List",
    "org.springframework.stereotype.Service"
  ],
  "package_name": "com.example.service",
  "primary_type_name": "MyService",
  "primary_type": "CLASS"
}
```

### Failure / Unsupported Language

```json
{
  "success": false,
  "language": "cobol",
  "error": "Unsupported language: cobol"
}
```

### Parse Error (Tree-sitter returned error nodes)

Return a partial success — include whatever symbols were extracted, set `"success": true`.
Only set `"success": false` for programming errors or unsupported languages, not for
syntactically invalid input files (Tree-sitter is error-tolerant and always produces a tree).

---

## Java API

### TreeSitterAnalyzer

```java
package dk.hjertmann.parser.wasm;

public final class TreeSitterAnalyzer implements AutoCloseable {

    /**
     * Create a new analyzer, loading the bundled WASM from the classpath.
     * Initializes the Chicory instance and calls warmup(). Thread-safe after construction.
     * This is expensive — create once and reuse.
     */
    public static TreeSitterAnalyzer create();

    /** Detect language from file extension. Returns "unknown" if not recognized. */
    public String detectLanguage(Path filePath);

    /** Returns true if this analyzer supports the given language name (e.g. "java"). */
    public boolean supportsLanguage(String language);

    /** Returns all supported language names. */
    public Set<String> supportedLanguages();

    /**
     * Parse source code for the given language.
     * On unsupported language: returns a fallback result (single UNKNOWN symbol).
     * Never throws — all errors are represented in ParsedFileResult.
     */
    public ParsedFileResult parse(String sourceCode, String language);

    /** Convenience: read file, detect language from extension, then parse. */
    public ParsedFileResult parseFile(Path filePath) throws IOException;

    @Override
    public void close();
}
```

### ParsedFileResult

```java
package dk.hjertmann.parser.wasm;

public record ParsedFileResult(
    boolean success,
    String language,
    List<WasmSymbol> symbols,
    List<String> imports,          // empty list (not null) for languages without imports
    String packageName,            // null for languages without package declarations
    String primaryTypeName,        // null if no primary type found
    WasmSymbolType primaryType,    // null if no primary type found
    String errorMessage            // null on success
) {
    /** Fallback: wraps entire file content as one UNKNOWN symbol. */
    public static ParsedFileResult fallback(String language, String content, String fileName);
}
```

### WasmSymbol

```java
package dk.hjertmann.parser.wasm;

public record WasmSymbol(
    String name,
    WasmSymbolType type,
    String signature,
    String content,     // full body text used as embedding input
    int startLine,
    int endLine
) {}
```

### WasmSymbolType

```java
package dk.hjertmann.parser.wasm;

public enum WasmSymbolType {
    CLASS, INTERFACE, ENUM, RECORD, METHOD, CONSTRUCTOR,
    FUNCTION, FIELD, VARIABLE, ANNOTATION, UNKNOWN
}
```

---

## Language Support Requirements

The WASM module **must** support all of the following. Grammar crates are from crates.io; verify
each is `wasm32-wasip1`-compatible before pinning (most are, since they're pure C parsers wrapped
in Rust FFI).

| Language | Cargo crate | Extensions | Symbol types extracted |
|---|---|---|---|
| Java | `tree-sitter-java` | `.java` | CLASS, INTERFACE, ENUM, RECORD, METHOD, CONSTRUCTOR, FIELD |
| Python | `tree-sitter-python` | `.py` | FUNCTION, CLASS |
| JavaScript | `tree-sitter-javascript` | `.js` `.mjs` `.cjs` | FUNCTION, CLASS, METHOD |
| TypeScript | `tree-sitter-typescript` (ts) | `.ts` | FUNCTION, CLASS, INTERFACE, METHOD |
| TSX | `tree-sitter-typescript` (tsx) | `.tsx` | FUNCTION, CLASS, INTERFACE, METHOD |
| Go | `tree-sitter-go` | `.go` | FUNCTION, METHOD, CLASS (struct) |
| Rust | `tree-sitter-rust` | `.rs` | FUNCTION, METHOD, CLASS (struct), INTERFACE (trait) |
| C | `tree-sitter-c` | `.c` `.h` | FUNCTION, CLASS (struct) |
| C++ | `tree-sitter-cpp` | `.cpp` `.cc` `.cxx` `.hpp` | FUNCTION, METHOD, CLASS |
| C# | `tree-sitter-c-sharp` | `.cs` | CLASS, INTERFACE, ENUM, METHOD, CONSTRUCTOR, FIELD |
| Kotlin | `tree-sitter-kotlin` | `.kt` `.kts` | **excluded from WASM** — falls back to whole-file UNKNOWN (depends on tree-sitter 0.20.x which uses `fdopen`/`dup`, incompatible with WASI) |
| Ruby | `tree-sitter-ruby` | `.rb` | METHOD, CLASS |
| Swift | `tree-sitter-swift` | `.swift` | FUNCTION, CLASS, RECORD (struct), INTERFACE (protocol), CONSTRUCTOR |
| Scala | `tree-sitter-scala` | `.scala` | CLASS, FUNCTION, METHOD |
| PHP | `tree-sitter-php` | `.php` | FUNCTION, CLASS, METHOD |
| Bash/Shell | `tree-sitter-bash` | `.sh` `.bash` | FUNCTION |
| Markdown | `tree-sitter-md` | `.md` | (headings as UNKNOWN symbols, imports=[]) |
| HTML | `tree-sitter-html` | `.html` `.htm` | (fallback: whole file as UNKNOWN) |
| CSS | `tree-sitter-css` | `.css` `.scss` | (fallback: whole file as UNKNOWN) |
| JSON | `tree-sitter-json` | `.json` | (fallback: whole file as UNKNOWN) |
| YAML | `tree-sitter-yaml` | `.yml` `.yaml` | (fallback: whole file as UNKNOWN) |
| TOML | `tree-sitter-toml` | `.toml` | **excluded from WASM** — falls back to whole-file UNKNOWN (same WASI incompatibility as Kotlin) |
| SQL | `tree-sitter-sql` | `.sql` | (fallback: whole file as UNKNOWN) |

If a grammar crate is not available on crates.io or does not compile to `wasm32-wasip1`, log a
build warning and skip it gracefully. The Java API returns `success: false` with an appropriate
message for unsupported languages.

### Import Extraction (per language)

Extract imports as fully-qualified strings:

| Language | Tree-sitter node | Example output |
|---|---|---|
| Java | `import_declaration` → `scoped_identifier` | `"java.util.List"` |
| Python | `import_statement`, `import_from_statement` | `"os"`, `"pathlib.Path"` |
| JavaScript/TypeScript | `import_statement` → string literal | `"./utils"`, `"react"` |
| Go | `import_spec` → interpreted string | `"fmt"`, `"github.com/pkg/errors"` |
| Rust | `use_declaration` → path | `"std::collections::HashMap"` |
| C/C++ | `preproc_include` → path/string | `"stdio.h"`, `"vector"` |
| C# | `using_directive` → identifier | `"System.Collections.Generic"` |
| Kotlin | `import_header` → identifier | `"kotlin.collections.List"` |
| Scala | `import_declaration` | `"scala.collection.mutable.Map"` |
| PHP | `use_declaration`, `namespace_use_clause` | `"App\\Http\\Controller"` |
| All others | (not applicable) | empty list `[]` |

### Package/Module Declaration (per language)

| Language | Tree-sitter node | Field |
|---|---|---|
| Java | `package_declaration` | `package_name` |
| Go | `package_clause` → `package_identifier` | `package_name` |
| C# | `namespace_declaration` → `qualified_name` | `package_name` |
| Kotlin | `package_header` → `identifier` | `package_name` |
| Scala | `package_clause` | `package_name` |
| PHP | `namespace_definition` | `package_name` |
| All others | (not applicable) | `null` |

---

## Rust Implementation Notes

### Cargo.toml structure

```toml
[package]
name = "tree-sitter-analysis"
version = "0.1.0"
edition = "2021"

[lib]
crate-type = ["cdylib"]

[dependencies]
tree-sitter = "0.25"
tree-sitter-java = "0.23"
tree-sitter-python = "0.25"
tree-sitter-javascript = "0.25"
tree-sitter-typescript = "0.23"
tree-sitter-go = "0.25"
tree-sitter-rust = "0.24"
tree-sitter-c = "0.24"
tree-sitter-cpp = "0.23"
tree-sitter-c-sharp = "0.23"
tree-sitter-ruby = "0.23"
tree-sitter-swift = "0.7"
tree-sitter-scala = "0.25"
tree-sitter-php = "0.24"
tree-sitter-bash = "0.25"
tree-sitter-html = "0.23"
tree-sitter-css = "0.25"
tree-sitter-json = "0.24"
tree-sitter-yaml = "0.7"
tree-sitter-md = "0.5"
# NOTE: tree-sitter-kotlin and tree-sitter-toml are excluded — they depend on
# tree-sitter 0.20.x which uses fdopen/dup syscalls incompatible with WASI.
serde = { version = "1", features = ["derive"] }
serde_json = "1"
streaming-iterator = "0.1"    # required for tree-sitter 0.25.x QueryMatches API

[profile.release]
opt-level = "z"       # size-optimized (smaller WASM binary)
lto = true
codegen-units = 1
```

**IMPORTANT**: `tree-sitter 0.25.x` uses `StreamingIterator` (not `Iterator`) for `QueryMatches`.
Always `use streaming_iterator::StreamingIterator` and use `while let Some(m) = matches.next()`.

Verify grammar crate versions on crates.io before pinning — the ecosystem is inconsistent.

### Memory allocation (lib.rs)

Use the standard allocator via `extern "C"` wrappers — do NOT use a custom allocator unless
required:

```rust
#[no_mangle]
pub extern "C" fn wasm_malloc(size: usize) -> *mut u8 {
    let layout = std::alloc::Layout::from_size_align(size, 1).unwrap();
    unsafe { std::alloc::alloc(layout) }
}

#[no_mangle]
pub extern "C" fn wasm_free(ptr: *mut u8, size: usize) {
    let layout = std::alloc::Layout::from_size_align(size, 1).unwrap();
    unsafe { std::alloc::dealloc(ptr, layout) }
}
```

### Packed result encoding (matches Lumis4J pattern)

```rust
fn pack_result(data: Vec<u8>) -> i64 {
    let len = data.len();
    let ptr = data.as_ptr() as i64;
    std::mem::forget(data);           // caller owns the memory now
    (ptr << 32) | (len as i64)
}
```

### Language dispatch (languages.rs)

Use a match on the language string. For each language, define:
1. A function that returns the Tree-sitter `Language` (from the grammar crate)
2. The set of Tree-sitter S-expression queries to run (one per symbol type)
3. Whether to extract imports and how
4. Whether to extract a package name and how

The query format uses the same Tree-sitter S-expression DSL supported by all Tree-sitter language bindings.

---

## Chicory Integration (Java side)

Model the Java integration directly on Lumis4J's `Lumis.java`. Key points:

1. Load the WASM from classpath: `TreeSitterAnalyzer.class.getResourceAsStream("/wasm/tree-sitter-analysis.wasm")`
2. Create `WasiOptions` with `inheritSystem()` and a `WasiPreview1` instance
3. Build a Chicory `Instance` using `Instance.builder(module).withImportValues(wasiImports).build()`
4. Wrap the exports (call `wasm_malloc`, `analyze`, `wasm_free`) in a helper class
5. Call `warmup()` immediately after construction to avoid cold-start latency on first `parse()`
6. Synchronize on the `Instance` if called concurrently (Chicory instances are not thread-safe) — or maintain a pool

### Thread safety

Use a simple `synchronized` block around the WASM call, OR create a
`BlockingDeque<Instance>` pool (one instance per thread). For high-concurrency use cases (virtual
thread per file), a pool of 4–8 instances is sufficient.

---

## Build Configuration

### WASI SDK requirement

The WASM build requires the **WASI SDK** because tree-sitter grammar crates compile C code.
`wasm32-wasip1` cross-compilation needs the WASI sysroot for stdlib headers (`stdio.h`, etc.).

WASI SDK 32 for arm64-macos is checked in at `rust/wasi-sdk-32.0-arm64-macos/` (not committed to
git — download separately if missing). The `buildWasm` task sets these env vars automatically:
- `WASI_SDK_PATH` — defaults to `<projectDir>/rust/wasi-sdk-32.0-arm64-macos`
- `CC_wasm32_wasip1` — `$WASI_SDK_PATH/bin/clang`
- `CFLAGS_wasm32_wasip1` — `--sysroot=$WASI_SDK_PATH/share/wasi-sysroot`

### Gradle task chain

The full pipeline is: `verifyRustToolchain` → `buildWasm` → `optimizeWasm` → `processResources`

- **`verifyRustToolchain`** — checks `cargo` and `wasm32-wasip1` target are installed
- **`buildWasm`** — runs `cargo build --release --target wasm32-wasip1` with WASI SDK env vars
- **`optimizeWasm`** — runs `wasm-opt -Oz` if available (falls back to plain copy); copies output to `src/main/resources/wasm/`
- **`processResources`** — only triggers `optimizeWasm` if the WASM resource doesn't already exist

`verifyRustToolchain` fails with:

```
ERROR: Rust toolchain required. Install with:
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
  rustup target add wasm32-wasip1
```

Install `wasm-opt` for smaller binaries: `brew install binaryen`

### Java toolchain constraint

Java 25 breaks Gradle's Kotlin DSL (Kotlin can't parse "25.x" version strings). The build uses a
**Java 24 toolchain** hardcoded in `build.gradle.kts`, producing Java 21-compatible bytecode
(`sourceCompatibility = JavaVersion.VERSION_21`). Do not upgrade to Java 25 without verifying
Gradle + Kotlin DSL compatibility first.

### Download pre-built WASM (for CI / users without Rust)

For releases, also provide a Gradle task `downloadWasm` that fetches the WASM from this project's
own GitHub Releases. `buildWasm` should be the primary path during development; `downloadWasm` is
for CI builds that just want to publish the JAR.

---

## Testing Requirements

### Unit tests

All test source files are embedded in `src/test/resources/fixtures/`:

- `Sample.java` — a realistic Spring Boot `@Service` class with methods, fields, Javadoc, imports, package declaration
- `sample.py` — functions and classes with docstrings and imports
- `sample.ts` — TypeScript interfaces, classes, imports
- `sample.go` — Go file with package, imports, functions, structs
- `sample.rs` — Rust file with structs, impl blocks, use declarations
- `sample.cs` — C# file with namespace, using directives, class, methods
- `sample.js`, `sample.rb`, `sample.kt`, `sample.cpp`, `sample.sh`, `sample.md`

**Required test cases:**

1. `JavaAnalysisTest` — parse `Sample.java` → verify:
   - At least one CLASS symbol with correct name
   - At least one METHOD symbol
   - `imports` list is non-empty and contains recognized fully-qualified names
   - `packageName` is correct
   - `primaryTypeName` matches the public class name
   - `primaryType` is `CLASS`

2. `MultiLanguageAnalysisTest` — for each supported language, parse the fixture file →
   - `success` is true
   - `symbols` is non-empty (except for markup/data formats which return UNKNOWN fallback)
   - No exceptions thrown

3. `FallbackBehaviorTest` —
   - Unsupported language → `success: false`, single UNKNOWN fallback
   - Empty file → success, empty symbols list
   - Null/blank source → no exception, success, empty symbols

4. Integration tests (tagged `@Tag("integration")`) — test with larger real files if available

### Run tests

```bash
./gradlew test                    # unit tests only
./gradlew integrationTest         # integration tests
```

---

## Key Constraints (Non-negotiable)

1. **Zero native dependencies at runtime** — no `.so`, `.dylib`, `.dll` in the JAR or on the system path
2. **No `--enable-native-access` JVM flag** — Chicory is pure bytecode; this must work in a standard JVM
3. **No external API calls at runtime** — everything runs locally
4. **Graceful degradation** — unsupported language or parse error → fallback result, not exception
5. **`TreeSitterAnalyzer` is AutoCloseable** — the Chicory WASM instance must be closed to free memory
6. **`TreeSitterAnalyzer.create()` is expensive** — document that users should create once and reuse (e.g. as a Spring singleton `@Bean`)
7. **`parse()` is thread-safe** — safe to call from multiple virtual threads concurrently

---

## Dependency Policy

**Do not add dependencies without checking maven-deps-server for the latest version.**

Pre-approved dependencies for this module:
- `com.dylibso.chicory:runtime` (currently 1.4.0)
- `com.dylibso.chicory:wasi` (same version as runtime)
- `com.fasterxml.jackson.core:jackson-databind` (for JSON parsing — check latest)
- `org.junit.jupiter:junit-jupiter` (testing only)

If Jackson feels too heavy for a library artifact, a hand-written minimal JSON deserializer is
acceptable — the output schema is simple and fixed.

---

## Reference: Lumis4J as Implementation Model

Study the Lumis4J source at https://github.com/roastedroot/lumis4j to understand:
- How `wasm-build/src/lib.rs` exposes the Rust API over the WASM boundary
- How `core/src/main/java/io/roastedroot/lumis4j/core/Lumis.java` uses Chicory to load and call the module
- How `wasm_malloc` / `wasm_free` are used to pass strings across the Java/WASM boundary
- How the packed `i64` result is decoded via `unpackResult()`

The analysis module should follow the same memory management pattern exactly. The primary
difference is that the Rust side calls tree-sitter query APIs and returns structured JSON instead
of highlighted text.
