package com.example.auditlog.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
public class JwtConfiguration {

    private static final Set<String> PERMISSIONS = Set.of("AUDIT_WRITE", "AUDIT_READ", "AUDIT_VERIFY", "AUDIT_DOCS",
            "AUDIT_LIFECYCLE", "AUDIT_EXPORT", "AUDIT_COMPLIANCE");

    @Bean
    JwtDecoder jwtDecoder(@Value("${audit.jwt.public-key-base64:}") String publicKey,
            @Value("${audit.jwt.issuer}") String issuer, @Value("${audit.jwt.audience}") String audience,
            @Value("${audit.jwt.allow-unconfigured:false}") boolean allowUnconfigured) {
        if (issuer.isBlank() || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience are required");
        }
        if (publicKey.isBlank() && allowUnconfigured) {
            // Disposable H2 can start without an issuer key, but no bearer token will authenticate.
            return token -> { throw new BadJwtException("JWT verification is not configured"); };
        }
        RSAPublicKey rsa;
        try {
            rsa = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Configure a Base64 X.509 RSA public key");
        }
        if (rsa.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("JWT RSA key must be at least 2048 bits");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsa).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(issuer),
                new JwtTimestampValidator(Duration.ofSeconds(30)), requiredClaims(audience)));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> requiredClaims(String audience) {
        return jwt -> {
            try {
                ResourceScope.claims(jwt.getSubject(), jwt.getClaims());
                Object permissions = jwt.getClaims().get("permissions");
                boolean permissionShape = permissions == null || permissions instanceof List<?> list
                        && list.stream().allMatch(value -> value instanceof String);
                Instant expires = jwt.getExpiresAt();
                Instant issued = jwt.getIssuedAt();
                Instant notBefore = jwt.getNotBefore();
                if (jwt.getSubject() != null && !jwt.getSubject().isBlank() && jwt.getSubject().length() <= 255
                        && jwt.getAudience().contains(audience) && expires != null && issued != null && notBefore != null
                        && expires.isAfter(issued) && !notBefore.isAfter(expires)
                        && !issued.isAfter(Instant.now().plusSeconds(30)) && permissionShape) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (RuntimeException exception) {
                // Claim conversion failure is an invalid token, never an authorization grant.
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "JWT validation failed", null));
        };
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object claim = jwt.getClaims().get("permissions");
            if (!(claim instanceof List<?> list)) {
                return List.of();
            }
            return list.stream().filter(value -> value instanceof String && PERMISSIONS.contains(value))
                    .map(value -> new SimpleGrantedAuthority((String) value))
                    .map(authority -> (org.springframework.security.core.GrantedAuthority) authority).distinct().toList();
        });
        return converter;
    }
}
