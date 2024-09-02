package com.manniu.datasync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties("manniu.datasync")
@Component
@Data
public class SyncProperties {
    private String ip;
    private Integer port;
    private String saveFile;

}
