package com.jobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extracts text from an input stream containing PDF data using Apache PDFBox.
     */
    public String extractTextFromPdf(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Calls the Gemini API to analyze the resume against the job description and returns a structured JSON string.
     */
    public String analyzeResume(String resumeText, String jobDescription) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return getFallbackAnalysis("Gemini API key is not configured in application.properties.");
        }

        try {
            String prompt = "You are an expert ATS (Applicant Tracking System) optimizer and technical interviewer.\n"
                    + "Analyze the following resume text against the job description.\n\n"
                    + "RESUME TEXT:\n" + resumeText + "\n\n"
                    + "JOB DESCRIPTION:\n" + jobDescription + "\n\n"
                    + "Analyze the matching and provide a response STRICTLY in JSON format matching this schema:\n"
                    + "{\n"
                    + "  \"score\": <integer from 0 to 100, representing ATS match score>,\n"
                    + "  \"matchedKeywords\": [<array of strings, key skills/keywords from the job description that are found in the resume>],\n"
                    + "  \"missingKeywords\": [<array of strings, key skills/keywords from the job description that are missing from the resume>],\n"
                    + "  \"weaknesses\": [<array of strings, specific weak parts, gaps, or flaws in the resume relative to the requirements>],\n"
                    + "  \"suggestions\": [<array of strings, concrete suggestions to improve the resume for this role>],\n"
                    + "  \"interviewQuestions\": [\n"
                    + "    {\n"
                    + "      \"question\": \"<string, tailored technical or behavioral interview question>\",\n"
                    + "      \"talkingPoints\": \"<string, guidance on how to answer this question effectively based on the resume>\"\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}\n"
                    + "Ensure the output is valid JSON and contains nothing else (do not wrap in markdown ```json or include any other text).";

            // Prepare payload
            Map<String, Object> part = Map.of("text", prompt);
            Map<String, Object> content = Map.of("parts", new Object[]{part});
            Map<String, Object> payloadMap = Map.of("contents", new Object[]{content});
            String payload = objectMapper.writeValueAsString(payloadMap);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return getFallbackAnalysis("Gemini API call failed with status code: " + response.statusCode() + ". Body: " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
            
            if (textNode.isMissingNode()) {
                return getFallbackAnalysis("Could not parse text candidate from Gemini response.");
            }

            String rawText = textNode.asText().trim();

            // Robustly extract the JSON object by finding the first '{' and last '}'
            int firstBrace = rawText.indexOf('{');
            int lastBrace = rawText.lastIndexOf('}');
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                rawText = rawText.substring(firstBrace, lastBrace + 1);
            }

            // Verify if it is valid JSON
            objectMapper.readTree(rawText);

            return rawText;

        } catch (Exception e) {
            e.printStackTrace();
            return getFallbackAnalysis("An error occurred during Gemini AI analysis: " + e.getMessage());
        }
    }

    private String getFallbackAnalysis(String errorMessage) {
        return "{\n"
                + "  \"score\": 0,\n"
                + "  \"matchedKeywords\": [],\n"
                + "  \"missingKeywords\": [],\n"
                + "  \"weaknesses\": [\"Unable to perform full analysis: " + errorMessage.replace("\"", "\\\"") + "\"],\n"
                + "  \"suggestions\": [\"Please verify your API key config and internet connection.\"],\n"
                + "  \"interviewQuestions\": [\n"
                + "    {\n"
                + "      \"question\": \"Please detail your past experience with related technologies.\",\n"
                + "      \"talkingPoints\": \"Be prepared to talk about all skills highlighted on your resume.\"\n"
                + "    }\n"
                + "  ]\n"
                + "}";
    }
}
