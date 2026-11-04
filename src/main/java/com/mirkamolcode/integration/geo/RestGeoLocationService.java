package com.mirkamolcode.integration.geo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Service
public class RestGeoLocationService implements GeoLocationService {

    private final RestClient client;

    private record IpApiResponse(String status, String country, String city) {
    }

    public RestGeoLocationService(RestClient.Builder builder,
                                  @Value("${app.geo.base-url}") String baseUrl,
                                  @Value("${app.geo.connect-timeout}") Duration connectTimeout,
                                  @Value("${app.geo.read-timeout}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    @Cacheable(value = "geo-locations", key = "#ip", unless = "#result == null")
    public GeoLocation locate(String ip) {
        if (ip == null || ip.isBlank() || "Unknown".equalsIgnoreCase(ip)) {
            return GeoLocation.UNKNOWN;
        }
        String cleanIp = ip.trim();
        if (isLoopback(cleanIp)) {
            return GeoLocation.LOCALHOST;
        }
        if (isPrivate(cleanIp)) {
            return GeoLocation.LOCAL_NETWORK;
        }
        return queryRemote(cleanIp);
    }

    private boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip)
                || "localhost".equalsIgnoreCase(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "::1".equals(ip);
    }

    private boolean isPrivate(String ip) {
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("169.254.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int secondOctet = Integer.parseInt(parts[1]);
                    if (secondOctet >= 16 && secondOctet <= 31) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return ip.startsWith("fc") || ip.startsWith("fd") || ip.startsWith("fe80");
    }

    private GeoLocation queryRemote(String ip) {
        try {
            IpApiResponse response = client.get()
                    .uri("/{ip}?fields=status,country,city", ip)
                    .retrieve()
                    .body(IpApiResponse.class);
            if (response != null && "success".equalsIgnoreCase(response.status())
                    && response.city() != null && response.country() != null) {
                return new GeoLocation(response.city(), response.country());
            }
            return GeoLocation.UNKNOWN;
        } catch (Exception e) {
            return GeoLocation.UNKNOWN;
        }
    }
}
