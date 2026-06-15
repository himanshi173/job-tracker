package com.jobtracker.dto;

import java.time.LocalDate;

public class JobApplicationResponse {

    private String id;
    private String companyName;
    private String role;
    private String status;
    private LocalDate appliedDate;
    private String notes;
    private String salary;
    private String location;

    public String getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public LocalDate getAppliedDate() { return appliedDate; }
    public String getNotes() { return notes; }
    public String getSalary() { return salary; }
    public String getLocation() { return location; }
    private LocalDate interviewDate;

    public void setId(String id) { this.id = id; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setRole(String role) { this.role = role; }
    public void setStatus(String status) { this.status = status; }
    public void setAppliedDate(LocalDate appliedDate) { this.appliedDate = appliedDate; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setSalary(String salary) { this.salary = salary; }
    public void setLocation(String location) { this.location = location; }
    public LocalDate getInterviewDate() {
        return interviewDate;
    }

    public void setInterviewDate(LocalDate interviewDate) {
        this.interviewDate = interviewDate;
    }
}