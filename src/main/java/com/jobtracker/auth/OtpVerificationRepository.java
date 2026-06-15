package com.jobtracker.auth;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface OtpVerificationRepository extends MongoRepository<OtpVerification, String> {
    Optional<OtpVerification> findTopByEmailAndPurposeOrderByExpiryTimeDesc(String email, String purpose);
    void deleteByEmail(String email);
}
