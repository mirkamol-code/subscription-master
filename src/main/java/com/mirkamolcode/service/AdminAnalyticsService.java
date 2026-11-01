package com.mirkamolcode.service;

import com.mirkamolcode.dto.response.CategoryAnalyticsResponse;
import com.mirkamolcode.dto.response.MerchantAnalyticsResponse;
import com.mirkamolcode.model.AnalyticsRange;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.repository.AdminAnalyticsRepository;
import com.mirkamolcode.repository.projection.CategoryActivity;
import com.mirkamolcode.repository.projection.MerchantActivity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminAnalyticsService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int AVERAGE_SCALE = 2;

    private final AdminAnalyticsRepository analytics;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public AdminAnalyticsService(AdminAnalyticsRepository analytics, CurrentUserService currentUser, Clock clock) {
        this.analytics = analytics;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public List<MerchantAnalyticsResponse> rankMerchants(AnalyticsRange range, SubscriptionCategory category) {
        Instant now = clock.instant();
        List<MerchantActivity> rows = analytics.summariseByMerchant(
                category == null ? null : category.name(),
                AnalyticsRange.LAST_7_DAYS.calculateStart(now),
                AnalyticsRange.LAST_30_DAYS.calculateStart(now),
                range.calculateStart(now),
                range.calculateFirstDay(findAdminToday(now)));
        long totalActive = rows.stream().mapToLong(MerchantActivity::getActiveSubscriptions).sum();
        return IntStream.range(0, rows.size())
                .mapToObj(index -> mapToMerchantResponse(index + 1, rows.get(index), totalActive))
                .toList();
    }

    public List<CategoryAnalyticsResponse> rankCategories(AnalyticsRange range) {
        List<CategoryActivity> rows = analytics.summariseByCategory(range.calculateFirstDay(findAdminToday(clock.instant())));
        long totalActive = rows.stream().mapToLong(CategoryActivity::getActiveSubscriptions).sum();
        return rows.stream().map(row -> mapToCategoryResponse(row, totalActive)).toList();
    }

    private LocalDate findAdminToday(Instant now) {
        return LocalDate.ofInstant(now, currentUser.requiredUser().getTimeZone());
    }

    private static MerchantAnalyticsResponse mapToMerchantResponse(int rank, MerchantActivity row, long totalActive) {
        return new MerchantAnalyticsResponse(rank, row.getSlug(), row.getName(),
                row.getActiveSubscribers(), row.getActiveSubscriptions(),
                calculateShare(row.getActiveSubscriptions(), totalActive),
                row.getNewLast7Days(), row.getNewLast30Days(), row.getCancelledInRange(),
                row.getTrackedSpendUzs(),
                averageOrNull(row.getTrackedSpendUzs(), row.getPayingUsers()),
                averageOrNull(BigDecimal.valueOf(row.getLogins()), row.getActiveSubscribers()));
    }

    private static CategoryAnalyticsResponse mapToCategoryResponse(CategoryActivity row, long totalActive) {
        return new CategoryAnalyticsResponse(SubscriptionCategory.valueOf(row.getCategory()),
                row.getActiveSubscribers(), row.getActiveSubscriptions(),
                calculateShare(row.getActiveSubscriptions(), totalActive), row.getTrackedSpendUzs());
    }

    private static BigDecimal calculateShare(long part, long total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(part).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageOrNull(BigDecimal amount, long count) {
        return count == 0 ? null : amount.divide(BigDecimal.valueOf(count), AVERAGE_SCALE, RoundingMode.HALF_UP);
    }
}
