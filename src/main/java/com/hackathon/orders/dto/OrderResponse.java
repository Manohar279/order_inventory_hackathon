package com.hackathon.orders.dto;

import com.hackathon.orders.entity.CustomerOrder;
import com.hackathon.orders.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        String message,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getMessage(),
                order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
