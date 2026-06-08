package com.example.meetingroom.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Application-wide infrastructure beans.
 *
 * <p>A {@link Clock} bean is exposed so that time-dependent logic (booking window
 * validation, lock expiry, no-show detection) reads from a single source of "now". A
 * {@link MutableClock} is used so the demo time-simulation console can shift it; injecting
 * it as {@link Clock} elsewhere keeps the rest of the code clock-agnostic, and unit tests
 * still supply their own {@code Clock.fixed(...)}.</p>
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    /** Shiftable system clock (offset 0 == real time). Satisfies any {@link Clock} injection. */
    @Bean
    public MutableClock clock() {
        return new MutableClock(ZoneId.systemDefault());
    }

    /** Allow only configured origins to call the API. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}

