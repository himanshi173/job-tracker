package com.jobtracker.repository;

import com.jobtracker.entity.PracticeInterview;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface PracticeInterviewRepository extends MongoRepository<PracticeInterview, String> {
    List<PracticeInterview> findByUserId(String userId);
}
