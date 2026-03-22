use tree_sitter::Language;

use crate::extractor::{ExtractorConfig, SymbolQuery};
use crate::types::SymbolType;

pub fn get_config(lang: &str) -> Option<ExtractorConfig> {
    match lang {
        "java" => Some(java_config()),
        "python" => Some(python_config()),
        "javascript" => Some(javascript_config()),
        "typescript" => Some(typescript_config()),
        "tsx" => Some(tsx_config()),
        "go" => Some(go_config()),
        "rust" => Some(rust_config()),
        "c" => Some(c_config()),
        "cpp" => Some(cpp_config()),
        "csharp" | "c#" | "cs" => Some(csharp_config()),
        // kotlin grammar crate requires tree-sitter <0.23 which doesn't compile to WASI
        "kotlin" => None,
        "ruby" => Some(ruby_config()),
        "swift" => Some(swift_config()),
        "scala" => Some(scala_config()),
        "php" => Some(php_config()),
        "bash" | "shell" | "sh" => Some(bash_config()),
        "html" | "htm" => Some(html_config()),
        "css" | "scss" => Some(css_config()),
        "json" => Some(json_config()),
        "yaml" | "yml" => Some(yaml_config()),
        // toml grammar crate requires tree-sitter 0.20 which doesn't compile to WASI
        "toml" => None,
        "markdown" | "md" => Some(markdown_config()),
        "sql" => Some(sql_config()),
        _ => None,
    }
}

pub fn supported_languages() -> Vec<&'static str> {
    vec![
        "java", "python", "javascript", "typescript", "tsx",
        "go", "rust", "c", "cpp", "csharp", "ruby",
        "swift", "scala", "php", "bash", "html", "css", "json",
        "yaml", "markdown", "sql",
        // kotlin and toml handled as fallback (grammar incompatible with WASI)
    ]
}

// ─── Java ────────────────────────────────────────────────────────────────────

fn java_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_java::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(class_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(interface_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Interface,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(enum_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Enum,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(record_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Record,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(annotation_type_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Annotation,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(method_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Method,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(constructor_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Constructor,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(field_declaration declarator: (variable_declarator name: (identifier) @name)) @body"#.into(),
                symbol_type: SymbolType::Field,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: Some(r#"(import_declaration (scoped_identifier) @import)"#.into()),
        package_query: Some(r#"(package_declaration (scoped_identifier) @pkg)"#.into()),
    }
}

// ─── Python ──────────────────────────────────────────────────────────────────

fn python_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_python::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(class_definition name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(function_definition name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: Some(r#"[
            (import_statement (dotted_name) @import)
            (import_from_statement module_name: (dotted_name) @module name: (dotted_name) @import)
            (import_from_statement module_name: (dotted_name) @import)
        ]"#.into()),
        package_query: None,
    }
}

// ─── JavaScript ──────────────────────────────────────────────────────────────

fn javascript_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_javascript::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(class_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(function_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(method_definition name: (property_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Method,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(lexical_declaration (variable_declarator name: (identifier) @name value: [(arrow_function) (function_expression)])) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: Some(r#"(import_statement source: (string (string_fragment) @import))"#.into()),
        package_query: None,
    }
}

// ─── TypeScript ──────────────────────────────────────────────────────────────

fn typescript_config() -> ExtractorConfig {
    let ts_lang: Language = tree_sitter_typescript::LANGUAGE_TYPESCRIPT.into();
    ExtractorConfig {
        language: ts_lang,
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(class_declaration name: (type_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(interface_declaration name: (type_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Interface,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(function_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(method_definition name: (property_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Method,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(type_alias_declaration name: (type_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: Some(r#"(import_statement source: (string (string_fragment) @import))"#.into()),
        package_query: None,
    }
}

// ─── TSX ─────────────────────────────────────────────────────────────────────

fn tsx_config() -> ExtractorConfig {
    let tsx_lang: Language = tree_sitter_typescript::LANGUAGE_TSX.into();
    ExtractorConfig {
        language: tsx_lang,
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(class_declaration name: (type_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(interface_declaration name: (type_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Interface,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(function_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(method_definition name: (property_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Method,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: Some(r#"(import_statement source: (string (string_fragment) @import))"#.into()),
        package_query: None,
    }
}

// ─── Go ──────────────────────────────────────────────────────────────────────

fn go_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_go::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(function_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(method_declaration name: (field_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Method,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(type_declaration (type_spec name: (type_identifier) @name type: (struct_type))) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(type_declaration (type_spec name: (type_identifier) @name type: (interface_type))) @body"#.into(),
                symbol_type: SymbolType::Interface,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: Some(r#"(import_spec path: (interpreted_string_literal) @import)"#.into()),
        package_query: Some(r#"(package_clause (package_identifier) @pkg)"#.into()),
    }
}

// ─── Rust ─────────────────────────────────────────────────────────────────────

fn rust_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_rust::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(struct_item name: (type_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(enum_item name: (type_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Enum,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(trait_item name: (type_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Interface,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(function_item name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(impl_item type: (type_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: Some(r#"(use_declaration argument: (_) @import)"#.into()),
        package_query: None,
    }
}

// ─── C ────────────────────────────────────────────────────────────────────────

fn c_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_c::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(function_definition declarator: (function_declarator declarator: (identifier) @name)) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(struct_specifier name: (type_identifier) @name body: (_)) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: Some(r#"(preproc_include path: [(system_lib_string) (string_literal)] @import)"#.into()),
        package_query: None,
    }
}

// ─── C++ ─────────────────────────────────────────────────────────────────────

fn cpp_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_cpp::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(class_specifier name: (type_identifier) @name body: (_)) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(function_definition declarator: (function_declarator declarator: (identifier) @name)) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(function_definition declarator: (function_declarator declarator: (qualified_identifier name: (identifier) @name))) @body"#.into(),
                symbol_type: SymbolType::Method,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: Some(r#"(preproc_include path: [(system_lib_string) (string_literal)] @import)"#.into()),
        package_query: None,
    }
}

// ─── C# ──────────────────────────────────────────────────────────────────────

fn csharp_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_c_sharp::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(class_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(interface_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Interface,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(enum_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Enum,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(method_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Method,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(constructor_declaration name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Constructor,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(field_declaration (variable_declaration (variable_declarator (identifier) @name))) @body"#.into(),
                symbol_type: SymbolType::Field,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: Some(r#"(using_directive (identifier) @import)"#.into()),
        package_query: Some(r#"(namespace_declaration name: (_) @pkg)"#.into()),
    }
}

// ─── Ruby ─────────────────────────────────────────────────────────────────────

fn ruby_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_ruby::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(class name: (constant) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(method name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Method,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(singleton_method name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Method,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: None,
        package_query: None,
    }
}

// ─── Swift ───────────────────────────────────────────────────────────────────

fn swift_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_swift::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(class_declaration name: (type_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(struct_declaration name: (type_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Record,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(protocol_declaration name: (type_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Interface,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(function_declaration name: (simple_identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(init_declaration) @body"#.into(),
                symbol_type: SymbolType::Constructor,
                name_capture: "body",
                body_capture: Some("body"),
            },
        ],
        import_query: None,
        package_query: None,
    }
}

// ─── Scala ───────────────────────────────────────────────────────────────────

fn scala_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_scala::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(class_definition name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(object_definition name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(trait_definition name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Interface,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(function_definition name: (identifier) @name) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: Some(r#"(import_declaration) @import"#.into()),
        package_query: Some(r#"(package_clause (package_identifier) @pkg)"#.into()),
    }
}

// ─── PHP ─────────────────────────────────────────────────────────────────────

fn php_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_php::LANGUAGE_PHP.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(class_declaration name: (name) @name) @body"#.into(),
                symbol_type: SymbolType::Class,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(interface_declaration name: (name) @name) @body"#.into(),
                symbol_type: SymbolType::Interface,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(function_definition name: (name) @name) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(method_declaration name: (name) @name) @body"#.into(),
                symbol_type: SymbolType::Method,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: Some(r#"(namespace_use_clause (qualified_name) @import)"#.into()),
        package_query: Some(r#"(namespace_definition name: (namespace_name) @pkg)"#.into()),
    }
}

// ─── Bash ────────────────────────────────────────────────────────────────────

fn bash_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_bash::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(function_definition name: (word) @name) @body"#.into(),
                symbol_type: SymbolType::Function,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: None,
        package_query: None,
    }
}

// ─── HTML (fallback) ─────────────────────────────────────────────────────────

fn html_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_html::LANGUAGE.into(),
        symbol_queries: vec![],
        import_query: None,
        package_query: None,
    }
}

// ─── CSS (fallback) ──────────────────────────────────────────────────────────

fn css_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_css::LANGUAGE.into(),
        symbol_queries: vec![],
        import_query: None,
        package_query: None,
    }
}

// ─── JSON (fallback) ─────────────────────────────────────────────────────────

fn json_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_json::LANGUAGE.into(),
        symbol_queries: vec![],
        import_query: None,
        package_query: None,
    }
}

// ─── YAML (fallback) ─────────────────────────────────────────────────────────

fn yaml_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_yaml::LANGUAGE.into(),
        symbol_queries: vec![],
        import_query: None,
        package_query: None,
    }
}

// ─── Markdown ────────────────────────────────────────────────────────────────

fn markdown_config() -> ExtractorConfig {
    ExtractorConfig {
        language: tree_sitter_md::LANGUAGE.into(),
        symbol_queries: vec![
            SymbolQuery {
                query: r#"(atx_heading (atx_h1_marker) heading_content: (_) @name) @body"#.into(),
                symbol_type: SymbolType::Unknown,
                name_capture: "name",
                body_capture: Some("body"),
            },
            SymbolQuery {
                query: r#"(atx_heading (atx_h2_marker) heading_content: (_) @name) @body"#.into(),
                symbol_type: SymbolType::Unknown,
                name_capture: "name",
                body_capture: Some("body"),
            },
        ],
        import_query: None,
        package_query: None,
    }
}

// ─── SQL (fallback) ──────────────────────────────────────────────────────────

fn sql_config() -> ExtractorConfig {
    // tree-sitter-sql 0.0.2 has minimal grammar support; treat as fallback.
    // Return an empty-symbol config using json grammar as structural placeholder.
    ExtractorConfig {
        language: tree_sitter_json::LANGUAGE.into(),
        symbol_queries: vec![],
        import_query: None,
        package_query: None,
    }
}
