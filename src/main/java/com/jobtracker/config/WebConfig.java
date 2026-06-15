//package com.jobtracker.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.servlet.config.annotation.*;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//
//@Configuration
//public class WebConfig {
//
//    // 🔥 PASSWORD ENCODER BEAN (ADD THIS)
//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder();
//    }
//
//    // 🔥 CORS CONFIG
//    @Bean
//    public WebMvcConfigurer corsConfigurer() {
//        return new WebMvcConfigurer() {
//
//            @Override
//            public void addCorsMappings(CorsRegistry registry) {
//                registry.addMapping("/**")
//
//                        .allowedOrigins("http://localhost:3000", "http://localhost:3001")
//
//                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
//
//                        .allowedHeaders("*")
//
//                        .allowCredentials(true)
//
//                        .maxAge(3600);
//            }
//        };
//    }
//}