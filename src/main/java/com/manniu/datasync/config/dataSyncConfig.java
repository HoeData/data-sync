package com.manniu.datasync.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@MapperScan("com.manniu.datasync.dao")
public class dataSyncConfig {


}
