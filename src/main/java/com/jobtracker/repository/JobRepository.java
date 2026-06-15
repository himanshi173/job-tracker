package com.jobtracker.repository;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.auth.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface JobRepository extends MongoRepository<JobApplication, String> {

    List<JobApplication> findByStatus(String status);

    List<JobApplication> findByUser(User user);
}