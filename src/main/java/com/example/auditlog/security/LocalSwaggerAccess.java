package com.example.auditlog.security;

import java.sql.SQLException;
import java.util.Arrays;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Explicit development exception for documentation only; fail closed outside loopback H2. */
@Component
public class LocalSwaggerAccess {

    private final boolean enabled;

    public LocalSwaggerAccess(@Value("${audit.swagger.local-public:false}") boolean enabled,
            @Value("${server.address:}") String address, DataSource source, Environment environment) throws SQLException {
        this.enabled = enabled;
        if (enabled) {
            if (!"127.0.0.1".equals(address) || Arrays.asList(environment.getActiveProfiles()).contains("postgres")) {
                throw new IllegalArgumentException("Public local Swagger requires 127.0.0.1 and H2 without the postgres profile");
            }
            try (var connection = source.getConnection()) {
                if (!"H2".equals(connection.getMetaData().getDatabaseProductName())) {
                    throw new IllegalArgumentException("Public local Swagger requires an H2 datasource");
                }
            }
        }
    }

    public boolean enabled() {
        return enabled;
    }
}
