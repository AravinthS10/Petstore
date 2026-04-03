package com.chtrembl.petstore.product.service;

import com.chtrembl.petstore.product.model.Product;
import com.chtrembl.petstore.product.model.Category;
import com.chtrembl.petstore.product.model.Tag;
import com.chtrembl.petstore.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<Product> findProductsByStatus(List<String> status) {
        log.info("Finding products with status: {}", status);
        return productRepository.findAll().stream()
                .filter(productDto -> status.stream().anyMatch(s -> s.equalsIgnoreCase(productDto.getStatus())))
                .map(this::mapToModelProduct)
                .collect(Collectors.toList());
    }

    public Optional<Product> findProductById(Long productId) {
        log.info("Finding product with id: {}", productId);
        return productRepository.findById(productId)
                .map(this::mapToModelProduct);
    }

    public List<Product> getAllProducts() {
        log.info("Getting all products");
        return productRepository.findAll().stream()
                .map(this::mapToModelProduct)
                .collect(Collectors.toList());
    }

    public int getProductCount() {
        return (int) productRepository.count();
    }

    private Product mapToModelProduct(com.chtrembl.petstore.product.dto.Product dto) {
        if (dto == null) return null;
        return Product.builder()
                .id(dto.getId())
                .name(dto.getName())
                .category(mapToModelCategory(dto.getCategory()))
                .photoURL(dto.getPhotoURL())
                .tags(dto.getTags() != null ? dto.getTags().stream().map(this::mapToModelTag).collect(Collectors.toList()) : null)
                .status(mapToModelStatus(dto.getStatus()))
                .build();
    }

    private Category mapToModelCategory(com.chtrembl.petstore.product.dto.Category dto) {
        if (dto == null) return null;
        return Category.builder()
                .id(dto.getId())
                .name(dto.getName())
                .build();
    }

    private Tag mapToModelTag(com.chtrembl.petstore.product.dto.Tag dto) {
        if (dto == null) return null;
        return Tag.builder()
                .id(dto.getId())
                .name(dto.getName())
                .build();
    }

    private Product.Status mapToModelStatus(String status) {
        if (status == null) return null;
        try {
            return Product.Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown status: {}", status);
            return null;
        }
    }
}