package com.mirkamolcode.dto.request;

import com.mirkamolcode.model.Role;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(
        @NotNull(message = "Role is required")
        Role role
) {
}
