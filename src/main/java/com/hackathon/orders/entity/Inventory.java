package com.hackathon.orders.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;


@Entity
@Table(name = "inventory")
@Check(constraints = "available_qty >= 0")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    protected Inventory() {
    }

    public Inventory(Long productId, int availableQty) {
        this.productId = productId;
        this.availableQty = availableQty;
    }

    public boolean canFulfil(int quantity) {
        return availableQty >= quantity;
    }

    public void deduct(int quantity) {
        if (!canFulfil(quantity)) {
            throw new IllegalStateException("Cannot deduct " + quantity + " from product " + productId);
        }
        this.availableQty -= quantity;
    }

    public void restore(int quantity) {
        this.availableQty += quantity;
    }

    public Long getProductId() { return productId; }
    public int getAvailableQty() { return availableQty; }
}
