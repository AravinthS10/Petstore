package com.chtrembl.petstore.order.config;

import com.azure.cosmos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosmosConfig {

    @Value("${azure.cosmos.uri}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Bean
    public CosmosClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.EVENTUAL)
                .buildClient();
    }
}
