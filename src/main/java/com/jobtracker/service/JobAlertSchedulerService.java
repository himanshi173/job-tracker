package com.jobtracker.service;

import com.jobtracker.entity.JobAlert;
import com.jobtracker.repository.JobAlertRepository;
import com.jobtracker.auth.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobAlertSchedulerService {

    @Autowired
    private JobAlertRepository alertRepository;

    @Autowired
    private MailService mailService;

    @Value("${jsearch.api.key:}")
    private String jsearchApiKey;

    private final RestTemplate restTemplate;

    public JobAlertSchedulerService() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3500);
        factory.setReadTimeout(3500);
        this.restTemplate = new RestTemplate(factory);
    }

    // Run every day at 8:00 AM
    @Scheduled(cron = "0 0 8 * * ?")
    public void processAllAlerts() {
        System.out.println("[Scheduler] Starting daily job search alerts check...");
        List<JobAlert> alerts = alertRepository.findAll();
        
        int digestSent = 0;
        for (JobAlert alert : alerts) {
            User user = alert.getUser();
            if (user == null || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                continue;
            }

            List<Map<String, Object>> jobs = fetchJobsForAlert(alert.getRole(), alert.getLocation());
            if (jobs != null && !jobs.isEmpty()) {
                // Compile matching jobs into readable list
                StringBuilder jobsBuilder = new StringBuilder();
                int count = 1;
                for (Map<String, Object> job : jobs) {
                    jobsBuilder.append(count).append(". ").append(job.get("role"))
                            .append(" at ").append(job.get("companyName"))
                            .append(" (").append(job.get("location")).append(")\n")
                            .append("   Salary: ").append(job.get("salary")).append("\n")
                            .append("   Apply Link: ").append(job.get("url")).append("\n\n");
                    count++;
                }

                mailService.sendJobAlertDigest(
                        user.getEmail(),
                        user.getUsername(),
                        alert.getRole(),
                        alert.getLocation(),
                        jobsBuilder.toString()
                );
                digestSent++;
            }
        }
        System.out.println("[Scheduler] Job search alerts processed. Digests sent: " + digestSent);
    }

    private List<Map<String, Object>> fetchJobsForAlert(String role, String location) {
        List<Map<String, Object>> jobs = new ArrayList<>();
        if (jsearchApiKey == null || jsearchApiKey.trim().isEmpty() || jsearchApiKey.contains("YOUR_JSEARCH_RAPIDAPI_KEY")) {
            return jobs;
        }

        try {
            String queryStr = (role != null ? role.trim() : "").trim();
            String locStr = (location != null ? location.trim() : "").trim();
            
            String query = queryStr;
            if (!locStr.isEmpty()) {
                if (query.isEmpty()) {
                    query = "jobs in " + locStr;
                } else {
                    query = query + " in " + locStr;
                }
            }
            if (query.isEmpty()) {
                query = "Software Developer";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-RapidAPI-Key", jsearchApiKey.trim());
            headers.set("X-RapidAPI-Host", "jsearch.p.rapidapi.com");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = "https://jsearch.p.rapidapi.com/search?query=" + URLEncoder.encode(query, "UTF-8") + "&page=1&num_pages=1";

            ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> response = responseEntity.getBody();

            if (response != null && response.containsKey("data")) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                if (data != null) {
                    // Get top 3 jobs to keep digest email concise
                    int limit = Math.min(data.size(), 3);
                    for (int i = 0; i < limit; i++) {
                        Map<String, Object> rj = data.get(i);
                        Map<String, Object> job = new HashMap<>();
                        job.put("companyName", rj.get("employer_name"));
                        job.put("role", rj.get("job_title"));
                        
                        String city = (String) rj.get("job_city");
                        String state = (String) rj.get("job_state");
                        String country = (String) rj.get("job_country");
                        List<String> locParts = new ArrayList<>();
                        if (city != null && !city.equalsIgnoreCase("null")) locParts.add(city);
                        if (state != null && !state.equalsIgnoreCase("null")) locParts.add(state);
                        if (country != null && !country.equalsIgnoreCase("null")) locParts.add(country);
                        String jobLoc = locParts.isEmpty() ? "Remote" : String.join(", ", locParts);
                        job.put("location", jobLoc);

                        Object minSal = rj.get("job_min_salary");
                        Object maxSal = rj.get("job_max_salary");
                        String salary = "Not Specified";
                        if (minSal != null && maxSal != null && !String.valueOf(minSal).isEmpty() && !String.valueOf(minSal).equals("null")) {
                            Object currency = rj.get("job_salary_currency");
                            String currSymbol = currency != null ? String.valueOf(currency) : "$";
                            salary = currSymbol + String.valueOf(minSal) + " - " + String.valueOf(maxSal);
                        }
                        job.put("salary", salary);
                        
                        String applyUrl = (String) rj.get("job_apply_link");
                        if (applyUrl == null || applyUrl.isEmpty()) {
                            applyUrl = (String) rj.get("job_google_link");
                        }
                        job.put("url", applyUrl != null ? applyUrl : "https://google.com");
                        jobs.add(job);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("JSearch query failed in alert scheduler: " + e.getMessage());
        }
        return jobs;
    }
}
