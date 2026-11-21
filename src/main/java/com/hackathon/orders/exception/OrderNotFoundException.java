package com.hackathon.orders.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("Order " + orderId + " not found");
    }
}
