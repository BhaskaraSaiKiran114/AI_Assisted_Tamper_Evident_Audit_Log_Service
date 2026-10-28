package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.example.auditlog.security.JwtConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(JwtConfiguration.class)
            .withPropertyValues("audit.jwt.issuer=urn:audit-log:local", "audit.jwt.audience=audit-log-service");

    @Test
    void missingOrInvalidPersistentConfigurationFailsAndH2FallbackNeverAuthenticates() throws Exception {
        context.run(application -> assertThat(application).hasFailed());
        context.withPropertyValues("audit.jwt.public-key-base64=bad").run(application -> assertThat(application).hasFailed());
        var weak = java.security.KeyPairGenerator.getInstance("RSA");
        weak.initialize(1024);
        context.withPropertyValues("audit.jwt.public-key-base64="
                + java.util.Base64.getEncoder().encodeToString(weak.generateKeyPair().getPublic().getEncoded()))
                .run(application -> assertThat(application).hasFailed());
        context.withPropertyValues("audit.jwt.allow-unconfigured=true").run(application -> {
            assertThat(application).hasNotFailed();
            assertThatThrownBy(() -> application.getBean(JwtDecoder.class).decode(JwtTestTokens.token("AUDIT_READ")))
                    .isInstanceOf(JwtException.class);
        });
    }

    @Test
    void developmentSourceLauncherProducesATokenAcceptedByTheRealDecoder() throws Exception {
        Path directory = Path.of(".tools", "jwt-test-" + UUID.randomUUID());
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        var init = new ProcessBuilder(javaExecutable.toString(), "scripts/LocalJwt.java", "init", directory.toString())
                .redirectErrorStream(true).start();
        String diagnostics = new String(init.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(init.waitFor()).withFailMessage("Local key initialization failed: %s", diagnostics).isZero();
        var mint = new ProcessBuilder(javaExecutable.toString(), "scripts/LocalJwt.java", "token", directory.toString(), "AUDIT_READ")
                .redirectErrorStream(true).start();
        String token = new String(mint.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertThat(mint.waitFor()).isZero();
        String publicKey = Files.readString(directory.resolve("public-key-base64.txt"));
        context.withPropertyValues("audit.jwt.public-key-base64=" + publicKey).run(application -> {
            var claims = application.getBean(JwtDecoder.class).decode(token);
            assertThat(claims.getSubject()).isEqualTo("local-developer");
            assertThat(claims.getClaimAsStringList("permissions")).containsExactly("AUDIT_READ");
        });
        // Generated test credentials remain only in ignored .tools; never print them or add them to Git.
    }
}
