package com.mirkamolcode.entity;

import com.mirkamolcode.model.Role;
import jakarta.persistence.*;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {
    public static final String DEFAULT_TIME_ZONE = "Asia/Tashkent";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(unique = true)
    private String googleSubject;

    private String displayName;

    @Column(length = 2048)
    private String avatarUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Set<Role> roles = new HashSet<>();

    @Column(nullable = false, length = 64)
    private String timeZone = DEFAULT_TIME_ZONE;

    @Version
    private long version;

    protected User() {
    }

    public User(String email, Set<Role> roles) {
        this.email = email;
        this.roles = new HashSet<>(roles);
    }

    public void linkGoogleAccount(String googleSubject, String email, String displayName, String avatarUrl) {
        this.googleSubject = googleSubject;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    public void changeTimeZone(ZoneId zone) {
        this.timeZone = zone.getId();
    }

    public void grantRole(Role role) {
        roles.add(role);
    }

    public void revokeRole(Role role) {
        roles.remove(role);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public ZoneId getTimeZone() {
        return ZoneId.of(timeZone);
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
