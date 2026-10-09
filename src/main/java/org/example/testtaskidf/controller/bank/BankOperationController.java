package org.example.testtaskidf.controller.bank;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.testtaskidf.dto.BankNotificationRequest;
import org.example.testtaskidf.dto.BankStubRequest;
import org.example.testtaskidf.dto.OperationState;
import org.example.testtaskidf.service.OperationLifecycleService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bank")
@Tag(name = "Operation lifecycle")
public class BankOperationController {
    private final OperationLifecycleService lifecycle;

    public BankOperationController(OperationLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @PostMapping("/notifications")
    @Operation(summary = "Notify final bank status; duplicates are idempotent")
    public OperationState notify(@Valid @RequestBody BankNotificationRequest request) {
        return lifecycle.notify(request);
    }

    @PutMapping("/stub/transactions/{id}")
    @Operation(summary = "Configure the local bank reply: PROCESSING, SUCCEEDED, FAILED or ERROR")
    public OperationState stub(@PathVariable UUID id, @Valid @RequestBody BankStubRequest request) {
        return lifecycle.configureStub(id, request.status());
    }

    @GetMapping("/transactions/{id}/status")
    @Operation(summary = "Inspect one operation state for bank integration and local diagnostics")
    public OperationState state(@PathVariable UUID id) {
        return lifecycle.state(id);
    }
}
