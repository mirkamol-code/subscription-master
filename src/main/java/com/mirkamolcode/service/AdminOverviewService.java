package com.mirkamolcode.service;

import com.mirkamolcode.dto.response.AdminOverviewResponse;
import com.mirkamolcode.exception.ExchangeRatesUnavailableException;
import com.mirkamolcode.repository.LoginEventRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.repository.UserRepository;
import com.mirkamolcode.repository.projection.CurrencyTotal;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminOverviewService {
    private static final Logger log = LoggerFactory.getLogger(AdminOverviewService.class);
    private static final int DUE_WINDOW_DAYS = 7;
    private static final Duration LOGIN_WINDOW = Duration.ofHours(24);

    private final UserRepository users;
    private final SubscriptionRepository subscriptions;
    private final LoginEventRepository loginEvents;
    private final ExchangeRateService rates;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public AdminOverviewService(UserRepository users,
                                SubscriptionRepository subscriptions,
                                LoginEventRepository loginEvents,
                                ExchangeRateService rates,
                                CurrentUserService currentUser,
                                Clock clock) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.loginEvents = loginEvents;
        this.rates = rates;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public AdminOverviewResponse getOverview() {
        ZoneId zone = currentUser.requiredUser().getTimeZone();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        List<CurrencyTotal> due = subscriptions.sumDuePaymentsByCurrency(today, today.plusDays(DUE_WINDOW_DAYS - 1));
        return new AdminOverviewResponse(
                users.countByCreatedAtGreaterThanEqual(today.atStartOfDay(zone).toInstant()),
                users.countByCreatedAtGreaterThanEqual(now.minus(Duration.ofDays(7))),
                users.countByCreatedAtGreaterThanEqual(now.minus(Duration.ofDays(30))),
                subscriptions.countActiveSubscribers(),
                loginEvents.countByOccurredAtGreaterThanEqual(now.minus(LOGIN_WINDOW)),
                due.stream().mapToLong(CurrencyTotal::getCount).sum(),
                convertToUzs(due));
    }

    private BigDecimal convertToUzs(List<CurrencyTotal> totals) {
        try {
            return totals.stream()
                    .map(total -> total.getTotal().multiply(rates.rateToUzs(total.getCurrency())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (ExchangeRatesUnavailableException e) {
            log.warn("Due payments total skipped: {}", e.getMessage());
            return null;
        }
    }
}
