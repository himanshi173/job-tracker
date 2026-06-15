package com.jobtracker.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import java.time.LocalDate;
import com.jobtracker.auth.User;

@Document(collection = "job_applications")
public class JobApplication {

        @Id
        private String id;

        private String companyName;
        private String role;

        private String status;

        private LocalDate appliedDate;

        private String notes;
        private LocalDate interviewDate;

        @DBRef
        private User user;

        private String salary;
        private String location;
        private String resumePath;
        private String resumeFilename;
        private String jobDescription;
        private Integer resumeScore;
        private String aiAnalysisJson;
        private String interviewNotes;
        private LocalDate createdDate;

        // ✅ GETTERS & SETTERS

        public String getResumePath() {
                return resumePath;
        }

        public void setResumePath(String resumePath) {
                this.resumePath = resumePath;
        }

        public String getResumeFilename() {
                return resumeFilename;
        }

        public void setResumeFilename(String resumeFilename) {
                this.resumeFilename = resumeFilename;
        }

        public String getJobDescription() {
                return jobDescription;
        }

        public void setJobDescription(String jobDescription) {
                this.jobDescription = jobDescription;
        }

        public Integer getResumeScore() {
                return resumeScore;
        }

        public void setResumeScore(Integer resumeScore) {
                this.resumeScore = resumeScore;
        }

        public String getAiAnalysisJson() {
                return aiAnalysisJson;
        }

        public void setAiAnalysisJson(String aiAnalysisJson) {
                this.aiAnalysisJson = aiAnalysisJson;
        }

        public String getInterviewNotes() {
                return interviewNotes;
        }

        public void setInterviewNotes(String interviewNotes) {
                this.interviewNotes = interviewNotes;
        }

        public String getSalary() {
                return salary;
        }

        public void setSalary(String salary) {
                this.salary = salary;
        }

        public String getLocation() {
                return location;
        }

        public void setLocation(String location) {
                this.location = location;
        }

        public String getId() {
                return id;
        }
        public void setId(String id) {
                this.id = id;
        }
        public String getCompanyName() {
                return companyName;
        }

        public void setCompanyName(String companyName) {
                this.companyName = companyName;
        }

        public String getRole() {
                return role;
        }

        public void setRole(String role) {
                this.role = role;
        }

        public String getStatus() {
                return status;
        }

        public void setStatus(String status) {
                this.status = status;
        }

        public LocalDate getAppliedDate() {
                return appliedDate;
        }

        public void setAppliedDate(LocalDate appliedDate) {
                this.appliedDate = appliedDate;
        }

        public String getNotes() {
                return notes;
        }

        public void setNotes(String notes) {
                this.notes = notes;
        }

        public LocalDate getInterviewDate() {
                return interviewDate;
        }

        public void setInterviewDate(LocalDate interviewDate) {
                this.interviewDate = interviewDate;
        }

        public User getUser() {
                return user;
        }

        public void setUser(User user) {
                this.user = user;
        }

        public LocalDate getCreatedDate() {
                return createdDate;
        }

        public void setCreatedDate(LocalDate createdDate) {
                this.createdDate = createdDate;
        }
}