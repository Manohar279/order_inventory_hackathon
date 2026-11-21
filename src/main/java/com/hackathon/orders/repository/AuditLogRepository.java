package com.hackathon.orders.repository;

import com.hackathon.orders.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}
