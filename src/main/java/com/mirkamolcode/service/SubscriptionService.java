package com.mirkamolcode.service;

import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.mapper.SubscriptionMapper;
import com.mirkamolcode.exception.NotFoundException;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.specification.SubscriptionSpecifications;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.Permission;
import com.mirkamolcode.model.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubscriptionService {
    private final SubscriptionRepository repository;
    private final SubscriptionMapper mapper;
    private final CurrentUserService currentUser;
    private final ExchangeRateService exchangeRateService;

    public SubscriptionService(SubscriptionRepository repository,
                               SubscriptionMapper mapper,
                               CurrentUserService currentUser,
                               ExchangeRateService exchangeRateService) {
        this.repository = repository;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.exchangeRateService = exchangeRateService;
    }

    @Transactional
    public SubscriptionResponse create(SubscriptionRequest request) {
        User user = currentUser.requiredUser();
        return mapper.toResponse(repository.save(new Subscription(
                user,
                request.name(),
                request.price(),
                request.currency(),
                request.frequency(),
                request.status(),
                request.category(),
                request.startDate()
        )));
    }

    public Page<SubscriptionResponse> list(SubscriptionStatus status,
                                           CurrencyCode currency,
                                           BigDecimal minPrice,
                                           BigDecimal maxPrice,
                                           Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new IllegalArgumentException("minPrice must not exceed maxPrice");

        Specification<Subscription> spec = SubscriptionSpecifications.visible()
                .and(SubscriptionSpecifications.hasStatus(status))
                .and(SubscriptionSpecifications.hasCurrency(currency));

        if (currency != null) {
            spec = spec.and(SubscriptionSpecifications.priceAtLeast(minPrice))
                    .and(SubscriptionSpecifications.priceAtMost(maxPrice));
        } else if (minPrice != null || maxPrice != null) {
            Map<CurrencyCode, BigDecimal> rates = new EnumMap<>(CurrencyCode.class);
            for (CurrencyCode c : CurrencyCode.values()) {
                rates.put(c, exchangeRateService.rateToUzs(c));
            }
            spec = spec.and(SubscriptionSpecifications.priceInUzsAtLeast(minPrice, rates))
                    .and(SubscriptionSpecifications.priceInUzsAtMost(maxPrice, rates));
        }

        if (!currentUser.hasPermission(Permission.SUBSCRIPTION_READ_ALL))
            spec = SubscriptionSpecifications.ownedBy(currentUser.requiredUser().getId()).and(spec);

        return repository.findAll(spec, pageable).map(mapper::toResponse);
    }

    public SubscriptionResponse get(Long id) {
        return mapper.toResponse(requiredAccessible(id, Permission.SUBSCRIPTION_READ_ALL));
    }

    @Transactional
    public SubscriptionResponse update(Long id, SubscriptionRequest request) {
        Subscription subscription = requiredAccessible(id, Permission.SUBSCRIPTION_UPDATE_ALL);
        subscription.update(request.name(), request.price(), request.currency(), request.frequency(), request.status(), request.category(), request.startDate());
        return mapper.toResponse(subscription);
    }

    @Transactional
    public void delete(Long id) {
        requiredAccessible(id, Permission.SUBSCRIPTION_DELETE_ALL).markDeleted();
    }

    private Subscription requiredAccessible(Long id, Permission allPermission) {
        Subscription subscription = repository.findById(id).orElseThrow(() -> new NotFoundException("Subscription not found: " + id));
        if (subscription.isDeleted() || (!currentUser.hasPermission(allPermission) && !subscription.getUser().getId().equals(currentUser.requiredUser().getId())))
            throw new NotFoundException("Subscription not found: " + id);
        return subscription;
    }
}
