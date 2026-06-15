package com.jobtracker.service;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.repository.JobRepository;
import com.jobtracker.auth.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.jobtracker.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.jobtracker.dto.JobApplicationResponse;
import java.util.stream.Collectors;



import java.util.List;

@Service
public class JobService {

    @Autowired
    private JobRepository repo;

    public JobApplication save(JobApplication job) {
        if (job.getCreatedDate() == null) {
            job.setCreatedDate(java.time.LocalDate.now());
        }
        return repo.save(job);
    }
    public JobApplication getById(String id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
    }
    public Page<JobApplication> getAllPaginated(Pageable pageable) {
        return repo.findAll(pageable);
    }
    public List<JobApplication> getAll() {
        return repo.findAll();
    }

    public List<JobApplication> getByUser(String userId) {
        User user = new User();
        user.setId(userId);
        return repo.findByUser(user);
    }
    public JobApplicationResponse convertToDTO(JobApplication job) {
        JobApplicationResponse dto = new JobApplicationResponse();

        dto.setId(job.getId());
        dto.setCompanyName(job.getCompanyName());
        dto.setRole(job.getRole());
        dto.setStatus(job.getStatus());
        dto.setAppliedDate(job.getAppliedDate());
        dto.setNotes(job.getNotes());
        dto.setInterviewDate(job.getInterviewDate());
        dto.setSalary(job.getSalary());
        dto.setLocation(job.getLocation());
        return dto;
    }
    public List<JobApplicationResponse> getAllDTO() {
        return repo.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<JobApplication> getByStatus(String status) {
        return repo.findByStatus(status);
    }

    public void delete(String id) {
        repo.deleteById(id);
    }
    public JobApplication update(String id, JobApplication job) {

        // 🔍 check if job exists
        JobApplication existing = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        // ✏️ update fields
        existing.setCompanyName(job.getCompanyName());
        existing.setRole(job.getRole());
        existing.setStatus(job.getStatus());
        existing.setAppliedDate(job.getAppliedDate());
        existing.setNotes(job.getNotes());
        existing.setInterviewDate(job.getInterviewDate());
        existing.setSalary(job.getSalary());
        existing.setLocation(job.getLocation());
        existing.setJobDescription(job.getJobDescription());
        existing.setInterviewNotes(job.getInterviewNotes());
        existing.setResumePath(job.getResumePath());
        existing.setResumeFilename(job.getResumeFilename());
        existing.setResumeScore(job.getResumeScore());
        existing.setAiAnalysisJson(job.getAiAnalysisJson());
        if (job.getCreatedDate() != null) {
            existing.setCreatedDate(job.getCreatedDate());
        }
        return repo.save(existing);
    }
}