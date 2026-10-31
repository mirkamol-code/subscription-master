package com.mirkamolcode.service;

import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.dto.response.CalendarResponse;
import com.mirkamolcode.dto.response.PaymentHistoryResponse;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.entity.BillingEmail;
import com.mirkamolcode.entity.PaymentHistory;
import com.mirkamolcode.dto.response.BillingEmailResponse;
import com.mirkamolcode.exception.ExchangeRatesUnavailableException;
import com.mirkamolcode.repository.BillingEmailRepository;
import com.mirkamolcode.repository.MerchantTombstoneRepository;
import com.mirkamolcode.entity.MerchantTombstone;
import com.mirkamolcode.entity.StatusChange;
import com.mirkamolcode.repository.StatusChangeRepository;
import com.mirkamolcode.dto.request.StatusChangeRequest;
import com.mirkamolcode.dto.response.StatusChangeResponse;
import com.mirkamolcode.mapper.SubscriptionMapper;
import com.mirkamolcode.exception.ConflictException;
import com.mirkamolcode.exception.NotFoundException;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.specification.SubscriptionSpecifications;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.Permission;
import com.mirkamolcode.model.QueryLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository repository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final SubscriptionMapper mapper;
    private final CurrentUserService currentUser;
    private final ExchangeRateService exchangeRateService;
    private final BillingEmailRepository billingEmailRepository;
    private final MerchantTombstoneRepository merchantTombstoneRepository;
    private final StatusChangeRepository statusChangeRepository;
    private final SubscriptionStatusService statusService;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository repository,
                               PaymentHistoryRepository paymentHistoryRepository,
                               SubscriptionMapper mapper,
                               CurrentUserService currentUser,
                               ExchangeRateService exchangeRateService,
                               BillingEmailRepository billingEmailRepository,
                               MerchantTombstoneRepository merchantTombstoneRepository,
                               StatusChangeRepository statusChangeRepository,
                               SubscriptionStatusService statusService,
                               Clock clock) {
        this.repository = repository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.exchangeRateService = exchangeRateService;
        this.billingEmailRepository = billingEmailRepository;
        this.merchantTombstoneRepository = merchantTombstoneRepository;
        this.statusChangeRepository = statusChangeRepository;
        this.statusService = statusService;
        this.clock = clock;
    }

    public Page<SubscriptionResponse> list(SubscriptionStatus status,
                                           CurrencyCode currency,
                                           BigDecimal minPrice,
                                           BigDecimal maxPrice,
                                           Pageable pageable) {
        return list(status, currency, null, null, null, null, minPrice, maxPrice, false, pageable);
    }

    public Page<SubscriptionResponse> list(SubscriptionStatus status,
                                           CurrencyCode currency,
                                           SubscriptionCategory category,
                                           String name,
                                           LocalDate nextPaymentDateFrom,
                                           LocalDate nextPaymentDateTo,
                                           BigDecimal minPrice,
                                           BigDecimal maxPrice,
                                           boolean hidden,
                                           Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new IllegalArgumentException("minPrice must not exceed maxPrice");

        Specification<Subscription> spec = buildSpec(status, currency, category, name, nextPaymentDateFrom, nextPaymentDateTo, minPrice, maxPrice, hidden);
        return repository.findAll(spec, pageable).map(mapper::toResponse);
    }

    public List<SubscriptionResponse> listAll(SubscriptionStatus status,
                                              CurrencyCode currency,
                                              SubscriptionCategory category,
                                              String name,
                                              LocalDate nextPaymentDateFrom,
                                              LocalDate nextPaymentDateTo,
                                              BigDecimal minPrice,
                                              BigDecimal maxPrice,
                                              boolean hidden) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new IllegalArgumentException("minPrice must not exceed maxPrice");

        Specification<Subscription> spec = buildSpec(status, currency, category, name, nextPaymentDateFrom, nextPaymentDateTo, minPrice, maxPrice, hidden);
        Pageable cappedPage = PageRequest.of(0, QueryLimits.MAX_UNPAGED_ROWS, Sort.by("nextPaymentDate", "id"));
        return repository.findAll(spec, cappedPage).stream().map(mapper::toResponse).toList();
    }

    public SubscriptionResponse get(Long id) {
        return mapper.toResponse(requiredAccessible(id, Permission.SUBSCRIPTION_READ_ALL));
    }

    @Transactional
    public SubscriptionResponse update(Long id, SubscriptionRequest request) {
        Subscription subscription = requiredAccessible(id, Permission.SUBSCRIPTION_UPDATE_ALL);
        if (request.version() != null && request.version() != subscription.getVersion())
            throw new ConflictException("Subscription was changed by someone else");
        subscription.update(request.name(), request.price(), request.currency(), request.frequency(), request.category(), request.startDate());
        if (request.status() != subscription.getStatus()) {
            if (request.version() == null)
                throw new ConflictException("Include the subscription version to change its status");
            statusService.markByUser(subscription, request.status());
        }
        repository.flush();
        return mapper.toResponse(subscription);
    }

    @Transactional
    public void delete(Long id) {
        Subscription subscription = requiredAccessible(id, Permission.SUBSCRIPTION_DELETE_ALL);
        subscription.markDeleted();
        if (subscription.getMerchantKey() != null)
            recordTombstone(subscription);
    }

    private void recordTombstone(Subscription subscription) {
        Instant deletedAt = clock.instant();
        merchantTombstoneRepository.findByUserIdAndMerchantKey(subscription.getUser().getId(), subscription.getMerchantKey())
                .ifPresentOrElse(tombstone -> tombstone.markDeletedAgain(deletedAt),
                        () -> merchantTombstoneRepository.save(
                                new MerchantTombstone(subscription.getUser(), subscription.getMerchantKey(), deletedAt)));
    }

    @Transactional
    public SubscriptionResponse changeStatus(Long id, StatusChangeRequest request) {
        Subscription subscription = requiredAccessible(id, Permission.SUBSCRIPTION_UPDATE_ALL);
        statusService.markByUser(subscription, request.status());
        return mapper.toResponse(subscription);
    }

    public List<StatusChangeResponse> findStatusHistory(Long id) {
        Subscription subscription = requiredAccessible(id, Permission.SUBSCRIPTION_READ_ALL);
        return statusChangeRepository.findBySubscriptionIdOrderByEffectiveAtDescIdDesc(subscription.getId())
                .stream()
                .map(SubscriptionService::mapToStatusChangeResponse)
                .toList();
    }

    @Transactional
    public SubscriptionResponse hide(Long id) {
        Subscription subscription = requiredAccessible(id, Permission.SUBSCRIPTION_UPDATE_ALL);
        subscription.hide();
        return mapper.toResponse(subscription);
    }

    @Transactional
    public SubscriptionResponse unhide(Long id) {
        Subscription subscription = requiredAccessible(id, Permission.SUBSCRIPTION_UPDATE_ALL);
        subscription.unhide();
        return mapper.toResponse(subscription);
    }

    public List<BillingEmailResponse> findEmails(Long id) {
        Subscription subscription = requiredAccessible(id, Permission.SUBSCRIPTION_READ_ALL);
        return billingEmailRepository.findBySubscriptionIdOrderByReceivedAtDesc(subscription.getId())
                .stream()
                .map(SubscriptionService::mapToBillingEmailResponse)
                .toList();
    }

    public List<PaymentHistoryResponse> getHistory(Long id) {
        Subscription subscription = requiredAccessible(id, Permission.SUBSCRIPTION_READ_ALL);
        return paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(subscription.getId())
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

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

    public CalendarResponse calendar(int year, int month) {
        PeriodValidation.requireSupportedYear(year);
        PeriodValidation.requireValidMonth(month);

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

    private Specification<Subscription> buildSpec(SubscriptionStatus status,
                                                   CurrencyCode currency,
                                                   SubscriptionCategory category,
                                                   String name,
                                                   LocalDate nextPaymentDateFrom,
                                                   LocalDate nextPaymentDateTo,
                                                   BigDecimal minPrice,
                                                   BigDecimal maxPrice,
                                                   boolean hidden) {
        Specification<Subscription> spec = (hidden ? SubscriptionSpecifications.hiddenByUser() : SubscriptionSpecifications.visible())
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
                try {
                    rates.put(c, exchangeRateService.rateToUzs(c));
                } catch (ExchangeRatesUnavailableException e) {
                    log.warn("Price filter skips {} subscriptions: no exchange rate available", c);
                }
            }
            spec = spec.and(SubscriptionSpecifications.priceInUzsAtLeast(minPrice, rates))
                    .and(SubscriptionSpecifications.priceInUzsAtMost(maxPrice, rates));
        }

        if (!currentUser.hasPermission(Permission.SUBSCRIPTION_READ_ALL))
            spec = SubscriptionSpecifications.ownedBy(currentUser.requiredUser().getId()).and(spec);

        return spec;
    }

    private static StatusChangeResponse mapToStatusChangeResponse(StatusChange change) {
        BillingEmail email = change.getBillingEmail();
        return new StatusChangeResponse(change.getId(), change.getFromStatus(), change.getToStatus(), change.getReason(),
                change.getReason().source(), change.getEffectiveAt(), change.getEndsOn(),
                email == null ? null : email.getId(), email == null ? null : email.getGmailMessageId(),
                email == null ? null : email.getSender(), email == null ? null : email.getSubject(),
                email == null ? null : email.getReceivedAt());
    }

    private static BillingEmailResponse mapToBillingEmailResponse(BillingEmail email) {
        List<BillingEmailResponse.Attachment> attachments = email.getAttachments().stream()
                .map(attachment -> new BillingEmailResponse.Attachment(
                        attachment.getId(), attachment.getFilename(), attachment.getMimeType(), attachment.getSizeBytes()))
                .toList();
        return new BillingEmailResponse(email.getId(), email.getGmailMessageId(), email.getKind(), email.getSender(),
                email.getSubject(), email.getReceivedAt(), email.getConfidence(), attachments);
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
