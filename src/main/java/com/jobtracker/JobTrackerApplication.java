package com.jobtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JobTrackerApplication {

	public static void main(String[] args) {
		try {
			java.io.File envFile = new java.io.File(".env");
			if (envFile.exists()) {
				java.nio.file.Files.lines(envFile.toPath())
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.forEach(line -> {
						int idx = line.indexOf('=');
						if (idx > 0) {
							String key = line.substring(0, idx).trim();
							String value = line.substring(idx + 1).trim();
							if (value.startsWith("\"") && value.endsWith("\"")) {
								value = value.substring(1, value.length() - 1);
							}
							if (System.getProperty(key) == null && System.getenv(key) == null) {
								System.setProperty(key, value);
							}
						}
					});
			}
		} catch (Exception e) {
			System.err.println("Failed to load .env file: " + e.getMessage());
		}
		SpringApplication.run(JobTrackerApplication.class, args);
	}

}
