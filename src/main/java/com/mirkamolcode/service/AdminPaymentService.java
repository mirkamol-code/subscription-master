package com.mirkamolcode.service;

import com.mirkamolcode.dto.response.DuePaymentsResponse;
import com.mirkamolcode.dto.response.PaymentDueResponse;
import com.mirkamolcode.dto.response.UpcomingPaymentsResponse;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.exception.ExchangeRatesUnavailableException;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.DueBucket;
import com.mirkamolcode.model.DueEntryKind;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.SubscriptionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminPaymentService {
    private static final int MAX_RANGE_DAYS = 93;
    private static final Comparator<PaymentDueResponse> BY_URGENCY = Comparator
            .comparing((PaymentDueResponse entry) -> entry.bucket() != DueBucket.OVERDUE)
            .thenComparing(PaymentDueResponse::dueDate)
            .thenComparing(PaymentDueResponse::merchant, String.CASE_INSENSITIVE_ORDER);

    private final SubscriptionRepository subscriptions;
    private final ExchangeRateService rates;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public AdminPaymentService(SubscriptionRepository subscriptions,
                               ExchangeRateService rates,
                               CurrentUserService currentUser,
                               Clock clock) {
        this.subscriptions = subscriptions;
        this.rates = rates;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public DuePaymentsResponse listDue(LocalDate from, LocalDate to, String merchantSlug, SubscriptionStatus status) {
        requireValidRange(from, to);
        ZoneId zone = currentUser.requiredUser().getTimeZone();
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        UzsConverter converter = new UzsConverter();
        List<PaymentDueResponse> entries = subscriptions.findDueBetween(from, to, normalizeSlug(merchantSlug), status).stream()
                .flatMap(subscription -> buildEntries(subscription, from, to, today, converter))
                .sorted(BY_URGENCY)
                .toList();
        return new DuePaymentsResponse(today, zone.getId(), entries);
    }

    public UpcomingPaymentsResponse listUpcoming() {
        return listUpcoming(currentUser.requiredUser().getTimeZone());
    }

    public UpcomingPaymentsResponse listUpcoming(ZoneId zone) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        LocalDate lastDay = today.plusDays(DueBucket.WEEK_DAYS - 1L);
        UzsConverter converter = new UzsConverter();
        Map<EntryKey, PaymentDueResponse> unique = new LinkedHashMap<>();
        Stream.concat(
                        subscriptions.findBillingRequired().stream().map(s -> buildPayment(s, today, converter)),
                        subscriptions.findDueBetween(today, lastDay, null, null).stream()
                                .flatMap(s -> buildEntries(s, today, lastDay, today, converter)))
                .forEach(entry -> unique.putIfAbsent(EntryKey.of(entry), entry));
        List<PaymentDueResponse> entries = unique.values().stream().sorted(BY_URGENCY).toList();
        return new UpcomingPaymentsResponse(today, zone.getId(), entries, sumExpected(entries), sumOverdue(entries));
    }

    public static BigDecimal sumUzs(Stream<PaymentDueResponse> payments) {
        return payments.map(PaymentDueResponse::amountUzs).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumExpected(List<PaymentDueResponse> entries) {
        return sumUzs(payments(entries).filter(entry -> entry.bucket() != DueBucket.OVERDUE));
    }

    private static BigDecimal sumOverdue(List<PaymentDueResponse> entries) {
        return sumUzs(payments(entries).filter(entry -> entry.bucket() == DueBucket.OVERDUE));
    }

    private static Stream<PaymentDueResponse> payments(List<PaymentDueResponse> entries) {
        return entries.stream().filter(entry -> entry.kind() == DueEntryKind.PAYMENT);
    }

    private static void requireValidRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from))
            throw new IllegalArgumentException("'to' must not be before 'from'");
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS)
            throw new IllegalArgumentException("The date range can span at most " + MAX_RANGE_DAYS + " days");
    }

    private static String normalizeSlug(String merchantSlug) {
        return merchantSlug == null || merchantSlug.isBlank() ? null : merchantSlug.trim().toLowerCase(Locale.ROOT);
    }

    private Stream<PaymentDueResponse> buildEntries(Subscription subscription, LocalDate from, LocalDate to,
                                                    LocalDate today, UzsConverter converter) {
        List<PaymentDueResponse> entries = new ArrayList<>(2);
        if (isWithin(subscription.getNextPaymentDate(), from, to) && willRenew(subscription))
            entries.add(buildPayment(subscription, today, converter));
        if (isWithin(subscription.getEndsOn(), from, to))
            entries.add(buildAccessEnd(subscription, today));
        return entries.stream();
    }

    private PaymentDueResponse buildPayment(Subscription subscription, LocalDate today, UzsConverter converter) {
        LocalDate dueDate = subscription.getNextPaymentDate();
        return mapToEntry(subscription, DueEntryKind.PAYMENT, dueDate, subscription.getPrice(),
                converter.convert(subscription.getPrice(), subscription.getCurrency()),
                DueBucket.classifyPayment(dueDate, subscription.getStatus(), today));
    }

    private static PaymentDueResponse buildAccessEnd(Subscription subscription, LocalDate today) {
        LocalDate endsOn = subscription.getEndsOn();
        return mapToEntry(subscription, DueEntryKind.ACCESS_END, endsOn, null, null,
                DueBucket.classifyAccessEnd(endsOn, today));
    }

    private static PaymentDueResponse mapToEntry(Subscription subscription, DueEntryKind kind, LocalDate date,
                                                 BigDecimal amount, BigDecimal amountUzs, DueBucket bucket) {
        User user = subscription.getUser();
        String userName = user.getDisplayName() == null ? user.getEmail() : user.getDisplayName();
        return new PaymentDueResponse(subscription.getId(), user.getId(), userName, user.getEmail(),
                subscription.getName(), subscription.getMerchantSlug(), kind, date, amount,
                subscription.getCurrency(), amountUzs, subscription.getStatus(), bucket);
    }

    private static boolean willRenew(Subscription subscription) {
        return subscription.getEndsOn() == null || subscription.getEndsOn().isAfter(subscription.getNextPaymentDate());
    }

    private static boolean isWithin(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    private record EntryKey(Long subscriptionId, DueEntryKind kind) {
        static EntryKey of(PaymentDueResponse entry) {
            return new EntryKey(entry.subscriptionId(), entry.kind());
        }
    }

    private class UzsConverter {
        private final Map<CurrencyCode, Optional<BigDecimal>> rateByCurrency = new EnumMap<>(CurrencyCode.class);

        BigDecimal convert(BigDecimal amount, CurrencyCode currency) {
            return rateByCurrency.computeIfAbsent(currency, this::findRate)
                    .map(amount::multiply)
                    .orElse(null);
        }

        private Optional<BigDecimal> findRate(CurrencyCode currency) {
            try {
                return Optional.of(rates.rateToUzs(currency));
            } catch (ExchangeRatesUnavailableException e) {
                return Optional.empty();
            }
        }
    }
}
