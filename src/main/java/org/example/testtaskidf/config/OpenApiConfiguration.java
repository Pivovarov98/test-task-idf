package org.example.testtaskidf.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** Defines the title and version of the generated OpenAPI specification. */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(title = "Bank Transactions API", version = "v1",
        description = "API for receiving bank transactions. Errors use application/problem+json; "
                + "validation errors additionally contain an errors object keyed by input field names."))
public class OpenApiConfiguration {
}
