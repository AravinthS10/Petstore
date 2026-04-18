package com.chtrembl.petstore.order.model;

import lombok.Data;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Data
public class OrderItemsReserver {
    private String sessionId;
    private Map<String, Object> orderDetails;
    private List<Map<String, Object>> products;
    private String generatedAtUtc;

    public OrderItemsReserver(String sessionId, Map<String, Object> orderDetails, List<Map<String, Object>> products) {
        this.sessionId = sessionId;
        this.orderDetails = orderDetails;
        this.products = products;
        this.generatedAtUtc = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    }
}