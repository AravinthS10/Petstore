package com.chtrembl.petstore.order.service;

import com.chtrembl.petstore.order.exception.OrderNotFoundException;
import com.chtrembl.petstore.order.model.Order;
import com.chtrembl.petstore.order.model.Product;
import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedIterable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final ProductService productService;
    private final CosmosClient cosmosClient;

    @Value("${petstore.service.product.url:http://localhost:8082}")
    private String productServiceUrl;

    @Value("${azure.cosmos.database}")
    private String cosmoDatabase;

    @Value("${azure.cosmos.container}")
    private String cosmoContainer;

    // Helper to get CosmosContainer
    private CosmosContainer getContainer() {
        return cosmosClient.getDatabase(cosmoDatabase).getContainer(cosmoContainer);
    }

    // Create order in Cosmos DB
    public Order createOrder(String orderId) {
        log.info("Creating new order with id: {} in Cosmos DB", orderId);
        Order order = Order.builder()
                .id(orderId)
                .products(new ArrayList<>())
                .status(Order.Status.PLACED)
                .complete(false)
                .build();
        getContainer().createItem(order);
        return order;
    }

    /**
     * Retrieves an existing order by ID from Cosmos DB.
     */
    public Order getOrderById(String orderId) {
        log.info("Retrieving order from Cosmos DB: {}", orderId);

        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("Order ID cannot be null or empty");
        }

        try {
            CosmosItemResponse<Order> response = getContainer().readItem(
                orderId,
                new PartitionKey(orderId),
                Order.class
            );
            return response.getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                log.warn("Order not found: {}", orderId);
                throw new OrderNotFoundException("Order with ID " + orderId + " not found");
            }
            throw e;
        }
    }

    /**
     * Gets an existing order or creates a new one if it doesn't exist.
     */
    public Order getOrCreateOrder(String orderId) {
        log.info("Getting or creating order: {}", orderId);
        try {
            return getOrderById(orderId);
        } catch (OrderNotFoundException ex) {
            return createOrder(orderId);
        }
    }

    public Order updateOrder(Order order) {
        log.info("Updating order: {}", order.getId());

        if (order.getProducts() != null && !order.getProducts().isEmpty()) {
            List<Product> availableProducts = productService.getAvailableProducts();
            validateProductsExist(order.getProducts(), availableProducts);
        }

        Order existingOrder = getOrCreateOrder(order.getId());

        existingOrder.setEmail(order.getEmail());

        if (order.getStatus() != null) {
            existingOrder.setStatus(order.getStatus());
        }

        Boolean isComplete = order.getComplete();
        if (isComplete != null && isComplete) {
            log.info("Completing order {} - clearing products", order.getId());
            existingOrder.setProducts(new ArrayList<>());
            existingOrder.setComplete(true);
        } else {
            existingOrder.setComplete(isComplete != null ? isComplete : false);
            updateOrderProducts(existingOrder, order.getProducts());
        }

        // Update in Cosmos DB
        getContainer().replaceItem(
            existingOrder,
            existingOrder.getId(),
            new PartitionKey(existingOrder.getId()),
            new CosmosItemRequestOptions()
        );

        return existingOrder;
    }

    /**
     * Validates that all products in the order exist in the available products list
     *
     * @param orderProducts List of products from the order
     * @param availableProducts List of available products from Product Service
     * @throws IllegalArgumentException if any product is not found
     */
    private void validateProductsExist(List<Product> orderProducts, List<Product> availableProducts) {
        if (orderProducts == null || orderProducts.isEmpty()) {
            return;
        }

        List<Long> requestedProductIds = orderProducts.stream()
                .map(Product::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());

        List<Long> availableProductIds = availableProducts.stream()
                .map(Product::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());

        List<Long> missingProductIds = requestedProductIds.stream()
                .filter(id -> !availableProductIds.contains(id))
                .collect(Collectors.toList());

        if (!missingProductIds.isEmpty()) {
            String errorMessage = String.format("Products with IDs %s are not available or do not exist",
                    missingProductIds);
            log.warn("Product validation failed for order: {}", errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        log.debug("Product validation passed for {} products", requestedProductIds.size());
    }

    private void updateOrderProducts(Order cachedOrder, List<Product> incomingProducts) {
        if (incomingProducts == null || incomingProducts.isEmpty()) {
            return;
        }

        // Single product update (add/remove/update from product page)
        if (incomingProducts.size() == 1) {
            handleSingleProductUpdate(cachedOrder, incomingProducts.get(0));
        }
        // Multiple products (cart update)
        else {
            cachedOrder.setProducts(new ArrayList<>(incomingProducts));
        }
    }

    private void handleSingleProductUpdate(Order cachedOrder, Product incomingProduct) {
        List<Product> existingProducts = cachedOrder.getProducts();
        if (existingProducts == null) {
            existingProducts = new ArrayList<>();
            cachedOrder.setProducts(existingProducts);
        }

        Integer quantity = incomingProduct.getQuantity();

        // Find existing product
        Optional<Product> existingProductOpt = existingProducts.stream()
                .filter(p -> p.getId().equals(incomingProduct.getId()))
                .findFirst();

        if (existingProductOpt.isPresent()) {
            // Update existing product quantity
            Product existingProduct = existingProductOpt.get();
            int currentQuantity = existingProduct.getQuantity();
            int newQuantity = currentQuantity + quantity;

            log.info("Updating product {} quantity: {} + {} = {}",
                    incomingProduct.getId(), currentQuantity, quantity, newQuantity);

            if (newQuantity <= 0) {
                existingProducts.removeIf(p -> p.getId().equals(incomingProduct.getId()));
                log.info("Removed product {} from order {} (quantity became {})",
                        incomingProduct.getId(), cachedOrder.getId(), newQuantity);
            } else if (newQuantity <= 10) { // Max quantity limit
                existingProduct.setQuantity(newQuantity);
                log.info("Updated product {} quantity to {} in order {}",
                        incomingProduct.getId(), newQuantity, cachedOrder.getId());
            } else {
                // Cap at maximum quantity
                existingProduct.setQuantity(10);
                log.warn("Quantity capped at maximum (10) for product {} in order {}",
                        incomingProduct.getId(), cachedOrder.getId());
            }
        } else {
            // Add new product only if quantity is positive
            if (quantity > 0) {
                int finalQuantity = Math.min(quantity, 10); // Ensure max limit
                existingProducts.add(Product.builder()
                        .id(incomingProduct.getId())
                        .quantity(finalQuantity)
                        .name(incomingProduct.getName())
                        .photoURL(incomingProduct.getPhotoURL())
                        .build());

                log.info("Added new product {} with quantity {} to order {}",
                        incomingProduct.getId(), finalQuantity, cachedOrder.getId());

                if (quantity > 10) {
                    log.warn("Quantity reduced to maximum (10) for new product {} in order {}",
                            incomingProduct.getId(), cachedOrder.getId());
                }
            } else {
                log.info("Ignoring request to add product {} with non-positive quantity {} to order {}",
                        incomingProduct.getId(), quantity, cachedOrder.getId());
            }
        }
    }

    public void enrichOrderWithProductDetails(Order order, List<Product> availableProducts) {
        if (order.getProducts() == null || availableProducts == null) {
            log.warn("Cannot enrich order: order.products={}, availableProducts={}",
                    order.getProducts(), availableProducts != null ? availableProducts.size() : "null");
            return;
        }

        log.info("Enriching order {} with {} available products",
                order.getId(), availableProducts.size());

        for (Product orderProduct : order.getProducts()) {
            String originalName = orderProduct.getName();
            String originalURL = orderProduct.getPhotoURL();

            Optional<Product> foundProduct = availableProducts.stream()
                    .filter(p -> p.getId().equals(orderProduct.getId()))
                    .findFirst();

            if (foundProduct.isPresent()) {
                Product availableProduct = foundProduct.get();
                orderProduct.setName(availableProduct.getName());
                orderProduct.setPhotoURL(availableProduct.getPhotoURL());

                log.info("Enriched product {}: '{}' -> '{}', URL: '{}' -> '{}'",
                        orderProduct.getId(), originalName, availableProduct.getName(),
                        originalURL, availableProduct.getPhotoURL());
            } else {
                log.warn("Product with id {} not found in available products during enrichment",
                        orderProduct.getId());
            }
        }
    }

    // All Cosmos DB CRUD operations for Order are implemented as required.
}
