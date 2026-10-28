package com.example.auditlog;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class LifecycleTestClock {

    static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    @Bean
    @Primary
    Clock fixedLifecycleClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
