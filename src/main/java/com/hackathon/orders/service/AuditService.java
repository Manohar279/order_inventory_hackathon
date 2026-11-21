package com.hackathon.orders.service;

import com.hackathon.orders.dto.AuditLogResponse;
import com.hackathon.orders.entity.AuditLog;
import com.hackathon.orders.exception.OrderNotFoundException;
import com.hackathon.orders.repository.AuditLogRepository;
import com.hackathon.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {

    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_REJECTED = "ORDER_REJECTED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    private final AuditLogRepository auditLogRepository;
    private final OrderRepository orderRepository;

    public AuditService(AuditLogRepository auditLogRepository, OrderRepository orderRepository) {
        this.auditLogRepository = auditLogRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * MANDATORY: must run inside the caller's transaction, so the audit entry commits
     * (or rolls back) together with the order and the stock change.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long orderId, String action, String message) {
        auditLogRepository.save(new AuditLog(orderId, action, message));
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getHistory(Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException(orderId);
        }
        return auditLogRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
