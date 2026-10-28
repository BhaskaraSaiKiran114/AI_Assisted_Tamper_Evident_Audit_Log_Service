import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.List;
import java.util.EnumSet;

/** Development-only source launcher: java scripts/LocalJwt.java init|token .tools/jwt [permissions]. */
class LocalJwt {
    private static final Set<String> PERMISSIONS = Set.of("AUDIT_WRITE", "AUDIT_READ", "AUDIT_VERIFY", "AUDIT_DOCS",
            "AUDIT_LIFECYCLE", "AUDIT_EXPORT", "AUDIT_COMPLIANCE");

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 2) {
            throw new IllegalArgumentException("Usage: init|token .tools/jwt [comma-separated permissions]");
        }
        Path directory = Path.of(arguments[1]).toAbsolutePath().normalize();
        Path allowed = Path.of(".tools").toAbsolutePath().normalize();
        if (!directory.startsWith(allowed) || directory.equals(allowed)) {
            throw new IllegalArgumentException("Run from project root and select a directory inside ignored .tools");
        }
        if (arguments[0].equals("init")) {
            Files.createDirectories(directory);
            Path privateFile = directory.resolve("private-key-base64.txt");
            Path publicFile = directory.resolve("public-key-base64.txt");
            if (Files.exists(privateFile) || Files.exists(publicFile)) {
                throw new IllegalArgumentException("Refusing to overwrite existing developer keys");
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keys = generator.generateKeyPair();
            if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
                Files.createFile(privateFile, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } else {
                Files.createFile(privateFile);
                var access = Files.getFileAttributeView(privateFile, AclFileAttributeView.class);
                if (access == null) {
                    throw new IllegalStateException("Private key creation requires owner-only filesystem permissions");
                }
                access.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                        .setPrincipal(Files.getOwner(privateFile))
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
            }
            Files.writeString(privateFile, Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()),
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(publicFile, Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()),
                    StandardOpenOption.CREATE_NEW);
            System.out.println("Developer keys created under ignored .tools; restrict access and keep the private key local.");
        } else if (arguments[0].equals("token") && arguments.length == 3) {
            var permissions = Arrays.stream(arguments[2].split(",", -1)).toList();
            if (permissions.isEmpty() || !PERMISSIONS.containsAll(permissions)) {
                throw new IllegalArgumentException("Only documented AUDIT_* permissions are allowed");
            }
            var privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder()
                    .decode(Files.readString(directory.resolve("private-key-base64.txt")).trim())));
            long now = Instant.now().getEpochSecond();
            String claims = "{\"iss\":\"urn:audit-log:local\",\"aud\":[\"audit-log-service\"],\"sub\":\"local-developer\","
                    + "\"iat\":" + now + ",\"nbf\":" + now + ",\"exp\":" + (now + 300) + ",\"permissions\":["
                    + String.join(",", permissions.stream().map(p -> "\"" + p + "\"").toList()) + "]}";
            String unsigned = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}") + "." + encode(claims);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            System.out.println(unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        } else {
            throw new IllegalArgumentException("Usage: init|token .tools/jwt [comma-separated permissions]");
        }
    }

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
