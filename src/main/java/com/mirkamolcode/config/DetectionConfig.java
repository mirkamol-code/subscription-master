package com.mirkamolcode.config;

import com.mirkamolcode.detection.DetectionProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DetectionProperties.class)
public class DetectionConfig {
}
