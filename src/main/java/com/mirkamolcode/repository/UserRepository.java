package com.mirkamolcode.repository;

import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.Role;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    long countByCreatedAtGreaterThanEqual(Instant since);

    @Query("select count(u) > 0 from User u join u.roles r where lower(u.email) = lower(:email) and r = :role")
    boolean hasRole(@Param("email") String email, @Param("role") Role role);

    @Query(value = "select ur.user_id from user_roles ur where ur.role = 'ADMIN' order by ur.user_id for update", nativeQuery = true)
    List<Long> lockAdminIds();
}
