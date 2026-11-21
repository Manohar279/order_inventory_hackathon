package com.hackathon.orders.exception;

import java.util.List;

public class UnknownProductException extends RuntimeException {

    private final List<Long> productIds;

    public UnknownProductException(List<Long> productIds) {
        super("Unknown product id(s): " + productIds);
        this.productIds = List.copyOf(productIds);
    }

    public List<Long> getProductIds() {
        return productIds;
    }
}
