package com.hackathon.orders.service;

import com.hackathon.orders.dto.ProductResponse;
import com.hackathon.orders.entity.Inventory;
import com.hackathon.orders.repository.InventoryRepository;
import com.hackathon.orders.repository.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public ProductService(ProductRepository productRepository, InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts() {
        Map<Long, Inventory> stock = inventoryRepository.findAll().stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));

        return productRepository.findAll(Sort.by("id")).stream()
                .map(p -> new ProductResponse(
                        p.getId(),
                        p.getName(),
                        p.getPrice(),
                        stock.containsKey(p.getId()) ? stock.get(p.getId()).getAvailableQty() : 0))
                .toList();
    }
}
