package com.example.auditlog.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Database-specific read snapshots without changing the append transaction's isolation. */
@Component
public class ReadSnapshot {

    private final PlatformTransactionManager manager;
    private final int isolation;

    public ReadSnapshot(PlatformTransactionManager manager, DataSource source) throws SQLException {
        this.manager = manager;
        try (Connection connection = source.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            isolation = switch (product) {
                case "PostgreSQL" -> TransactionDefinition.ISOLATION_REPEATABLE_READ;
                case "H2" -> TransactionDefinition.ISOLATION_SERIALIZABLE;
                default -> throw new IllegalStateException("Unsupported snapshot database");
            };
        }
    }

    public <T> T execute(int timeoutSeconds, Supplier<T> operation) {
        TransactionTemplate transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(isolation);
        transaction.setTimeout(timeoutSeconds);
        // Never silently join a caller's weaker isolation or mix an uncommitted append with verification.
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> operation.get());
    }
}
