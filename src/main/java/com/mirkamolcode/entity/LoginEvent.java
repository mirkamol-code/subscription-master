package com.mirkamolcode.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "login_events")
public class LoginEvent {
    public static final int MAX_USER_AGENT_LENGTH = 512;
    public static final int MAX_IP_LENGTH = 45;
    public static final int MAX_LOCATION_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(length = MAX_IP_LENGTH)
    private String ip;

    @Column(length = MAX_USER_AGENT_LENGTH)
    private String userAgent;

    @Column(length = MAX_LOCATION_LENGTH)
    private String city;

    @Column(length = MAX_LOCATION_LENGTH)
    private String country;

    protected LoginEvent() {
    }

    public LoginEvent(User user, Instant occurredAt, String ip, String userAgent) {
        this(user, occurredAt, ip, userAgent, null, null);
    }

    public LoginEvent(User user, Instant occurredAt, String ip, String userAgent, String city, String country) {
        this.user = user;
        this.occurredAt = occurredAt;
        this.ip = truncate(ip, MAX_IP_LENGTH);
        this.userAgent = truncate(userAgent, MAX_USER_AGENT_LENGTH);
        this.city = truncate(city, MAX_LOCATION_LENGTH);
        this.country = truncate(country, MAX_LOCATION_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }
}
