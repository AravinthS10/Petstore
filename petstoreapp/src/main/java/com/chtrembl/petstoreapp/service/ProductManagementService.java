package com.chtrembl.petstoreapp.service;

import com.chtrembl.petstoreapp.client.ProductServiceClient;
import com.chtrembl.petstoreapp.exception.ProductServiceException;
import com.chtrembl.petstoreapp.model.ContainerEnvironment;
import com.chtrembl.petstoreapp.model.Product;
import com.chtrembl.petstoreapp.model.Tag;
import com.chtrembl.petstoreapp.model.User;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static com.chtrembl.petstoreapp.config.Constants.CATEGORY;
import static com.chtrembl.petstoreapp.config.Constants.OPERATION;
import static com.chtrembl.petstoreapp.config.Constants.REQUEST_ID;
import static com.chtrembl.petstoreapp.config.Constants.TRACE_ID;
import static com.chtrembl.petstoreapp.model.Status.AVAILABLE;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductManagementService {

    private final User sessionUser;
    private final ContainerEnvironment containerEnvironment;
    private final ProductServiceClient productServiceClient;

    public Collection<Product> getProductsByCategory(String category, List<Tag> tags){
    List<Product> products;

    MDC.put(OPERATION, "getProducts");
    MDC.put(CATEGORY, category);

    String requestId = MDC.get(REQUEST_ID);
    String traceId = MDC.get(TRACE_ID);

    log.info("Starting product retrieval operation [RequestID: {}, TraceID: {}, Category: {}]",
            requestId, traceId, category);

    try {
        this.sessionUser.getTelemetryClient().trackEvent(
                String.format("PetStoreApp user %s is requesting to retrieve products from the ProductService",
                        this.sessionUser.getName()),
                this.sessionUser.getCustomEventProperties(), null);

        // Custom event with username and session
        Map<String, String> eventProps = new HashMap<>(this.sessionUser.getCustomEventProperties());
        eventProps.put("username", this.sessionUser.getName());
        eventProps.put("sessionId", this.sessionUser.getSessionId()); // or appropriate session info

        this.sessionUser.getTelemetryClient().trackEvent(
            "PetStoreApp user requested products by category",
             eventProps,
             null);

        products = productServiceClient.getProductsByStatus(AVAILABLE.getValue());
        this.sessionUser.setProducts(products);

        if (tags.stream().anyMatch(t -> t.getName().equals("large"))) {
            products = products.stream()
                    .filter(product -> category.equals(product.getCategory().getName())
                            && product.getTags().toString().contains("large"))
                    .toList();
        } else {
            products = products.stream()
                    .filter(product -> category.equals(product.getCategory().getName())
                            && product.getTags().toString().contains("small"))
                    .toList();
        }

        log.info("Successfully retrieved {} products for category {} with tags {} [RequestID: {}, TraceID: {}]",
                products.size(), category, tags, requestId, traceId);

        // Custom metric for number of products returned
        this.sessionUser.getTelemetryClient().trackMetric("ProductsReturned", products.size());
		boolean val=true;
		if(val){
        throw new Exception("Cannot move further");
		}
        return products;
        } catch (FeignException fe) {
            log.error("Feign error retrieving products [RequestID: {}, TraceID: {}, Category: {}, HTTP: {}, Message: {}]",
                    requestId, traceId, category, fe.status(), fe.getMessage(), fe);

            this.sessionUser.getTelemetryClient().trackException(fe);
            this.sessionUser.getTelemetryClient().trackEvent(
                    String.format("PetStoreApp %s received Feign error %s (HTTP %d), container host: %s",
                            this.sessionUser.getName(),
                            fe.getMessage(),
                            fe.status(),
                            this.containerEnvironment.getContainerHostName())
            );
            log.error("Failed to retrieve products from ProductService via Feign client", fe);
            throw new ProductServiceException("Unable to retrieve products from product service", fe);
        }
	catch (Exception e) {
	log.error("General error retrieving products [RequestID: {}, TraceID: {}, Category: {}, Message: {}]",
	                 requestId, traceId, category, e.getMessage(), e);
        this.sessionUser.getTelemetryClient().trackException(e);
        this.sessionUser.getTelemetryClient().trackEvent(
                String.format("PetStoreApp %s received general error %s, container host: %s",
                        this.sessionUser.getName(),
                        e.getMessage(),
                        this.containerEnvironment.getContainerHostName())
        );
        throw new ProductServiceException("Unable to retrieve products from product service", e);
    } finally {
        MDC.remove(OPERATION);
        MDC.remove(CATEGORY);
    }
}
}