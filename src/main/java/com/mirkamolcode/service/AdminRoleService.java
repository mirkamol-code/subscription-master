package com.mirkamolcode.service;

import com.mirkamolcode.dto.response.AdminUserResponse;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.exception.LastAdminException;
import com.mirkamolcode.exception.NotFoundException;
import com.mirkamolcode.mapper.AdminUserMapper;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.repository.UserRepository;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminRoleService {
    private static final Logger log = LoggerFactory.getLogger(AdminRoleService.class);

    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final AdminUserMapper mapper;

    public AdminRoleService(UserRepository users, CurrentUserService currentUser, AdminUserMapper mapper) {
        this.users = users;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    public AdminUserResponse changeRole(Long userId, Role role) {
        User target = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (role == Role.ADMIN)
            target.grantRole(Role.ADMIN);
        else
            demote(target);
        log.info("Admin {} set role of user {} to {}", currentUser.requiredUser().getId(), userId, role);
        return mapper.toResponse(users.save(target));
    }

    private void demote(User target) {
        List<Long> adminIds = users.lockAdminIds();
        boolean isLastAdmin = adminIds.size() == 1 && adminIds.contains(target.getId());
        if (isLastAdmin)
            throw new LastAdminException();
        target.revokeRole(Role.ADMIN);
    }
}
