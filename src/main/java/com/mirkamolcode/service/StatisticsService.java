package com.mirkamolcode.service;

import com.mirkamolcode.dto.*;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.specification.SubscriptionSpecifications;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional(readOnly = true)
public class StatisticsService {
    private final SubscriptionRepository subscriptions;
    private final PaymentHistoryRepository payments;
    private final ExchangeRateService rates;
    private final CurrentUserService currentUser;

    public StatisticsService(SubscriptionRepository subscriptions, PaymentHistoryRepository payments, ExchangeRateService rates, CurrentUserService currentUser) {
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.rates = rates;
        this.currentUser = currentUser;
    }

    public SpendingSummary mySummary() {
        List<Subscription> items = activeFor(currentUser.requiredUser().getId());
        List<Cost> costs = items.stream().map(this::monthlyCost).toList();
        BigDecimal total = sum(costs.stream().map(Cost::amount).toList());
        SubscriptionCost highest = costs.stream().max(Comparator.comparing(Cost::amount)).map(c -> new SubscriptionCost(c.subscription().getId(), c.subscription().getName(), c.amount())).orElse(null);
        Map<SubscriptionCategory, BigDecimal> grouped = costs.stream().collect(Collectors.groupingBy(c -> c.subscription().getCategory(), Collectors.reducing(BigDecimal.ZERO, Cost::amount, BigDecimal::add)));
        List<CategoryCost> categories = grouped.entrySet().stream().map(e -> new CategoryCost(e.getKey(), scale(e.getValue()))).sorted(Comparator.comparing(CategoryCost::category)).toList();
        return new SpendingSummary(scale(total), "UZS", highest, categories);
    }

    public List<MonthlyCost> monthlyDynamics(int months) {
        if (months < 1 || months > 12) throw new IllegalArgumentException("months must be between 1 and 12");
        Long userId = currentUser.requiredUser().getId();
        YearMonth thisMonth = YearMonth.now();
        return IntStream.range(0, months).mapToObj(i -> thisMonth.minusMonths(months - 1L - i)).map(month -> {
            BigDecimal sum = payments.findBySubscriptionUserIdAndPaymentDateBetween(userId, month.atDay(1), month.atEndOfMonth()).stream().map(p -> p.getAmountUzs()).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new MonthlyCost(month, scale(sum));
        }).toList();
    }

    public List<AdminUsage> serviceUsage() {
        return subscriptions.findAll(SubscriptionSpecifications.visible(), Sort.by("name")).stream().collect(Collectors.groupingBy(Subscription::getName, Collectors.counting())).entrySet().stream().map(e -> new AdminUsage(e.getKey(), e.getValue())).sorted(Comparator.comparingLong(AdminUsage::subscriptionCount).reversed().thenComparing(AdminUsage::serviceName)).toList();
    }

    private List<Subscription> activeFor(Long userId) {
        return subscriptions.findAll(SubscriptionSpecifications.ownedBy(userId).and(SubscriptionSpecifications.visible()).and(SubscriptionSpecifications.hasStatus(SubscriptionStatus.ACTIVE)));
    }

    private Cost monthlyCost(Subscription s) {
        return new Cost(s, s.getPrice().multiply(s.getFrequency().monthlyMultiplier()).multiply(rates.rateToUzs(s.getCurrency())));
    }

    private BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private record Cost(Subscription subscription, BigDecimal amount) {
    }
}
