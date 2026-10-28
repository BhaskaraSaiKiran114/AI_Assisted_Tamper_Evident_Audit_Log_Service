package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import com.example.auditlog.security.LocalSwaggerAccess;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LocalSwaggerAccessTest {

    @Test
    void defaultExceptionIsDisabledWithoutDatabaseAccess() throws Exception {
        DataSource source = mock(DataSource.class);
        assertThat(new LocalSwaggerAccess(false, "0.0.0.0", source, new MockEnvironment()).enabled()).isFalse();
        verifyNoInteractions(source);
    }

    @Test
    void explicitlyEnabledLoopbackH2IsAllowed() throws Exception {
        assertThat(new LocalSwaggerAccess(true, "127.0.0.1", source("H2"), new MockEnvironment()).enabled()).isTrue();
    }

    @Test
    void absentOrNonLoopbackBindAndPostgresProfileFailBeforeConnection() {
        DataSource source = mock(DataSource.class);
        for (String address : new String[] {"", "0.0.0.0", "localhost", "192.0.2.10"}) {
            assertThatThrownBy(() -> new LocalSwaggerAccess(true, address, source, new MockEnvironment()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var environment = new MockEnvironment();
        environment.setActiveProfiles("h2", "postgres");
        assertThatThrownBy(() -> new LocalSwaggerAccess(true, "127.0.0.1", source, environment))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(source);
    }

    @Test
    void actualDatabaseProductMustBeH2() throws Exception {
        assertThatThrownBy(() -> new LocalSwaggerAccess(true, "127.0.0.1", source("PostgreSQL"), new MockEnvironment()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DataSource source(String product) throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn(product);
        return source;
    }
}
