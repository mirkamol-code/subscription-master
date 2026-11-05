package com.mirkamolcode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(
        exclude = UserDetailsServiceAutoConfiguration.class
)
@EnableCaching
public class SubscribeMasterApplication {
    public static void main(String[] args) { SpringApplication.run(SubscribeMasterApplication.class, args); }
}
