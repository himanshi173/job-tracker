package com.jobtracker.auth;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    // 🔍 custom method
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);
}