package com.mirkamolcode.service;

import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.dto.response.CalendarResponse;
import com.mirkamolcode.dto.response.PaymentHistoryResponse;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.entity.PaymentHistory;
import com.mirkamolcode.mapper.SubscriptionMapper;
import com.mirkamolcode.exception.NotFoundException;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.specification.SubscriptionSpecifications;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.Permission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubscriptionService {
    private final SubscriptionRepository repository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final SubscriptionMapper mapper;
    private final CurrentUserService currentUser;
    private final ExchangeRateService exchangeRateService;

    public SubscriptionService(SubscriptionRepository repository,
                               PaymentHistoryRepository paymentHistoryRepository,
                               SubscriptionMapper mapper,
                               CurrentUserService currentUser,
                               ExchangeRateService exchangeRateService) {
        this.repository = repository;
        this.paymentHistoryRepository = paymentHistoryRepository;
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
        return list(status, currency, null, null, null, null, minPrice, maxPrice, pageable);
    }

    /**
     * Paginated list with extended filters:
     * TASK-01: name (LIKE), TASK-02: category, TASK-05: nextPaymentDateFrom/To
     */
    public Page<SubscriptionResponse> list(SubscriptionStatus status,
                                           CurrencyCode currency,
                                           SubscriptionCategory category,
                                           String name,
                                           LocalDate nextPaymentDateFrom,
                                           LocalDate nextPaymentDateTo,
                                           BigDecimal minPrice,
                                           BigDecimal maxPrice,
                                           Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new IllegalArgumentException("minPrice must not exceed maxPrice");

        Specification<Subscription> spec = buildSpec(status, currency, category, name, nextPaymentDateFrom, nextPaymentDateTo, minPrice, maxPrice);
        return repository.findAll(spec, pageable).map(mapper::toResponse);
    }

    /**
     * TASK-03: Return ALL subscriptions (no pagination) — for calendar view.
     */
    public List<SubscriptionResponse> listAll(SubscriptionStatus status,
                                              CurrencyCode currency,
                                              SubscriptionCategory category,
                                              String name,
                                              LocalDate nextPaymentDateFrom,
                                              LocalDate nextPaymentDateTo,
                                              BigDecimal minPrice,
                                              BigDecimal maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new IllegalArgumentException("minPrice must not exceed maxPrice");

        Specification<Subscription> spec = buildSpec(status, currency, category, name, nextPaymentDateFrom, nextPaymentDateTo, minPrice, maxPrice);
        return repository.findAll(spec).stream().map(mapper::toResponse).toList();
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

    /** TASK-08: Pause — set status to PAUSED */
    @Transactional
    public SubscriptionResponse pause(Long id) {
        Subscription subscription = requiredAccessible(id, Permission.SUBSCRIPTION_UPDATE_ALL);
        if (subscription.getStatus() == SubscriptionStatus.PAUSED)
            throw new IllegalArgumentException("Subscription is already paused");
        subscription.pause();
        return mapper.toResponse(subscription);
    }

    /** TASK-08: Resume — set status back to ACTIVE */
    @Transactional
    public SubscriptionResponse resume(Long id) {
        Subscription subscription = requiredAccessible(id, Permission.SUBSCRIPTION_UPDATE_ALL);
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE)
            throw new IllegalArgumentException("Subscription is already active");
        subscription.resume();
        return mapper.toResponse(subscription);
    }

    /** TASK-07: Payment history for a subscription */
    public List<PaymentHistoryResponse> getHistory(Long id) {
        Subscription subscription = requiredAccessible(id, Permission.SUBSCRIPTION_READ_ALL);
        return paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(subscription.getId())
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    /** TASK-09: Upcoming renewals within the next N days */
    public List<SubscriptionResponse> upcoming(int days) {
        if (days < 1 || days > 365)
            throw new IllegalArgumentException("days must be between 1 and 365");

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(days);

        Specification<Subscription> spec = SubscriptionSpecifications.visible()
                .and(SubscriptionSpecifications.hasStatus(SubscriptionStatus.ACTIVE))
                .and(SubscriptionSpecifications.nextPaymentDateFrom(from))
                .and(SubscriptionSpecifications.nextPaymentDateTo(to));

        if (!currentUser.hasPermission(Permission.SUBSCRIPTION_READ_ALL))
            spec = SubscriptionSpecifications.ownedBy(currentUser.requiredUser().getId()).and(spec);

        return repository.findAll(spec).stream().map(mapper::toResponse).toList();
    }

    /** TASK-04: Calendar view with pre-converted costs in UZS */
    public CalendarResponse calendar(int year, int month) {
        if (month < 1 || month > 12)
            throw new IllegalArgumentException("month must be between 1 and 12");

        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = YearMonth.of(year, month).atEndOfMonth();

        Specification<Subscription> spec = SubscriptionSpecifications.visible()
                .and(SubscriptionSpecifications.hasStatus(SubscriptionStatus.ACTIVE))
                .and(SubscriptionSpecifications.nextPaymentDateFrom(from))
                .and(SubscriptionSpecifications.nextPaymentDateTo(to));

        if (!currentUser.hasPermission(Permission.SUBSCRIPTION_READ_ALL))
            spec = SubscriptionSpecifications.ownedBy(currentUser.requiredUser().getId()).and(spec);

        List<Subscription> subs = repository.findAll(spec);

        Map<LocalDate, List<Subscription>> grouped = subs.stream()
                .collect(Collectors.groupingBy(Subscription::getNextPaymentDate, TreeMap::new, Collectors.toList()));

        BigDecimal totalMonthCost = BigDecimal.ZERO;
        List<CalendarResponse.CalendarDayGroup> days = new ArrayList<>();

        for (Map.Entry<LocalDate, List<Subscription>> entry : grouped.entrySet()) {
            BigDecimal dayTotal = BigDecimal.ZERO;
            List<SubscriptionResponse> daySubs = new ArrayList<>();
            for (Subscription s : entry.getValue()) {
                BigDecimal rate = exchangeRateService.rateToUzs(s.getCurrency());
                BigDecimal costUzs = s.getPrice().multiply(rate);
                dayTotal = dayTotal.add(costUzs);
                daySubs.add(mapper.toResponse(s));
            }
            dayTotal = dayTotal.setScale(2, RoundingMode.HALF_UP);
            totalMonthCost = totalMonthCost.add(dayTotal);
            days.add(new CalendarResponse.CalendarDayGroup(entry.getKey(), dayTotal, daySubs));
        }

        totalMonthCost = totalMonthCost.setScale(2, RoundingMode.HALF_UP);
        return new CalendarResponse(year, month, totalMonthCost, "UZS", days);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Specification<Subscription> buildSpec(SubscriptionStatus status,
                                                   CurrencyCode currency,
                                                   SubscriptionCategory category,
                                                   String name,
                                                   LocalDate nextPaymentDateFrom,
                                                   LocalDate nextPaymentDateTo,
                                                   BigDecimal minPrice,
                                                   BigDecimal maxPrice) {
        Specification<Subscription> spec = SubscriptionSpecifications.visible()
                .and(SubscriptionSpecifications.hasStatus(status))
                .and(SubscriptionSpecifications.hasCurrency(currency))
                .and(SubscriptionSpecifications.hasCategory(category))
                .and(SubscriptionSpecifications.nameContains(name))
                .and(SubscriptionSpecifications.nextPaymentDateFrom(nextPaymentDateFrom))
                .and(SubscriptionSpecifications.nextPaymentDateTo(nextPaymentDateTo));

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

        return spec;
    }

    private Subscription requiredAccessible(Long id, Permission allPermission) {
        Subscription subscription = repository.findById(id).orElseThrow(() -> new NotFoundException("Subscription not found: " + id));
        if (subscription.isDeleted() || (!currentUser.hasPermission(allPermission) && !subscription.getUser().getId().equals(currentUser.requiredUser().getId())))
            throw new NotFoundException("Subscription not found: " + id);
        return subscription;
    }

    private PaymentHistoryResponse toHistoryResponse(PaymentHistory p) {
        return new PaymentHistoryResponse(
                p.getId(),
                p.getPaymentDate(),
                p.getOriginalAmount(),
                p.getOriginalCurrency(),
                p.getAmountUzs(),
                p.getExchangeRateToUzs()
        );
    }
}
