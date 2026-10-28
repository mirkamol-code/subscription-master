package com.mirkamolcode.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "https://subscriptionmaster.app",
                        "https://www.subscriptionmaster.app",
                        "https://subscription-master.app",
                        "https://www.subscription-master.app",
                        "http://localhost:5173",
                        "http://127.0.0.1:5173"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin",
                        "Access-Control-Request-Method", "Access-Control-Request-Headers")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new CaseInsensitiveStringToEnumConverterFactory());
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        CustomSortHandlerMethodArgumentResolver sortResolver = new CustomSortHandlerMethodArgumentResolver();
        CustomPageableHandlerMethodArgumentResolver pageableResolver = new CustomPageableHandlerMethodArgumentResolver(sortResolver);
        pageableResolver.setFallbackPageable(org.springframework.data.domain.PageRequest.of(0, 20));
        pageableResolver.setMaxPageSize(100);

        resolvers.add(sortResolver);
        resolvers.add(pageableResolver);
    }
}
