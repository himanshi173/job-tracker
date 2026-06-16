package com.jobtracker.controller;

import com.jobtracker.entity.PracticeInterview;
import com.jobtracker.entity.PracticeInterview.ChatMessage;
import com.jobtracker.repository.PracticeInterviewRepository;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.service.JobService;
import com.jobtracker.service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/interviews")
@CrossOrigin(origins = "*")
public class MockInterviewController {

    @Autowired
    private PracticeInterviewRepository interviewRepository;

    @Autowired
    private JobService jobService;

    @Autowired
    private GeminiService geminiService;

    // ✅ START MOCK INTERVIEW
    @PostMapping("/start")
    public ResponseEntity<?> startInterview(@RequestParam String jobId, @RequestParam String userId) {
        try {
            JobApplication job = jobService.getById(jobId);
            if (job == null) {
                return ResponseEntity.badRequest().body("Job application not found");
            }

            // Extract resume text if present
            String resumeText = "";
            if (job.getResumePath() != null && !job.getResumePath().trim().isEmpty()) {
                try {
                    Path file = Paths.get(job.getResumePath());
                    if (Files.exists(file)) {
                        try (InputStream in = Files.newInputStream(file)) {
                            resumeText = geminiService.extractTextFromPdf(in);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse resume text for practice session: " + e.getMessage());
                }
            }

            String jobDesc = job.getJobDescription();
            if (jobDesc == null || jobDesc.trim().isEmpty()) {
                jobDesc = "A position for " + job.getRole() + " at " + job.getCompanyName() + ".";
            }

            // Generate first question
            String prompt = "You are a professional technical interviewer at " + job.getCompanyName() + " conducting a mock interview for the position of " + job.getRole() + ".\n"
                    + "Here is the candidate's resume (if available):\n" + resumeText + "\n\n"
                    + "Here is the job description:\n" + jobDesc + "\n\n"
                    + "Please start the mock interview by introducing yourself briefly (in 1 sentence) as the interviewer and ask the 1st technical or behavioral question.\n"
                    + "Keep the question clear, direct, and tailored to their skills.\n"
                    + "Respond with ONLY the interviewer's words (no extra metadata or packaging).";

            String firstQuestion = geminiService.generateContent(prompt);
            if (firstQuestion == null || firstQuestion.trim().isEmpty() || firstQuestion.startsWith("Gemini API failed")) {
                firstQuestion = "Hello, I am your interviewer for the " + job.getRole() + " role. To start off, could you please tell me about your background and your experience working with core technologies required for this role?";
            }

            // Create Practice Session
            PracticeInterview session = new PracticeInterview();
            session.setUserId(userId);
            session.setJobId(jobId);
            session.setCompanyName(job.getCompanyName());
            session.setRole(job.getRole());
            session.setCurrentQuestionIndex(1);
            session.setMaxQuestions(5);
            session.setCompleted(false);
            session.getMessages().add(new ChatMessage("interviewer", firstQuestion));

            PracticeInterview saved = interviewRepository.save(session);
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error starting mock interview: " + e.getMessage());
        }
    }

    // ✅ SUBMIT ANSWER & ASK NEXT QUESTION OR EVALUATE
    @PostMapping("/{id}/answer")
    public ResponseEntity<?> submitAnswer(@PathVariable String id, @RequestBody Map<String, String> requestBody) {
        try {
            String candidateAnswer = requestBody.get("answer");
            if (candidateAnswer == null || candidateAnswer.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Answer cannot be empty");
            }

            Optional<PracticeInterview> sessionOpt = interviewRepository.findById(id);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Practice session not found");
            }

            PracticeInterview session = sessionOpt.get();
            if (session.isCompleted()) {
                return ResponseEntity.badRequest().body("This practice session is already completed");
            }

            // Append candidate response
            session.getMessages().add(new ChatMessage("candidate", candidateAnswer));

            JobApplication job = jobService.getById(session.getJobId());
            String jobDesc = job != null && job.getJobDescription() != null ? job.getJobDescription() : "";
            String resumeText = "";
            if (job != null && job.getResumePath() != null && !job.getResumePath().trim().isEmpty()) {
                try {
                    Path file = Paths.get(job.getResumePath());
                    if (Files.exists(file)) {
                        try (InputStream in = Files.newInputStream(file)) {
                            resumeText = geminiService.extractTextFromPdf(in);
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Check if final question answered
            if (session.getCurrentQuestionIndex() >= session.getMaxQuestions()) {
                // Perform final AI evaluation
                String transcript = session.getMessages().stream()
                        .map(m -> m.getSender().toUpperCase() + ": " + m.getText())
                        .collect(Collectors.joining("\n\n"));

                String evalPrompt = "You are an expert technical recruiter and interviewer. Evaluate the candidate's performance in the following mock interview transcript:\n\n"
                        + transcript + "\n\n"
                        + "Provide a comprehensive feedback report. You MUST respond STRICTLY in JSON format matching this schema:\n"
                        + "{\n"
                        + "  \"score\": <integer from 0 to 100 representing their performance score>,\n"
                        + "  \"strengths\": [<array of strings, key strengths demonstrated in their answers>],\n"
                        + "  \"improvements\": [<array of strings, concrete suggestions for improvement in their answers>],\n"
                        + "  \"sampleAnswers\": [\n"
                        + "    {\n"
                        + "      \"question\": \"<string, the question asked in the interview>\",\n"
                        + "      \"suggestedAnswer\": \"<string, optimal model answer that the candidate should have given for this question>\"\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}\n"
                        + "Ensure the output is valid JSON and contains nothing else (do not wrap in markdown ```json or include extra text).";

                String rawJsonEvaluation = geminiService.generateContent(evalPrompt);
                
                // Clean markdown wrapped blocks if Gemini accidentally includes them
                int firstBrace = rawJsonEvaluation.indexOf('{');
                int lastBrace = rawJsonEvaluation.lastIndexOf('}');
                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    rawJsonEvaluation = rawJsonEvaluation.substring(firstBrace, lastBrace + 1);
                }

                session.setEvaluationJson(rawJsonEvaluation);
                session.setCompleted(true);
            } else {
                // Ask the next question
                String transcript = session.getMessages().stream()
                        .map(m -> m.getSender().toUpperCase() + ": " + m.getText())
                        .collect(Collectors.joining("\n\n"));

                String nextPrompt = "You are a professional technical interviewer conducting a mock interview for the position of " + session.getRole() + " at " + session.getCompanyName() + ".\n"
                        + "Here is the candidate's resume (if available):\n" + resumeText + "\n\n"
                        + "Here is the job description:\n" + jobDesc + "\n\n"
                        + "Here is the transcript of the interview so far:\n" + transcript + "\n\n"
                        + "Please ask the next relevant technical or behavioral question (" + (session.getCurrentQuestionIndex() + 1) + " of 5).\n"
                        + "You can choose to follow up on their previous answer or move to a new relevant topic on the job description.\n"
                        + "Respond with ONLY the interviewer's question (do not add conversational padding like 'Here is the next question:').";

                String nextQuestion = geminiService.generateContent(nextPrompt);
                if (nextQuestion == null || nextQuestion.trim().isEmpty() || nextQuestion.startsWith("Gemini API failed")) {
                    nextQuestion = "Understood. For the next question, could you explain a challenging technical problem you solved in a past project and how you approached it?";
                }

                session.getMessages().add(new ChatMessage("interviewer", nextQuestion));
                session.setCurrentQuestionIndex(session.getCurrentQuestionIndex() + 1);
            }

            PracticeInterview updated = interviewRepository.save(session);
            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing answer: " + e.getMessage());
        }
    }

    // ✅ GET USER INTERVIEW HISTORY
    @GetMapping("/history")
    public ResponseEntity<?> getHistory(@RequestParam String userId) {
        try {
            List<PracticeInterview> list = interviewRepository.findByUserId(userId);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching history: " + e.getMessage());
        }
    }

    // ✅ GET SPECIFIC PRACTICE INTERVIEW DETAILS
    @GetMapping("/{id}")
    public ResponseEntity<?> getSession(@PathVariable String id) {
        try {
            Optional<PracticeInterview> sessionOpt = interviewRepository.findById(id);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.status(404).body("Practice session not found");
            }
            return ResponseEntity.ok(sessionOpt.get());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error retrieving session: " + e.getMessage());
        }
    }
}
