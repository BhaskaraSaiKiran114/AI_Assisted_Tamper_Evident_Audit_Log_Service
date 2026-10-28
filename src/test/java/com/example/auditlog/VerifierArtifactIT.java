package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifierArtifactIT {

    @TempDir
    private Path directory;

    @Test
    void packagedToolRunsWithoutServerOrThirdPartyLibrariesAndFailsOnWrongKey() throws Exception {
        Path jar = Path.of("target/audit-log-service-0.1.0-SNAPSHOT-verifier.jar").toAbsolutePath();
        try (JarFile artifact = new JarFile(jar.toFile())) {
            var classes = artifact.stream().map(java.util.jar.JarEntry::getName).filter(name -> name.endsWith(".class")).toList();
            assertThat(classes).isNotEmpty().allMatch(name -> name.startsWith("com/example/auditlog/export/ExportBundle"));
        }
        Path bundle = directory.resolve("bundle.zip");
        Path key = directory.resolve("trusted.txt");
        Files.write(bundle, ExportBundleTest.fixture());
        Files.writeString(key, Base64.getEncoder().encodeToString(ExportTestKeys.PAIR.getPublic().getEncoded()));
        assertThat(run(jar, bundle, key)).isZero();
        Files.writeString(key, Base64.getEncoder().encodeToString(ExportTestKeys.pair().getPublic().getEncoded()));
        assertThat(run(jar, bundle, key)).isEqualTo(1);
        Files.write(bundle, new byte[] {1, 2, 3});
        assertThat(run(jar, bundle, key)).isEqualTo(1);
        Files.write(bundle, ExportBundleTest.fixture());
        Files.writeString(key, "A".repeat(16385));
        assertThat(run(jar, bundle, key)).isEqualTo(1);
        Files.writeString(key, Base64.getEncoder().encodeToString(ExportTestKeys.PAIR.getPublic().getEncoded()));
        Files.write(bundle, new byte[com.example.auditlog.export.ExportBundle.MAX_BODY + 1024 * 1024 + 1]);
        assertThat(run(jar, bundle, key)).isEqualTo(1);
    }

    private int run(Path jar, Path bundle, Path key) throws Exception {
        Path log = directory.resolve("tool-output.txt");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", jar.toString(), bundle.toString(), key.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Verifier process timeout");
        }
        if (process.exitValue() == 0) {
            assertThat(Files.readString(log)).contains("VERIFIED selected=1 head=1");
        } else {
            assertThat(Files.readString(log)).contains("INVALID:").doesNotContain("Exception", "PKCS8");
        }
        return process.exitValue();
    }
}
