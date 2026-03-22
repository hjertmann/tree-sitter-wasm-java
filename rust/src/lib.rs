mod extractor;
mod languages;
mod types;

use types::{AnalysisResult, QueryResult, Symbol, SymbolType};

// ─── Memory API ──────────────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn wasm_malloc(size: usize) -> *mut u8 {
    let layout = std::alloc::Layout::from_size_align(size, 1).unwrap();
    unsafe { std::alloc::alloc(layout) }
}

#[no_mangle]
pub extern "C" fn wasm_free(ptr: *mut u8, size: usize) {
    if ptr.is_null() || size == 0 {
        return;
    }
    let layout = std::alloc::Layout::from_size_align(size, 1).unwrap();
    unsafe { std::alloc::dealloc(ptr, layout) }
}

// ─── Warmup ──────────────────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn warmup() {
    // Parse a trivial Java snippet to force tree-sitter initialization
    if let Some(config) = languages::get_config("java") {
        let _ = extractor::extract("class W {}", &config);
    }
}

// ─── Main Analysis Entry Point ───────────────────────────────────────────────

/// Returns packed i64: upper 32 bits = result ptr, lower 32 bits = result len.
/// Caller must free result with wasm_free(ptr, len).
#[no_mangle]
pub extern "C" fn analyze(
    source_ptr: i32,
    source_len: i32,
    lang_ptr: i32,
    lang_len: i32,
) -> i64 {
    let source = read_str(source_ptr, source_len);
    let language = read_str(lang_ptr, lang_len);

    let result = do_analyze(&source, &language);
    let json = serde_json::to_string(&result).unwrap_or_else(|e| {
        format!(r#"{{"success":false,"language":"{}","error":"serialization error: {}"}}"#, language, e)
    });

    pack_result(json.into_bytes())
}

// ─── Raw Query Entry Point ────────────────────────────────────────────────────

/// Run an arbitrary Tree-sitter S-expression query against source code.
/// Returns packed i64: upper 32 bits = result ptr, lower 32 bits = result len.
/// Caller must free result with wasm_free(ptr, len).
#[no_mangle]
pub extern "C" fn query_source(
    source_ptr: i32, source_len: i32,
    lang_ptr:   i32, lang_len:   i32,
    query_ptr:  i32, query_len:  i32,
) -> i64 {
    let source   = read_str(source_ptr, source_len);
    let language = read_str(lang_ptr, lang_len);
    let query    = read_str(query_ptr, query_len);

    let result = do_query(&source, &language, &query);
    let json = serde_json::to_string(&result).unwrap_or_else(|e| {
        format!(r#"{{"success":false,"language":"{}","error":"serialization error: {}"}}"#, language, e)
    });

    pack_result(json.into_bytes())
}

fn do_query(source: &str, language: &str, query: &str) -> QueryResult {
    let config = match languages::get_config(language) {
        Some(c) => c,
        None => return QueryResult::error(language, &format!("Unsupported language: {}", language)),
    };
    extractor::run_query(source, &config, query, language)
}

fn do_analyze(source: &str, language: &str) -> AnalysisResult {
    // SQL has an unreliable grammar — return whole-file fallback directly
    if language == "sql" {
        return AnalysisResult::success(
            language,
            vec![whole_file_symbol(source)],
            vec![],
            None,
        );
    }

    let config = match languages::get_config(language) {
        Some(c) => c,
        None => {
            return AnalysisResult::error(
                language,
                &format!("Unsupported language: {}", language),
            )
        }
    };

    // Empty or whitespace-only source → success with empty symbols
    if source.trim().is_empty() {
        return AnalysisResult::success(language, vec![], vec![], None);
    }

    // For fallback-only languages (html, css, json, yaml, toml, kotlin) return whole file
    // kotlin: grammar crate incompatible with WASI; toml: grammar crate incompatible with WASI
    let is_fallback = matches!(language, "html" | "htm" | "css" | "scss" | "json" | "yaml" | "yml" | "toml" | "kotlin");
    if is_fallback {
        return AnalysisResult::success(
            language,
            vec![whole_file_symbol(source)],
            vec![],
            None,
        );
    }

    let extracted = extractor::extract(source, &config);
    AnalysisResult::success(
        language,
        extracted.symbols,
        extracted.imports,
        extracted.package_name,
    )
}

fn whole_file_symbol(source: &str) -> Symbol {
    let lines: Vec<&str> = source.lines().collect();
    let end_line = lines.len().max(1);
    Symbol {
        name: "file".to_string(),
        symbol_type: SymbolType::Unknown,
        signature: lines.first().copied().unwrap_or("").to_string(),
        content: source.to_string(),
        start_line: 1,
        end_line,
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

fn read_str(ptr: i32, len: i32) -> String {
    if len <= 0 {
        return String::new();
    }
    unsafe {
        let bytes = std::slice::from_raw_parts(ptr as *const u8, len as usize);
        std::str::from_utf8(bytes).unwrap_or("").to_string()
    }
}

fn pack_result(data: Vec<u8>) -> i64 {
    let len = data.len();
    let ptr = data.as_ptr() as i64;
    std::mem::forget(data);
    (ptr << 32) | (len as i64)
}
