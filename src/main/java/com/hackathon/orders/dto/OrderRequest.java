package com.hackathon.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;


public record OrderRequest(
        @NotBlank(message = "customerId is required")
        String customerId,

        @NotEmpty(message = "items must contain at least one product")
        List<@NotNull(message = "items must not contain null entries") @Valid OrderItemRequest> items,

        @Size(max = 100, message = "requestId must be at most 100 characters")
        String requestId
) {
}
