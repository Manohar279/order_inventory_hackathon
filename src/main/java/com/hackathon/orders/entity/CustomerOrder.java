package com.hackathon.orders.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(name = "order_seq", sequenceName = "order_seq", initialValue = 1001, allocationSize = 1)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 500)
    private String message;

    /** Bonus: optional client-supplied key for idempotent submission. */
    @Column(name = "request_id", unique = true, length = 100)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(String customerId, String requestId) {
        this.customerId = customerId;
        this.requestId = requestId;
        this.createdAt = LocalDateTime.now();
        this.totalAmount = BigDecimal.ZERO;
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
        totalAmount = totalAmount.add(item.getLineTotal());
    }

    public void confirm(String message) {
        this.status = OrderStatus.CONFIRMED;
        this.message = message;
    }

    public void reject(String message) {
        this.status = OrderStatus.REJECTED;
        this.message = message;
    }

    public void cancel(String message) {
        this.status = OrderStatus.CANCELLED;
        this.message = message;
    }

    public Long getId() { return id; }
    public String getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getMessage() { return message; }
    public String getRequestId() { return requestId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return items; }
}
