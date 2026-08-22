package com.mirkamolcode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        exclude = UserDetailsServiceAutoConfiguration.class
)
@EnableCaching
@EnableScheduling

public class SubscribeMasterApplication {
    public static void main(String[] args) { SpringApplication.run(SubscribeMasterApplication.class, args); }
}
