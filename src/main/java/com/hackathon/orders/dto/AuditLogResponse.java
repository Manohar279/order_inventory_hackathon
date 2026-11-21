package com.hackathon.orders.dto;

import com.hackathon.orders.entity.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long orderId,
        String action,
        String message,
        LocalDateTime timestamp
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getOrderId(), log.getAction(), log.getMessage(), log.getCreatedAt());
    }
}
