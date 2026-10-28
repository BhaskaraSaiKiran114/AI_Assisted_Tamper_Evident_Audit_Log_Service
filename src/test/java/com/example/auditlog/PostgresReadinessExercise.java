package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.auditlog.audit.CanonicalJson;
import com.example.auditlog.audit.EventInput;
import com.example.auditlog.audit.PayloadProtector;
import com.example.auditlog.audit.StoredJson;
import com.example.auditlog.persistence.AuditAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Opt-in real HTTP exercise against the runner's disposable PostgreSQL cluster; no latency SLO is invented. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "audit.retention.window=P1D")
@ActiveProfiles("postgres")
@Import(LifecycleTestClock.class)
class PostgresReadinessExercise {

    private static final Path OUTPUT = Path.of("target", "readiness");
    private static final String ALL = JwtTestTokens.token("AUDIT_WRITE", "AUDIT_READ", "AUDIT_VERIFY", "AUDIT_LIFECYCLE",
            "AUDIT_EXPORT", "AUDIT_COMPLIANCE", "AUDIT_DOCS");
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate runtime;
    @Autowired
    private CanonicalJson canonical;
    @Autowired
    private StoredJson json;
    @Autowired
    private AuditAppender appender;
    @Autowired
    private PayloadProtector protector;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        PostgresFixture.configure(registry, true);
        registry.add("audit.jwt.public-key-base64", JwtTestTokens::publicKey);
        registry.add("audit.export.private-key-base64", ExportTestKeys::privateKey);
    }

    @BeforeEach
    void freshSyntheticStore() throws Exception {
        Files.createDirectories(OUTPUT);
        try (var connection = PostgresFixture.admin(); var statement = connection.createStatement()) {
            for (String table : List.of("payload_redaction", "event_archive", "lifecycle_action", "payload_key", "audit_event")) {
                statement.executeUpdate("DELETE FROM " + table);
            }
            statement.executeUpdate("UPDATE chain_head SET last_sequence = 0, last_hash = '" + "0".repeat(64) + "'");
        }
        assertThat(runtime.queryForObject("SELECT current_user", String.class)).isEqualTo("audit_runtime");
    }

    @Test
    void realHttpScenarioABCAndOfflineExportWithAuditedLifecycleAndDirectTamper() throws Exception {
        request("/actuator/health", "GET", null, null, 200);
        request("/audit/events", "GET", null, null, 401);
        request("/audit/verify", "GET", null, JwtTestTokens.token("AUDIT_READ"), 403);
        var receipt = object(request("/audit/events", "POST", event("scenario-actor", "scenario-account", "SUCCESS"), ALL, 201));
        UUID id = UUID.fromString((String) receipt.get("eventId"));
        request("/audit/events", "POST", event("other-actor", "other-account", "DENIED"), ALL, 201);
        var page = object(request("/audit/events?actorId=scenario-actor&resourceType=CLIENT_ACCOUNT&resourceId=scenario-account", "GET", null, ALL, 200));
        assertThat((List<?>) page.get("events")).hasSize(1);
        request("/audit/events/" + id, "DELETE", null, ALL, 403);
        assertThatThrownBy(() -> runtime.execute("UPDATE audit_event SET actor_id = 'forged'"))
                .isInstanceOf(DataAccessException.class);
        byte[] original = runtime.queryForObject("SELECT canonical_bytes FROM audit_event WHERE event_id = ?", byte[].class, id);
        UUID operation = UUID.randomUUID();
        String redaction = text(Map.of("operationId", operation.toString(), "reason", "synthetic-readiness",
                "approvalReference", "synthetic-approved-fixture", "confirmation", "ERASE_FIELD_KEYS", "paths", List.of("/accountNumber")));
        request("/audit/events/" + id + "/redactions", "POST", redaction, ALL, 200);
        request("/audit/events/" + id + "/redactions", "POST", redaction, ALL, 200);
        String view = new String(request("/audit/events/" + id, "GET", null, ALL, 200), StandardCharsets.UTF_8);
        assertThat(view).contains("$auditRedacted").doesNotContain("synthetic-sensitive");
        assertThat(runtime.queryForObject("SELECT canonical_bytes FROM audit_event WHERE event_id = ?", byte[].class, id)).isEqualTo(original);
        UUID old = UUID.randomUUID();
        var payload = Map.<String, Object>of("accountNumber", "synthetic-old", "outcome", "SUCCESS");
        appender.append(old, Instant.parse("2020-01-01T00:00:00Z"), new EventInput("READ", "scenario-actor", "CLIENT_ACCOUNT",
                "scenario-account", payload, Instant.parse("2026-10-01T12:00:00Z")), protector.protect(old, payload));
        var retained = object(request("/audit/retention/runs", "POST", text(Map.of("operationId", UUID.randomUUID().toString(),
                "reason", "synthetic-readiness-retention")), ALL, 200));
        assertThat(retained.get("affectedCount").toString()).isEqualTo("1");
        request("/audit/events/" + old, "GET", null, ALL, 404);
        request("/audit/events/" + old + "?includeArchived=true", "GET", null, ALL, 200);
        var report = object(request("/audit/compliance/access-report?from=2026-10-01T00:00:00Z&to=2026-10-02T00:00:00Z"
                + "&accountId=scenario-account&actorId=scenario-actor&action=READ&outcome=SUCCESS", "GET", null,
                JwtTestTokens.token("AUDIT_COMPLIANCE"), 200));
        assertThat((List<?>) report.get("entries")).hasSize(1);
        assertThat(text(report)).doesNotContain("payload", "synthetic-sensitive", "$auditProtected");
        byte[] bundle = request("/audit/exports?actorId=scenario-actor&includeArchived=true", "GET", null, ALL, 200);
        Path zip = OUTPUT.resolve("scenario-export.zip");
        Path key = OUTPUT.resolve("trusted-public-key-base64.txt");
        Files.write(zip, bundle);
        Files.writeString(key, Base64.getEncoder().encodeToString(ExportTestKeys.PAIR.getPublic().getEncoded()));
        Path verifierLog = OUTPUT.resolve("scenario-verifier.log");
        var process = new ProcessBuilder(java(), "-jar", Path.of("target/audit-log-service-0.1.0-SNAPSHOT-verifier.jar").toAbsolutePath().toString(),
                zip.toAbsolutePath().toString(), key.toAbsolutePath().toString()).redirectErrorStream(true).redirectOutput(verifierLog.toFile()).start();
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            assertThat(Files.readString(verifierLog)).contains("VERIFIED selected=2 head=5");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        assertThat(object(request("/audit/verify", "GET", null, ALL, 200)).get("intact")).isEqualTo(true);
        try (var connection = PostgresFixture.admin(); var statement = connection.prepareStatement("UPDATE audit_event SET actor_id = ? WHERE event_id = ?")) {
            statement.setString(1, "forged-fixture");
            statement.setObject(2, id);
            statement.executeUpdate();
        }
        var broken = object(request("/audit/verify", "GET", null, ALL, 200));
        assertThat(broken.get("intact")).isEqualTo(false);
        assertThat(((Map<?, ?>) broken.get("inconsistency")).get("type")).isEqualTo("INDEX_FIELD_MISMATCH");
        request("/audit/exports?actorId=scenario-actor", "GET", null, ALL, 503);
        request("/v3/api-docs", "GET", null, JwtTestTokens.token("AUDIT_DOCS"), 200);
        Files.writeString(OUTPUT.resolve("scenario-result.json"), text(Map.of("status", "passed", "databaseRole", "audit_runtime",
                "transport", "loopback HTTP", "scenarios", List.of("A ingestion/query/tamper", "B lifecycle/signed offline export", "C metadata report"))));
    }

    @Test
    void measureRealHttpBaselineWithoutInventedLatencyAcceptanceThreshold() throws Exception {
        int warmup = 50;
        for (int index = 0; index < warmup; index++) {
            request("/audit/events", "POST", event("warmup", "warmup", "SUCCESS"), ALL, 201);
        }
        freshSyntheticStore();
        int count = 1000;
        int concurrency = 4;
        String payload = event("benchmark-actor", "benchmark-account", "SUCCESS");
        List<Callable<Long>> work = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            work.add(() -> {
                long start = System.nanoTime();
                request("/audit/events", "POST", payload, ALL, 201);
                return System.nanoTime() - start;
            });
        }
        long start = System.nanoTime();
        double[] milliseconds = new double[count];
        try (var pool = Executors.newFixedThreadPool(concurrency)) {
            var results = pool.invokeAll(work, 2, TimeUnit.MINUTES);
            for (int index = 0; index < count; index++) {
                assertThat(results.get(index).isCancelled()).isFalse();
                milliseconds[index] = results.get(index).get() / 1_000_000.0;
            }
        }
        double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
        Arrays.sort(milliseconds);
        assertThat(runtime.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(count);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("measuredAtUtc", Instant.now().toString());
        metrics.put("javaVersion", System.getProperty("java.version"));
        metrics.put("springBootVersion", org.springframework.boot.SpringBootVersion.getVersion());
        metrics.put("springFrameworkVersion", org.springframework.core.SpringVersion.getVersion());
        metrics.put("springSecurityVersion", org.springframework.security.core.SpringSecurityCoreVersion.getVersion());
        metrics.put("tomcatVersion", org.apache.catalina.util.ServerInfo.getServerNumber());
        try (var connection = runtime.getDataSource().getConnection()) {
            metrics.put("jdbcDriverVersion", connection.getMetaData().getDriverVersion());
        }
        metrics.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        metrics.put("logicalProcessors", Runtime.getRuntime().availableProcessors());
        metrics.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        var system = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        metrics.put("physicalMemoryBytes", system.getTotalMemorySize());
        metrics.put("postgresVersion", runtime.queryForObject("SHOW server_version", String.class));
        metrics.put("databaseRole", "audit_runtime");
        metrics.put("transport", "loopback HTTP/1.1; client and server in same JVM/host; no TLS");
        metrics.put("payloadUtf8Bytes", payload.getBytes(StandardCharsets.UTF_8).length);
        metrics.put("protectedFieldsPerEvent", 1);
        metrics.put("warmupRequests", warmup);
        metrics.put("measuredAppendRequests", count);
        metrics.put("appendConcurrency", concurrency);
        metrics.put("appendElapsedSeconds", decimal(elapsed));
        metrics.put("appendThroughputPerSecond", decimal(count / elapsed));
        metrics.put("appendP50Ms", decimal(percentile(milliseconds, 0.50)));
        metrics.put("appendP95Ms", decimal(percentile(milliseconds, 0.95)));
        metrics.put("appendP99Ms", decimal(percentile(milliseconds, 0.99)));
        metrics.put("appendMaxMs", decimal(milliseconds[count - 1]));
        metrics.put("queryPage50", timings("/audit/events?actorId=benchmark-actor&limit=50", 10));
        metrics.put("fullVerify1000", timings("/audit/verify", 5));
        metrics.put("reportPage50", timings("/audit/compliance/access-report?from=2026-10-01T00:00:00Z&to=2026-10-02T00:00:00Z"
                + "&accountId=benchmark-account&limit=50", 10));
        metrics.put("protectedExport1000", timings("/audit/exports?actorId=benchmark-actor", 3));
        metrics.put("relationBytes", runtime.queryForObject("SELECT pg_total_relation_size('audit_event')", Long.class));
        for (int index = 0; index < 100; index++) {
            UUID aged = UUID.randomUUID();
            var agedPayload = Map.<String, Object>of("outcome", "SUCCESS", "accountNumber", "synthetic-aged");
            appender.append(aged, Instant.parse("2020-01-01T00:00:00Z"), new EventInput("READ", "benchmark-aged",
                    "CLIENT_ACCOUNT", "benchmark-aged-account", agedPayload, Instant.parse("2026-10-01T12:00:00Z")),
                    protector.protect(aged, agedPayload));
        }
        UUID first = runtime.queryForObject("SELECT event_id FROM audit_event WHERE event_sequence = 1", UUID.class);
        long redactStart = System.nanoTime();
        request("/audit/events/" + first + "/redactions", "POST", text(Map.of("operationId", UUID.randomUUID().toString(),
                "reason", "synthetic-fanout-measurement", "approvalReference", "synthetic-approved", "confirmation", "ERASE_FIELD_KEYS",
                "paths", List.of("/accountNumber"))), ALL, 200);
        metrics.put("redactOneFieldMs", decimal((System.nanoTime() - redactStart) / 1_000_000.0));
        long retainStart = System.nanoTime();
        var retention = object(request("/audit/retention/runs", "POST", text(Map.of("operationId", UUID.randomUUID().toString(),
                "reason", "synthetic-fanout-measurement")), ALL, 200));
        metrics.put("archiveBatch100Ms", decimal((System.nanoTime() - retainStart) / 1_000_000.0));
        assertThat(retention.get("affectedCount").toString()).isEqualTo("100");
        metrics.put("archivedFixtureCount", 100);
        metrics.put("postLifecycleChainCount", 1102);
        metrics.put("archivedPage50Batch100", timings("/audit/events?actorId=benchmark-aged&includeArchived=true&limit=50", 3));
        metrics.put("postLifecycleVerify1102", timings("/audit/verify", 3));
        metrics.put("postLifecycleExport1000", timings("/audit/exports?actorId=benchmark-actor", 3));
        metrics.put("acceptance", "All HTTP statuses/counts/integrity checked; latency measurements are a baseline, not an SLO pass");
        assertThat(object(request("/audit/verify", "GET", null, ALL, 200)).get("intact")).isEqualTo(true);
        Files.writeString(OUTPUT.resolve("performance.json"), text(metrics));
    }

    @Test
    void freshlyPackagedApplicationStartsAndMigratesDisposableH2OverRealHttp() throws Exception {
        Path log = OUTPUT.resolve("packaged-application.log");
        var builder = new ProcessBuilder(java(), "-jar", Path.of("target/audit-log-service-0.1.0-SNAPSHOT.jar").toAbsolutePath().toString(),
                "--server.port=0", "--server.address=127.0.0.1", "--spring.profiles.active=h2",
                "--spring.datasource.url=jdbc:h2:mem:packagedreadiness;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
                .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("AUDIT_JWT_PUBLIC_KEY_BASE64", JwtTestTokens.publicKey());
        builder.environment().remove("AUDIT_EXPORT_PRIVATE_KEY_BASE64");
        var process = builder.start();
        try {
            int actualPort = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
            while (process.isAlive() && System.nanoTime() < deadline) {
                var found = java.util.regex.Pattern.compile("Tomcat started on port (\\d+)").matcher(Files.readString(log));
                if (found.find()) {
                    actualPort = Integer.parseInt(found.group(1));
                    break;
                }
                Thread.sleep(100);
            }
            assertThat(actualPort).as("Packaged app starts on a dynamic loopback port").isPositive();
            int previousPort = port;
            try {
                port = actualPort;
                request("/actuator/health", "GET", null, null, 200);
                request("/audit/events", "GET", null, null, 401);
                request("/audit/events", "POST", event("packaged", "packaged", "SUCCESS"), ALL, 201);
                assertThat(object(request("/audit/verify", "GET", null, ALL, 200)).get("intact")).isEqualTo(true);
                request("/audit/exports?actorId=packaged", "GET", null, ALL, 503); // No signing fallback.
            } finally {
                port = previousPort;
            }
            Files.writeString(OUTPUT.resolve("packaged-start-result.json"), text(Map.of("status", "passed", "profile", "fresh H2",
                    "artifact", "executable Boot JAR", "transport", "dynamic loopback HTTP")));
        } finally {
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private Map<String, Object> timings(String route, int repetitions) throws Exception {
        double[] times = new double[repetitions];
        for (int index = 0; index < repetitions; index++) {
            long start = System.nanoTime();
            byte[] result = request(route, "GET", null, ALL, 200);
            if (route.equals("/audit/verify")) {
                assertThat(object(result).get("intact")).isEqualTo(true);
            }
            times[index] = (System.nanoTime() - start) / 1_000_000.0;
        }
        Arrays.sort(times);
        return Map.of("repetitions", repetitions, "concurrency", 1, "p50Ms", decimal(percentile(times, 0.5)),
                "p95Ms", decimal(percentile(times, 0.95)), "maxMs", decimal(times[times.length - 1]));
    }

    private byte[] request(String path, String method, String body, String token, int status) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).as(method + " " + path).isEqualTo(status);
        return response.body();
    }

    private String event(String actor, String account, String outcome) {
        return text(Map.of("eventType", "READ", "actorId", actor, "resourceType", "CLIENT_ACCOUNT", "resourceId", account,
                "timestamp", "2026-10-01T12:00:00Z", "payload", Map.of("outcome", outcome, "accountNumber", "synthetic-sensitive")));
    }

    private Map<String, Object> object(byte[] bytes) {
        return json.object(bytes);
    }

    private String text(Object value) {
        return new String(canonical.bytes(value), StandardCharsets.UTF_8);
    }

    private java.math.BigDecimal decimal(double value) {
        return java.math.BigDecimal.valueOf(value).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private double percentile(double[] sorted, double fraction) {
        return sorted[Math.max(0, (int) Math.ceil(fraction * sorted.length) - 1)];
    }

    private String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
