package com.chtrembl.petstore.product.repository;

import com.chtrembl.petstore.product.dto.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}