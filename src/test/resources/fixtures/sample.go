package storage

import (
	"context"
	"fmt"
	"github.com/pkg/errors"
)

// Store defines the storage interface.
type Store interface {
	Get(ctx context.Context, key string) ([]byte, error)
	Set(ctx context.Context, key string, value []byte) error
	Delete(ctx context.Context, key string) error
}

// MemoryStore is an in-memory implementation of Store.
type MemoryStore struct {
	data map[string][]byte
}

// NewMemoryStore creates a new MemoryStore.
func NewMemoryStore() *MemoryStore {
	return &MemoryStore{data: make(map[string][]byte)}
}

// Get retrieves a value by key.
func (s *MemoryStore) Get(ctx context.Context, key string) ([]byte, error) {
	v, ok := s.data[key]
	if !ok {
		return nil, errors.New("key not found: " + key)
	}
	return v, nil
}

// Set stores a key-value pair.
func (s *MemoryStore) Set(ctx context.Context, key string, value []byte) error {
	s.data[key] = value
	return nil
}

// Delete removes a key.
func (s *MemoryStore) Delete(ctx context.Context, key string) error {
	delete(s.data, key)
	return nil
}

// FormatKey formats a storage key.
func FormatKey(namespace, id string) string {
	return fmt.Sprintf("%s:%s", namespace, id)
}
