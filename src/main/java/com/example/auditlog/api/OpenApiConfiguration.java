package com.example.auditlog.api;

import java.util.Map;
import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI auditOpenApi() {
        return new OpenAPI().info(new Info().title("Audit Log Service").version("0.1.0")
                        .description("Append-only audit API. Explicit permissions are required; issuer identity attests the producer, "
                                + "not the truth of a producer-supplied actorId. Queries restore protected values. "
                                + "Verification checks stored commitments; intact=false is a successful integrity report."))
                .schemaRequirement("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                        .scheme("bearer").bearerFormat("JWT RS256"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    OpenApiCustomizer permissionDocumentation() {
        return api -> {
            api.getComponents().addSchemas("ApiProblem", new ObjectSchema()
                    .addProperty("type", new StringSchema().format("uri"))
                    .addProperty("title", new StringSchema()).addProperty("status", new IntegerSchema())
                    .addProperty("detail", new StringSchema()).addProperty("instance", new StringSchema().format("uri"))
                    .required(List.of("type", "title", "status", "detail")));
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            String permission = Map.of("POST /audit/events", "AUDIT_WRITE", "GET /audit/events", "AUDIT_READ",
                    "GET /audit/events/{eventId}", "AUDIT_READ", "GET /audit/verify", "AUDIT_VERIFY")
                    .get(method.name() + " " + path);
            if (permission == null) {
                throw new IllegalStateException("New API operations need an explicit documented permission");
            }
            operation.addExtension("x-required-permission", permission);
            var errors = new java.util.LinkedHashMap<>(Map.of("400", "Invalid request", "401", "Invalid or absent bearer token",
                    "403", "Missing permission", "503", "Execution, integrity or key availability failure"));
            if (path.equals("/audit/verify")) {
                errors.put("429", "Verification capacity exhausted");
            } else if (method.name().equals("POST")) {
                errors.put("413", "Request exceeds 256 KiB");
            } else if (path.equals("/audit/events")) {
                errors.put("409", "Pagination anchor unavailable");
            } else {
                errors.put("404", "Event not found");
            }
            errors.forEach((status, description) -> operation.getResponses().addApiResponse(status,
                    new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiProblem"))))));
        }));
        };
    }
}
