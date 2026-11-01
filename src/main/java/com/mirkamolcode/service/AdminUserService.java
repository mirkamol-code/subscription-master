package com.mirkamolcode.service;

import com.mirkamolcode.dto.request.AdminUserFilter;
import com.mirkamolcode.dto.response.AdminPaymentResponse;
import com.mirkamolcode.dto.response.AdminSubscriptionResponse;
import com.mirkamolcode.dto.response.AdminUserResponse;
import com.mirkamolcode.dto.response.AdminUserRowResponse;
import com.mirkamolcode.dto.response.LoginEventResponse;
import com.mirkamolcode.dto.response.MerchantOptionResponse;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.exception.NotFoundException;
import com.mirkamolcode.mapper.AdminUserMapper;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.LoginEventRepository;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.repository.UserRepository;
import com.mirkamolcode.repository.projection.UserSubscriptionSummary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.mirkamolcode.specification.UserSpecifications.*;

@Service
@Transactional(readOnly = true)
public class AdminUserService {
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "lastLoginAt", "email", "displayName");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));
    private static final int MAX_MERCHANTS_PER_ROW = 3;
    private static final String MERCHANT_NAME_SEPARATOR = "\u001f";

    private final UserRepository users;
    private final SubscriptionRepository subscriptions;
    private final PaymentHistoryRepository payments;
    private final LoginEventRepository loginEvents;
    private final AdminUserMapper mapper;
    private final Clock clock;

    public AdminUserService(UserRepository users,
                            SubscriptionRepository subscriptions,
                            PaymentHistoryRepository payments,
                            LoginEventRepository loginEvents,
                            AdminUserMapper mapper,
                            Clock clock) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.loginEvents = loginEvents;
        this.mapper = mapper;
        this.clock = clock;
    }

    public Page<AdminUserRowResponse> listUsers(AdminUserFilter filter, Pageable pageable) {
        Specification<User> specification = Specification.allOf(
                matchesSearch(filter.search()),
                joinedSince(subtractDays(filter.joinedWithinDays())),
                loggedInSince(subtractDays(filter.loggedInWithinDays())),
                hasSubscriptionWithStatus(filter.status()),
                hasSubscriptionWithMerchant(filter.merchant()),
                orderedNullsLast(validateSort(pageable.getSort())));
        Page<User> page = users.findAll(specification, withoutSort(pageable));
        Map<Long, UserSubscriptionSummary> summaries = loadSummaries(page.getContent());
        return page.map(user -> mapToRowResponse(user, summaries.get(user.getId())));
    }

    public AdminUserResponse findUser(Long userId) {
        return mapper.toResponse(requireUser(userId));
    }

    public Page<LoginEventResponse> listLogins(Long userId, Pageable pageable) {
        requireUser(userId);
        return loginEvents.findByUserIdOrderByOccurredAtDesc(userId, withoutSort(pageable)).map(mapper::toResponse);
    }

    public List<AdminSubscriptionResponse> listSubscriptions(Long userId) {
        requireUser(userId);
        return subscriptions.findByUserIdOrderByCreatedAtDesc(userId).stream().map(mapper::toResponse).toList();
    }

    public Page<AdminPaymentResponse> listPayments(Long userId, Pageable pageable) {
        requireUser(userId);
        return payments.findBySubscriptionUserIdOrderByPaymentDateDescIdDesc(userId, withoutSort(pageable))
                .map(mapper::toResponse);
    }

    public List<MerchantOptionResponse> listMerchants() {
        return subscriptions.findMerchantOptions().stream()
                .map(option -> new MerchantOptionResponse(option.getSlug(), option.getName()))
                .toList();
    }

    private User requireUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Instant subtractDays(Integer days) {
        return days == null ? null : clock.instant().minus(Duration.ofDays(days));
    }

    private Map<Long, UserSubscriptionSummary> loadSummaries(List<User> pageUsers) {
        if (pageUsers.isEmpty())
            return Map.of();
        List<Long> ids = pageUsers.stream().map(User::getId).toList();
        return subscriptions.summariseByUser(ids).stream()
                .collect(Collectors.toMap(UserSubscriptionSummary::getUserId, Function.identity()));
    }

    private static Sort validateSort(Sort requested) {
        Sort sort = requested.isUnsorted() ? DEFAULT_SORT : requested;
        sort.forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty()))
                throw new IllegalArgumentException("Users cannot be sorted by " + order.getProperty());
        });
        return sort.and(Sort.by("id"));
    }

    private static Pageable withoutSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private AdminUserRowResponse mapToRowResponse(User user, UserSubscriptionSummary summary) {
        AdminUserResponse profile = mapper.toResponse(user);
        return new AdminUserRowResponse(profile.id(), profile.email(), profile.displayName(), profile.avatarUrl(),
                profile.role(), profile.createdAt(), profile.lastLoginAt(),
                summary == null ? 0 : summary.getActiveCount(),
                summary == null ? List.of() : splitMerchantNames(summary.getMerchantNames()),
                summary == null ? null : mostUrgentStatus(summary));
    }

    private static SubscriptionStatus mostUrgentStatus(UserSubscriptionSummary summary) {
        if (summary.getHasBillingRequired())
            return SubscriptionStatus.BILLING_REQUIRED;
        if (summary.getHasActive())
            return SubscriptionStatus.ACTIVE;
        return summary.getHasCancelled() ? SubscriptionStatus.CANCELLED : null;
    }

    private static List<String> splitMerchantNames(String joined) {
        if (joined == null || joined.isEmpty())
            return List.of();
        return Arrays.stream(joined.split(MERCHANT_NAME_SEPARATOR)).limit(MAX_MERCHANTS_PER_ROW).toList();
    }
}
