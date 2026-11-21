package com.hackathon.orders.service;

import com.hackathon.orders.dto.OrderItemRequest;
import com.hackathon.orders.dto.OrderRequest;
import com.hackathon.orders.dto.OrderResponse;
import com.hackathon.orders.entity.CustomerOrder;
import com.hackathon.orders.entity.Inventory;
import com.hackathon.orders.entity.OrderItem;
import com.hackathon.orders.entity.OrderStatus;
import com.hackathon.orders.entity.Product;
import com.hackathon.orders.exception.InvalidOrderStateException;
import com.hackathon.orders.exception.OrderNotFoundException;
import com.hackathon.orders.exception.UnknownProductException;
import com.hackathon.orders.repository.InventoryRepository;
import com.hackathon.orders.repository.OrderRepository;
import com.hackathon.orders.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final AuditService auditService;

    public OrderService(ProductRepository productRepository,
                        InventoryRepository inventoryRepository,
                        OrderRepository orderRepository,
                        AuditService auditService) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderRepository = orderRepository;
        this.auditService = auditService;
    }

    /** Result of a submission. replay=true means an earlier order with the same requestId was returned. */
    public record PlacedOrder(OrderResponse order, boolean replay) {
    }

    /**
     * Processes an order in ONE database transaction:
     * validate -> lock inventory rows -> check every item -> deduct all (CONFIRMED) or nothing (REJECTED)
     * -> save order + items -> write audit entry.
     *
     * Rejection is a normal business outcome, not an exception: the REJECTED order is
     * committed so it stays retrievable by id, and no inventory row is modified.
     * Invalid input / unknown products throw before anything is written (-> 4xx, nothing saved).
     */
    @Transactional
    public PlacedOrder placeOrder(OrderRequest request) {
        // Bonus: idempotency. Same requestId -> return the original outcome, do nothing else.
        String requestId = blankToNull(request.requestId());
        if (requestId != null) {
            Optional<CustomerOrder> existing = orderRepository.findByRequestId(requestId);
            if (existing.isPresent()) {
                return new PlacedOrder(OrderResponse.from(existing.get()), true);
            }
        }

        // 1. Merge duplicate lines, e.g. two lines for product 101 become one line of qty 2.
        Map<Long, Integer> requested = mergeItems(request.items());

        // 2. Every product must exist; otherwise 400 and nothing is written.
        Map<Long, Product> products = productRepository.findAllById(requested.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Long> unknown = requested.keySet().stream().filter(id -> !products.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new UnknownProductException(unknown);
        }

        // 3. Lock inventory rows (SELECT ... FOR UPDATE) so concurrent orders queue up here.
        Map<Long, Inventory> inventory = inventoryRepository.lockByProductIds(requested.keySet()).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));

        // 4. Check EVERY item before touching any stock.
        List<String> shortages = new ArrayList<>();
        requested.forEach((productId, qty) -> {
            Inventory stock = inventory.get(productId);
            int available = stock == null ? 0 : stock.getAvailableQty();
            if (available < qty) {
                shortages.add("Insufficient stock for product " + productId
                        + " (requested " + qty + ", available " + available + ")");
            }
        });

        CustomerOrder order = new CustomerOrder(request.customerId().trim(), requestId);
        requested.forEach((productId, qty) -> order.addItem(new OrderItem(products.get(productId), qty)));

        if (!shortages.isEmpty()) {
            // 5a. All-or-nothing: reject the whole order, inventory untouched.
            order.reject(String.join("; ", shortages));
            orderRepository.save(order);
            auditService.record(order.getId(), AuditService.ORDER_REJECTED, order.getMessage());
            return new PlacedOrder(OrderResponse.from(order), false);
        }

        // 5b. Every item can be fulfilled: deduct all quantities.
        requested.forEach((productId, qty) -> inventory.get(productId).deduct(qty));
        order.confirm("Order confirmed successfully");
        orderRepository.save(order);
        auditService.record(order.getId(), AuditService.ORDER_CONFIRMED,
                "Order confirmed; stock reserved for " + describe(requested) + "; total INR " + order.getTotalAmount());
        return new PlacedOrder(OrderResponse.from(order), false);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** Bonus: cancel a CONFIRMED order and put its stock back, in one transaction. */
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        CustomerOrder order = orderRepository.lockById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(
                    "Only CONFIRMED orders can be cancelled; order " + orderId + " is " + order.getStatus());
        }

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        order.getItems().forEach(i -> quantities.merge(i.getProductId(), i.getQuantity(), Integer::sum));

        inventoryRepository.lockByProductIds(quantities.keySet())
                .forEach(stock -> stock.restore(quantities.get(stock.getProductId())));

        order.cancel("Order cancelled; inventory restored");
        auditService.record(orderId, AuditService.ORDER_CANCELLED,
                "Order cancelled; stock restored for " + describe(quantities));
        return OrderResponse.from(order);
    }

    private static Map<Long, Integer> mergeItems(List<OrderItemRequest> items) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            merged.merge(item.productId(), item.quantity(), Math::addExact);
        }
        return merged;
    }

    private static String describe(Map<Long, Integer> quantities) {
        return quantities.entrySet().stream()
                .map(e -> "product " + e.getKey() + " x" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
