package org.example.testtaskidf.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** Defines the title and version of the generated OpenAPI specification. */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(title = "Bank Transactions API", version = "v1",
        description = "Bank API receives transactions; client API establishes account expense limits in USD. "
                + "Unknown source accounts are registered automatically. Default limit: 1000 USD per category. "
                + "Limit timestamps are assigned by the server and returned at UTC+03:00. "
                + "Errors use RFC 9457 application/problem+json; validation errors contain errors, "
                + "limit conflicts contain code. USD conversion uses cached OER daily values or remains PENDING. "
                + "Exceeded-limit queries are not implemented."))
public class OpenApiConfiguration {
}
