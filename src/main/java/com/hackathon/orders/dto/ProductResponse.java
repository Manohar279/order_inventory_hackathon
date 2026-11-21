package com.hackathon.orders.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        int availableQuantity
) {
}
