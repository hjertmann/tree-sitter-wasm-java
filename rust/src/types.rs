use serde::{Deserialize, Serialize};

// ─── Query API Types ──────────────────────────────────────────────────────────

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QueryCaptureResult {
    pub name: String,
    pub text: String,
    pub start_line: usize,   // 1-based
    pub start_column: usize, // 0-based
    pub end_line: usize,     // 1-based
    pub end_column: usize,   // 0-based
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QueryMatchResult {
    pub match_index: usize,
    pub captures: Vec<QueryCaptureResult>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QueryResult {
    pub success: bool,
    pub language: String,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    pub matches: Vec<QueryMatchResult>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

impl QueryResult {
    pub fn success(language: &str, matches: Vec<QueryMatchResult>) -> Self {
        QueryResult { success: true, language: language.to_string(), matches, error: None }
    }

    pub fn error(language: &str, message: &str) -> Self {
        QueryResult { success: false, language: language.to_string(), matches: vec![], error: Some(message.to_string()) }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum SymbolType {
    Class,
    Interface,
    Enum,
    Record,
    Method,
    Constructor,
    Function,
    Field,
    Variable,
    Annotation,
    Unknown,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Symbol {
    pub name: String,
    #[serde(rename = "type")]
    pub symbol_type: SymbolType,
    pub signature: String,
    pub content: String,
    pub start_line: usize,
    pub end_line: usize,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AnalysisResult {
    pub success: bool,
    pub language: String,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    pub symbols: Vec<Symbol>,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    pub imports: Vec<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub package_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub primary_type_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub primary_type: Option<SymbolType>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

impl AnalysisResult {
    pub fn success(language: &str, symbols: Vec<Symbol>, imports: Vec<String>, package_name: Option<String>) -> Self {
        let primary = symbols.iter().find(|s| matches!(
            s.symbol_type,
            SymbolType::Class | SymbolType::Interface | SymbolType::Enum | SymbolType::Record
        )).cloned();

        AnalysisResult {
            success: true,
            language: language.to_string(),
            symbols,
            imports,
            package_name,
            primary_type_name: primary.as_ref().map(|s| s.name.clone()),
            primary_type: primary.map(|s| s.symbol_type),
            error: None,
        }
    }

    pub fn error(language: &str, message: &str) -> Self {
        AnalysisResult {
            success: false,
            language: language.to_string(),
            symbols: vec![],
            imports: vec![],
            package_name: None,
            primary_type_name: None,
            primary_type: None,
            error: Some(message.to_string()),
        }
    }
}
