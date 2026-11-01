package com.mirkamolcode.controller;

import com.mirkamolcode.dto.request.AdminUserFilter;
import com.mirkamolcode.dto.request.RoleChangeRequest;
import com.mirkamolcode.dto.response.*;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.service.AdminOverviewService;
import com.mirkamolcode.service.AdminRoleService;
import com.mirkamolcode.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@Validated
public class AdminUserController {
    private final AdminUserService users;
    private final AdminRoleService roles;
    private final AdminOverviewService overview;

    public AdminUserController(AdminUserService users, AdminRoleService roles, AdminOverviewService overview) {
        this.users = users;
        this.roles = roles;
        this.overview = overview;
    }

    @GetMapping("/overview")
    @PreAuthorize("@adminAuthorizationManager.isAdmin(authentication)")
    public AdminOverviewResponse getOverview() {
        return overview.getOverview();
    }

    @GetMapping("/users")
    @PreAuthorize("@adminAuthorizationManager.isAdmin(authentication)")
    public Page<AdminUserRowResponse> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @Min(1) @Max(365) Integer joinedWithinDays,
            @RequestParam(required = false) @Min(1) @Max(365) Integer loggedInWithinDays,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) String merchant,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return users.listUsers(new AdminUserFilter(search, joinedWithinDays, loggedInWithinDays, status, merchant), pageable);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("@adminAuthorizationManager.isAdmin(authentication)")
    public AdminUserResponse findUser(@PathVariable Long id) {
        return users.findUser(id);
    }

    @GetMapping("/users/{id}/logins")
    @PreAuthorize("@adminAuthorizationManager.isAdmin(authentication)")
    public Page<LoginEventResponse> listLogins(@PathVariable Long id,
                                               @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return users.listLogins(id, pageable);
    }

    @GetMapping("/users/{id}/subscriptions")
    @PreAuthorize("@adminAuthorizationManager.isAdmin(authentication)")
    public List<AdminSubscriptionResponse> listSubscriptions(@PathVariable Long id) {
        return users.listSubscriptions(id);
    }

    @GetMapping("/users/{id}/payments")
    @PreAuthorize("@adminAuthorizationManager.isAdmin(authentication)")
    public Page<AdminPaymentResponse> listPayments(@PathVariable Long id,
                                                   @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return users.listPayments(id, pageable);
    }

    @PutMapping("/users/{id}/role")
    @PreAuthorize("@adminAuthorizationManager.isAdmin(authentication)")
    public AdminUserResponse changeRole(@PathVariable Long id, @Valid @RequestBody RoleChangeRequest request) {
        return roles.changeRole(id, request.role());
    }

    @GetMapping("/merchants")
    @PreAuthorize("@adminAuthorizationManager.isAdmin(authentication)")
    public List<MerchantOptionResponse> listMerchants() {
        return users.listMerchants();
    }
}
