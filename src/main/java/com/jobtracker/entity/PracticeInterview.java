package com.jobtracker.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.ArrayList;

@Document(collection = "practice_interviews")
public class PracticeInterview {

    @Id
    private String id;
    private String userId;
    private String jobId;
    private String companyName;
    private String role;
    private List<ChatMessage> messages = new ArrayList<>();
    private int currentQuestionIndex;
    private int maxQuestions = 5;
    private boolean isCompleted;
    private String evaluationJson;

    public static class ChatMessage {
        private String sender; // "interviewer" or "candidate"
        private String text;

        public ChatMessage() {}

        public ChatMessage(String sender, String text) {
            this.sender = sender;
            this.text = text;
        }

        public String getSender() {
            return sender;
        }

        public void setSender(String sender) {
            this.sender = sender;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }

    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(int currentQuestionIndex) { this.currentQuestionIndex = currentQuestionIndex; }

    public int getMaxQuestions() { return maxQuestions; }
    public void setMaxQuestions(int maxQuestions) { this.maxQuestions = maxQuestions; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean isCompleted) { this.isCompleted = isCompleted; }

    public String getEvaluationJson() { return evaluationJson; }
    public void setEvaluationJson(String evaluationJson) { this.evaluationJson = evaluationJson; }
}
