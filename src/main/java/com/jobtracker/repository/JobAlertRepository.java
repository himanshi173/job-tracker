package com.jobtracker.repository;

import com.jobtracker.entity.JobAlert;
import com.jobtracker.auth.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface JobAlertRepository extends MongoRepository<JobAlert, String> {
    List<JobAlert> findByUser(User user);
}
