package com.example.service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Sample service for demonstrating Java analysis.
 */
@Service
public class UserService {

    private final UserRepository repository;

    @Autowired
    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    /**
     * Find a user by their ID.
     */
    public Optional<User> findById(Long id) {
        return repository.findById(id);
    }

    /**
     * Get all active users.
     */
    public List<User> findAllActive() {
        return repository.findByActive(true);
    }

    /**
     * Save a user to the repository.
     */
    public User save(User user) {
        return repository.save(user);
    }

    /**
     * Delete a user by ID.
     */
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public enum Status {
        ACTIVE, INACTIVE, SUSPENDED
    }
}
