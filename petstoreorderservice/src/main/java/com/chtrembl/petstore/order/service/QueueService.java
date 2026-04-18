package com.chtrembl.petstore.order.service;


import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.chtrembl.petstore.order.model.Order;
import com.chtrembl.petstore.order.model.OrderItemsReserver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class QueueService {

    private final ServiceBusSenderClient serviceBusSenderClient;
    private final ObjectMapper objectMapper;

    public OrderItemsReserver reserveOrderItems(Order updatedOrder) {
        try {
            // Build orderDetails map
            Map<String, Object> orderDetails = new HashMap<>();
            orderDetails.put("orderId", updatedOrder.getId());
            orderDetails.put("customer", updatedOrder.getEmail());
            // Build products list
            List<Map<String, Object>> products = updatedOrder.getProducts().stream().map(product -> {
                Map<String, Object> prod = new HashMap<>();
                prod.put("id", product.getId());
                prod.put("name", product.getName());
                prod.put("quantity", product.getQuantity());
                return prod;
            }).collect(Collectors.toList());
            OrderItemsReserver requestPayload = new OrderItemsReserver(
                    MDC.get("sessionId"),
                    orderDetails,
                    products
            );
            String jsonBody = objectMapper.writeValueAsString(requestPayload);
            // Send the message to the queue
            serviceBusSenderClient.sendMessage(new com.azure.messaging.servicebus.ServiceBusMessage(jsonBody));
            log.info("OrderItemsReserver message sent to queue: {}", jsonBody);
            return requestPayload;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize OrderItemsReserver payload", e);
            throw new RuntimeException("Serialization error", e);
        } catch (RestClientException e) {
            log.error("REST call to reserve order items failed", e);
            throw new RuntimeException("REST call failed", e);
        }
    }

}
