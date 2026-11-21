package com.hackathon.orders.repository;

import com.hackathon.orders.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
