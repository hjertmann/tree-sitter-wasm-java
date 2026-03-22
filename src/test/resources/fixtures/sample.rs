use std::collections::HashMap;
use std::sync::{Arc, Mutex};

/// A thread-safe key-value cache.
pub struct Cache<V> {
    store: Arc<Mutex<HashMap<String, V>>>,
}

impl<V: Clone> Cache<V> {
    /// Create a new empty cache.
    pub fn new() -> Self {
        Cache {
            store: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    /// Insert a value.
    pub fn insert(&self, key: String, value: V) {
        self.store.lock().unwrap().insert(key, value);
    }

    /// Get a value by key.
    pub fn get(&self, key: &str) -> Option<V> {
        self.store.lock().unwrap().get(key).cloned()
    }

    /// Remove a key.
    pub fn remove(&self, key: &str) -> Option<V> {
        self.store.lock().unwrap().remove(key)
    }
}

/// Serializable cache entry.
pub struct Entry {
    pub key: String,
    pub value: String,
}

/// Error types for cache operations.
pub enum CacheError {
    NotFound,
    LockPoisoned,
}

pub trait Serializable {
    fn serialize(&self) -> Vec<u8>;
    fn deserialize(bytes: &[u8]) -> Option<Self> where Self: Sized;
}
