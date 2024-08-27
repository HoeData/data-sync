package com.manniu.datasync.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Autowired
    private SyncProperties syncProperties;

    @Bean
    public WebClient webClient() {
        return WebClient.builder().baseUrl("http://" + syncProperties.getIp() + ":" + syncProperties.getPort()).build();
    }
}
