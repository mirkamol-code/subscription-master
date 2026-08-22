package com.mirkamolcode.bootstrap;

import com.mirkamolcode.entity.PaymentHistory;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataLoader implements ApplicationRunner {
    private final UserRepository users;
    private final PaymentHistoryRepository historyRepository;
    private final SubscriptionRepository subscriptions;
    private final PasswordEncoder passwordEncoder;

    public DataLoader(UserRepository users, PaymentHistoryRepository historyRepository, SubscriptionRepository subscriptions, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.historyRepository = historyRepository;
        this.subscriptions = subscriptions;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        User admin = users.save(
                new User(
                        "admin@subscribemaster.local",
                        passwordEncoder.encode("AdminPassword123!"),
                        Set.of(Role.ADMIN, Role.USER)
                )
        );

        User mirkamol = users.save(
                new User(
                        "hteg9188@gmail.com",
                        passwordEncoder.encode("DemoPassword123!"),
                        Set.of(Role.USER)
                )
        );

        Subscription netflix = subscriptions.save(
                new Subscription(
                        mirkamol,
                        "Netflix",
                        new BigDecimal("12.99"),
                        CurrencyCode.USD,
                        BillingFrequency.WEEKLY,
                        SubscriptionStatus.ACTIVE,
                        SubscriptionCategory.ENTERTAINMENT,
                        LocalDate.now().plusDays(2)
                )
        );


        Subscription chatGpt = subscriptions.save(
                new Subscription(
                        mirkamol,
                        "ChatGPT Plus",
                        new BigDecimal("20.00"),
                        CurrencyCode.USD,
                        BillingFrequency.MONTHLY,
                        SubscriptionStatus.ACTIVE,
                        SubscriptionCategory.AI_TOOLS,
                        LocalDate.now().plusDays(9)
                )
        );

        Subscription github = subscriptions.save(
                new Subscription(
                        mirkamol,
                        "GitHub Copilot",
                        new BigDecimal("10.00"),
                        CurrencyCode.USD,
                        BillingFrequency.MONTHLY,
                        SubscriptionStatus.ACTIVE,
                        SubscriptionCategory.AI_TOOLS,
                        LocalDate.now().plusDays(5)
                )
        );

        Subscription youtube = subscriptions.save(
                new Subscription(
                        mirkamol,
                        "YouTube Premium",
                        new BigDecimal("7.99"),
                        CurrencyCode.USD,
                        BillingFrequency.MONTHLY,
                        SubscriptionStatus.ACTIVE,
                        SubscriptionCategory.ENTERTAINMENT,
                        LocalDate.now().plusDays(12)
                )
        );

        Subscription notion = subscriptions.save(
                new Subscription(
                        mirkamol,
                        "Notion",
                        new BigDecimal("10.00"),
                        CurrencyCode.USD,
                        BillingFrequency.MONTHLY,
                        SubscriptionStatus.ACTIVE,
                        SubscriptionCategory.PRODUCTIVITY,
                        LocalDate.now().plusDays(18)
                )
        );

        Subscription spotify = subscriptions.save(
                new Subscription(
                        admin,
                        "Spotify",
                        new BigDecimal("5.99"),
                        CurrencyCode.USD,
                        BillingFrequency.MONTHLY,
                        SubscriptionStatus.PAUSED,
                        SubscriptionCategory.ENTERTAINMENT,
                        LocalDate.now().plusDays(15)
                )
        );

        Subscription aws = subscriptions.save(
                new Subscription(
                        admin,
                        "AWS",
                        new BigDecimal("25.00"),
                        CurrencyCode.USD,
                        BillingFrequency.MONTHLY,
                        SubscriptionStatus.ACTIVE,
                        SubscriptionCategory.FINANCE,
                        LocalDate.now().plusDays(7)
                )
        );

        historyRepository.save(
                new PaymentHistory(
                        netflix,
                        LocalDate.now().minusMonths(1),
                        new BigDecimal("12.99"),
                        CurrencyCode.USD,
                        new BigDecimal("12500.00")
                )
        );

        historyRepository.save(
                new PaymentHistory(
                        netflix,
                        LocalDate.now().minusMonths(2),
                        new BigDecimal("12.99"),
                        CurrencyCode.USD,
                        new BigDecimal("12450.00")
                )
        );

        historyRepository.save(
                new PaymentHistory(
                        chatGpt,
                        LocalDate.now().minusMonths(1),
                        new BigDecimal("20.00"),
                        CurrencyCode.USD,
                        new BigDecimal("12500.00")
                )
        );

        historyRepository.save(
                new PaymentHistory(
                        github,
                        LocalDate.now().minusMonths(1),
                        new BigDecimal("10.00"),
                        CurrencyCode.USD,
                        new BigDecimal("12500.00")
                )
        );

        historyRepository.save(
                new PaymentHistory(
                        youtube,
                        LocalDate.now().minusMonths(1),
                        new BigDecimal("7.99"),
                        CurrencyCode.USD,
                        new BigDecimal("12500.00")
                )
        );

        historyRepository.save(
                new PaymentHistory(
                        notion,
                        LocalDate.now().minusMonths(1),
                        new BigDecimal("10.00"),
                        CurrencyCode.USD,
                        new BigDecimal("12500.00")
                )
        );

        historyRepository.save(
                new PaymentHistory(
                        spotify,
                        LocalDate.now().minusMonths(2),
                        new BigDecimal("5.99"),
                        CurrencyCode.USD,
                        new BigDecimal("12450.00")
                )
        );

        historyRepository.save(
                new PaymentHistory(
                        aws,
                        LocalDate.now().minusMonths(1),
                        new BigDecimal("21.43"),
                        CurrencyCode.USD,
                        new BigDecimal("12500.00")
                )
        );
}
}
