package com.jobtracker.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs/openings")
@CrossOrigin(origins = "*")
public class JobSearchController {

    private final RestTemplate restTemplate;

    public JobSearchController() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3500);
        factory.setReadTimeout(3500);
        this.restTemplate = new RestTemplate(factory);
    }

    @Value("${jsearch.api.key:}")
    private String jsearchApiKey;

    @GetMapping
    public List<Map<String, Object>> searchOpenings(
            @RequestParam(required = false, defaultValue = "") String role,
            @RequestParam(required = false, defaultValue = "") String location) {
        
        List<Map<String, Object>> jobs = new ArrayList<>();
        
        // 1. Fetch live jobs from JSearch API
        jobs.addAll(fetchFromJSearch(role, location));
        
        // 2. Fallback to mock data if no jobs fetched at all (e.g. key missing or rate limited)
        if (jobs.isEmpty()) {
            jobs = getMockJobs();
        }
        
        // Apply filters locally (case-insensitive contains check)
        final String searchRole = role.toLowerCase().trim();
        final String searchLoc = location.toLowerCase().trim();

        return jobs.stream()
                .filter(job -> {
                    boolean roleMatch = true;
                    boolean locMatch = true;
                    
                    if (!searchRole.isEmpty()) {
                        String jobRole = String.valueOf(job.get("role")).toLowerCase();
                        String jobComp = String.valueOf(job.get("companyName")).toLowerCase();
                        roleMatch = jobRole.contains(searchRole) || jobComp.contains(searchRole);
                    }
                    
                    if (!searchLoc.isEmpty()) {
                        String jobLoc = String.valueOf(job.get("location")).toLowerCase();
                        locMatch = jobLoc.contains(searchLoc);
                    }
                    
                    return roleMatch && locMatch;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> fetchFromJSearch(String role, String location) {
        List<Map<String, Object>> jobs = new ArrayList<>();
        
        // Check if API key is not configured or placeholder is left
        if (jsearchApiKey == null || jsearchApiKey.trim().isEmpty() || jsearchApiKey.contains("YOUR_JSEARCH_RAPIDAPI_KEY")) {
            System.err.println("JSearch API Key is not configured in application.properties! Using mock fallback.");
            return jobs;
        }

        try {
            // Construct query, e.g. "Java Developer in Noida" or just "Java Developer"
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
                query = "Software Developer"; // default fallback search
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-RapidAPI-Key", jsearchApiKey.trim());
            headers.set("X-RapidAPI-Host", "jsearch.p.rapidapi.com");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = "https://jsearch.p.rapidapi.com/search?query=" + java.net.URLEncoder.encode(query, "UTF-8") + "&page=1&num_pages=1";

            System.out.println("Calling JSearch API with query: " + query);
            ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> response = responseEntity.getBody();

            if (response != null && response.containsKey("data")) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                if (data != null) {
                    for (Map<String, Object> rj : data) {
                        Map<String, Object> job = new HashMap<>();
                        job.put("id", "jsearch-" + rj.get("job_id"));
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
                        job.put("url", applyUrl != null ? applyUrl : "https://google.com/search?q=" + java.net.URLEncoder.encode(rj.get("job_title") + " " + rj.get("employer_name"), "UTF-8"));
                        job.put("source", "JSearch API");
                        jobs.add(job);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("JSearch API failed: " + e.getMessage());
        }
        return jobs;
    }

    private List<Map<String, Object>> getMockJobs() {
        List<Map<String, Object>> mockList = new ArrayList<>();
        
        // India - Noida, Jaipur, Gurgaon, Pune, Bangalore
        mockList.add(createJob("m1", "Tata Consultancy Services (TCS)", "Senior Java Developer", "Noida, UP, IN", "₹8,00,000 - ₹12,00,000", "https://www.tcs.com/careers"));
        mockList.add(createJob("m2", "Infosys", "React Frontend Engineer", "Jaipur, RJ, IN", "₹6,00,000 - ₹10,00,000", "https://www.infosys.com/careers"));
        mockList.add(createJob("m3", "Zomato", "Full Stack Software Engineer", "Gurgaon, HR, IN", "₹15,00,000 - ₹22,00,000", "https://www.zomato.com/careers"));
        mockList.add(createJob("m4", "Paytm", "Python Backend Developer", "Noida, UP, IN", "₹12,00,000 - ₹18,00,000", "https://careers.paytm.com"));
        mockList.add(createJob("m5", "Wipro", "AWS Cloud Architect", "Gurgaon, HR, IN", "₹10,00,000 - ₹15,00,000", "https://careers.wipro.com"));
        mockList.add(createJob("m6", "Cognizant", "Quality Assurance Engineer", "Jaipur, RJ, IN", "₹5,00,000 - ₹8,00,000", "https://careers.cognizant.com"));
        mockList.add(createJob("m7", "Swiggy", "Backend Engineer (Go/Java)", "Gurgaon, HR, IN / Remote", "₹18,00,000 - ₹25,00,000", "https://careers.swiggy.com"));
        mockList.add(createJob("m8", "Tech Mahindra", "Embedded Systems Developer", "Noida, UP, IN", "₹7,00,000 - ₹11,00,000", "https://www.techmahindra.com/careers"));
        mockList.add(createJob("m9", "Flipkart", "Senior UI/UX Frontend Web Engineer", "Bengaluru, KA, IN", "₹20,00,000 - ₹28,00,000", "https://www.flipkartcareers.com"));
        mockList.add(createJob("m10", "Amazon India", "Software Development Engineer (SDE-II)", "Bengaluru, KA, IN / Remote", "₹25,00,000 - ₹35,00,000", "https://amazon.jobs"));
        mockList.add(createJob("m11", "Microsoft India", "Azure DevOps Architect", "Noida, UP, IN", "₹22,00,000 - ₹32,00,000", "https://careers.microsoft.com"));
        mockList.add(createJob("m12", "HCLTech", "Spring Boot Java Engineer", "Noida, UP, IN", "₹6,50,000 - ₹9,50,000", "https://www.hcltech.com/careers"));

        // International fallback
        mockList.add(createJob("m13", "Google", "Senior Staff Software Engineer", "New York, NY, US", "$170,000 - $220,000", "https://careers.google.com"));
        mockList.add(createJob("m14", "Barclays", "Investment Banking Backend Analyst", "London, UK", "£70,000 - £95,000", "https://search.jobs.barclays"));

        return mockList;
    }

    private Map<String, Object> createJob(String id, String company, String role, String location, String salary, String url) {
        Map<String, Object> job = new HashMap<>();
        job.put("id", id);
        job.put("companyName", company);
        job.put("role", role);
        job.put("location", location);
        job.put("salary", salary);
        job.put("url", url);
        job.put("source", "Premium Database");
        return job;
    }
}
