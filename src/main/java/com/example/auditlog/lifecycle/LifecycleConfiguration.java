package com.example.auditlog.lifecycle;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LifecycleConfiguration {

    @Bean
    Clock lifecycleClock() {
        return Clock.systemUTC();
    }
}
