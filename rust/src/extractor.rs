use streaming_iterator::StreamingIterator;
use tree_sitter::{Language, Node, Parser, Query, QueryCursor};

use crate::types::{QueryCaptureResult, QueryMatchResult, QueryResult, Symbol, SymbolType};

pub struct ExtractorConfig {
    pub language: Language,
    pub symbol_queries: Vec<SymbolQuery>,
    pub import_query: Option<String>,
    pub package_query: Option<String>,
}

pub struct SymbolQuery {
    pub query: String,
    pub symbol_type: SymbolType,
    pub name_capture: &'static str,
    pub body_capture: Option<&'static str>,
}

pub struct ExtractionResult {
    pub symbols: Vec<Symbol>,
    pub imports: Vec<String>,
    pub package_name: Option<String>,
}

pub fn extract(source: &str, config: &ExtractorConfig) -> ExtractionResult {
    let mut parser = Parser::new();
    parser.set_language(&config.language).expect("Failed to set language");

    let tree = parser.parse(source, None).expect("Failed to parse");
    let root = tree.root_node();
    let source_bytes = source.as_bytes();

    let symbols = extract_symbols(root, source_bytes, config);
    let imports = extract_imports(root, source_bytes, config);
    let package_name = extract_package(root, source_bytes, config);

    ExtractionResult { symbols, imports, package_name }
}

fn extract_symbols(root: Node, source: &[u8], config: &ExtractorConfig) -> Vec<Symbol> {
    let mut symbols = Vec::new();

    for sq in &config.symbol_queries {
        let query = match Query::new(&config.language, &sq.query) {
            Ok(q) => q,
            Err(e) => {
                eprintln!("Query error for '{}': {:?}", sq.query, e);
                continue;
            }
        };

        let name_idx = match query.capture_index_for_name(sq.name_capture) {
            Some(i) => i,
            None => {
                eprintln!("No capture named '{}' in query", sq.name_capture);
                continue;
            }
        };

        let body_idx = sq.body_capture
            .and_then(|cap| query.capture_index_for_name(cap));

        let mut cursor = QueryCursor::new();
        let mut matches = cursor.matches(&query, root, source);

        while let Some(m) = matches.next() {
            let name_node = m.captures.iter()
                .find(|c| c.index == name_idx)
                .map(|c| c.node);

            let name = match name_node {
                Some(n) => node_text(n, source),
                None => continue,
            };

            let body_node = if let Some(bidx) = body_idx {
                m.captures.iter().find(|c| c.index == bidx).map(|c| c.node)
            } else {
                m.captures.first().map(|c| c.node)
            };

            let content = body_node
                .map(|n| node_text(n, source))
                .unwrap_or_default();

            let signature = first_line(&content).trim().to_string();
            let start_line = body_node.map(|n| n.start_position().row + 1).unwrap_or(1);
            let end_line = body_node.map(|n| n.end_position().row + 1).unwrap_or(1);

            symbols.push(Symbol {
                name,
                symbol_type: sq.symbol_type.clone(),
                signature,
                content,
                start_line,
                end_line,
            });
        }
    }

    // Deduplicate by (name, start_line)
    let mut seen = std::collections::HashSet::new();
    symbols.retain(|s| seen.insert((s.name.clone(), s.start_line)));

    symbols
}

fn extract_imports(root: Node, source: &[u8], config: &ExtractorConfig) -> Vec<String> {
    let query_str = match &config.import_query {
        Some(q) => q,
        None => return vec![],
    };

    let query = match Query::new(&config.language, query_str) {
        Ok(q) => q,
        Err(e) => {
            eprintln!("Import query error: {:?}", e);
            return vec![];
        }
    };

    let mut cursor = QueryCursor::new();
    let mut imports = Vec::new();
    let mut matches = cursor.matches(&query, root, source);

    while let Some(m) = matches.next() {
        if let Some(cap) = m.captures.first() {
            let text = node_text(cap.node, source);
            // Strip surrounding quotes for import paths
            let text = text.trim_matches(|c| c == '"' || c == '\'' || c == '<' || c == '>');
            if !text.is_empty() {
                imports.push(text.to_string());
            }
        }
    }

    imports.sort();
    imports.dedup();
    imports
}

fn extract_package(root: Node, source: &[u8], config: &ExtractorConfig) -> Option<String> {
    let query_str = config.package_query.as_ref()?;

    let query = Query::new(&config.language, query_str).ok()?;
    let mut cursor = QueryCursor::new();
    let mut matches = cursor.matches(&query, root, source);

    matches.next()
        .and_then(|m| m.captures.first())
        .map(|c| node_text(c.node, source))
}

// ─── Raw Query API ────────────────────────────────────────────────────────────

pub fn run_query(source: &str, config: &ExtractorConfig, query_str: &str, language: &str) -> QueryResult {
    let mut parser = Parser::new();
    parser.set_language(&config.language).expect("Failed to set language");

    let tree = parser.parse(source, None).expect("Failed to parse");
    let root = tree.root_node();
    let source_bytes = source.as_bytes();

    let query = match Query::new(&config.language, query_str) {
        Ok(q) => q,
        Err(e) => return QueryResult::error(language, &format!("Invalid query: {:?}", e)),
    };

    let capture_names: Vec<String> = query.capture_names().iter().map(|s| s.to_string()).collect();

    let mut cursor = QueryCursor::new();
    let mut matches_iter = cursor.matches(&query, root, source_bytes);
    let mut matches = Vec::new();
    let mut match_index = 0usize;

    while let Some(m) = matches_iter.next() {
        let captures = m.captures.iter().map(|c| {
            let name = capture_names[c.index as usize].clone();
            let text = node_text(c.node, source_bytes);
            let start = c.node.start_position();
            let end = c.node.end_position();
            QueryCaptureResult {
                name,
                text,
                start_line: start.row + 1,
                start_column: start.column,
                end_line: end.row + 1,
                end_column: end.column,
            }
        }).collect();

        matches.push(QueryMatchResult { match_index, captures });
        match_index += 1;
    }

    QueryResult::success(language, matches)
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

fn node_text(node: Node, source: &[u8]) -> String {
    node.utf8_text(source)
        .unwrap_or("")
        .to_string()
}

fn first_line(s: &str) -> String {
    s.lines().next().unwrap_or("").to_string()
}
