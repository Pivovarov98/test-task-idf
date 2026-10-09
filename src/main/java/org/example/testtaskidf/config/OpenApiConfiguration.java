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
                + "limit conflicts contain code. USD conversion uses cached OER daily values or the last available close, or remains PENDING. Rate dates use UTC; accounting months use Europe/Moscow (UTC+03:00). Operations reserve fixed USD amounts before bank completion. Final bank notifications retain or release reservations; three hours of continuous polling errors cause TIMED_OUT. Late success after timeout restores the original expense. The local bank stub is configurable. "
                + "Exceeded-limit queries are not implemented."))
public class OpenApiConfiguration {
}

