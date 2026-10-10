package com.mirkamolcode.bootstrap;

import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.entity.PaymentHistory;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.ExchangeRateRepository;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!test")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final UserRepository users;
    private final PaymentHistoryRepository historyRepository;
    private final SubscriptionRepository subscriptions;
    private final ExchangeRateRepository exchangeRates;
    private final PasswordEncoder passwordEncoder;

    private record SubscriptionSeed(
            String name,
            String price,
            CurrencyCode currency,
            BillingFrequency frequency,
            SubscriptionStatus status,
            SubscriptionCategory category,
            int startMonthsOffset,
            int nextDaysOffset
    ) {}

    private static final List<SubscriptionSeed> SEED_SUBSCRIPTIONS = List.of(
            new SubscriptionSeed("Netflix Premium", "22.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -18, 5),
            new SubscriptionSeed("Spotify Individual", "11.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -12, 12),
            new SubscriptionSeed("YouTube Premium Family", "22.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -14, 8),
            new SubscriptionSeed("Disney+ Standard", "9.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -10, 16),
            new SubscriptionSeed("HBO Max Standard", "15.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -8, 20),
            new SubscriptionSeed("Apple TV+", "9.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -6, 22),
            new SubscriptionSeed("Amazon Prime Video", "8.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -15, 14),
            new SubscriptionSeed("Hulu No Ads", "17.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.PAUSED, SubscriptionCategory.ENTERTAINMENT, -9, 18),
            new SubscriptionSeed("Paramount+ with Showtime", "11.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -7, 25),
            new SubscriptionSeed("Crunchyroll Mega Fan", "9.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -11, 3),
            new SubscriptionSeed("Tidal HiFi Plus", "19.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -5, 27),
            new SubscriptionSeed("Deezer Premium", "10.99", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -13, 9),
            new SubscriptionSeed("Twitch Turbo", "11.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -4, 2),
            new SubscriptionSeed("PlayStation Plus Extra", "134.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -8, 120),
            new SubscriptionSeed("Xbox Game Pass Ultimate", "19.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -16, 7),
            new SubscriptionSeed("Nintendo Switch Online", "19.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -20, 160),
            new SubscriptionSeed("Audible Premium Plus", "14.95", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -17, 11),
            new SubscriptionSeed("Peacock Premium Plus", "11.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -3, 29),
            new SubscriptionSeed("Discovery+ Ad-Free", "8.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.CANCELLED, SubscriptionCategory.ENTERTAINMENT, -12, 30),
            new SubscriptionSeed("SoundCloud Go+", "9.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -6, 15),
            new SubscriptionSeed("MUBI Cinema Club", "14.99", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -5, 19),
            new SubscriptionSeed("Curiosity Stream", "39.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -14, 210),
            new SubscriptionSeed("ESPN+ Monthly", "10.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -8, 4),
            new SubscriptionSeed("DAZN Standard", "29.99", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -4, 26),
            new SubscriptionSeed("NBA League Pass", "14.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.PAUSED, SubscriptionCategory.ENTERTAINMENT, -6, 24),
            new SubscriptionSeed("Criterion Channel", "10.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -7, 13),
            new SubscriptionSeed("Shudder Monthly", "6.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -9, 21),
            new SubscriptionSeed("BritBox Annual", "89.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -11, 280),
            new SubscriptionSeed("Rakuten Viki Pass Plus", "9.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -3, 17),
            new SubscriptionSeed("Yandex Plus Uzbekistan", "24999.00", CurrencyCode.UZS, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -15, 6),
            new SubscriptionSeed("Kinopoisk HD Max", "39999.00", CurrencyCode.UZS, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -8, 10),
            new SubscriptionSeed("Allplay TV Premium", "45000.00", CurrencyCode.UZS, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -5, 23),
            new SubscriptionSeed("iTV Uzbekistan VIP", "50000.00", CurrencyCode.UZS, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -7, 1),
            new SubscriptionSeed("Apple Music Family", "16.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -18, 12),
            new SubscriptionSeed("Qobuz Studio Sublime", "179.99", CurrencyCode.EUR, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, -10, 190),
            new SubscriptionSeed("Notion Plus Plan", "10.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -14, 15),
            new SubscriptionSeed("Slack Pro Workspace", "8.75", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -20, 10),
            new SubscriptionSeed("Trello Standard", "5.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -16, 25),
            new SubscriptionSeed("Asana Starter", "10.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -11, 8),
            new SubscriptionSeed("Monday.com Standard", "12.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -9, 14),
            new SubscriptionSeed("Linear Standard Team", "8.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -7, 21),
            new SubscriptionSeed("Jira Cloud Standard", "7.75", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -13, 28),
            new SubscriptionSeed("Zoom Pro Plan", "15.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -22, 5),
            new SubscriptionSeed("Google Workspace Business", "14.40", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -18, 1),
            new SubscriptionSeed("Microsoft 365 Personal", "69.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -15, 240),
            new SubscriptionSeed("Loom Business", "12.50", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -8, 11),
            new SubscriptionSeed("Miro Starter", "8.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -6, 17),
            new SubscriptionSeed("Figma Professional", "12.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -12, 4),
            new SubscriptionSeed("Canva Pro Individual", "12.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -10, 20),
            new SubscriptionSeed("Evernote Personal", "14.99", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.PAUSED, SubscriptionCategory.PRODUCTIVITY, -24, 16),
            new SubscriptionSeed("Todoist Pro", "4.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -15, 9),
            new SubscriptionSeed("Obsidian Sync", "8.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -7, 23),
            new SubscriptionSeed("1Password Individual", "35.88", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -19, 150),
            new SubscriptionSeed("Bitwarden Premium", "10.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -25, 300),
            new SubscriptionSeed("Dropbox Plus 2TB", "11.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -17, 13),
            new SubscriptionSeed("Box Personal Pro", "10.00", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -8, 27),
            new SubscriptionSeed("ClickUp Unlimited", "7.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -11, 6),
            new SubscriptionSeed("Basecamp Standard", "15.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -5, 18),
            new SubscriptionSeed("Airtable Team Plan", "20.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -9, 22),
            new SubscriptionSeed("Coda Pro Maker", "10.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -4, 30),
            new SubscriptionSeed("Craft Docs Pro", "5.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -6, 2),
            new SubscriptionSeed("Bear Pro Notes", "14.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -13, 110),
            new SubscriptionSeed("Raycast Pro Developer", "8.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -5, 12),
            new SubscriptionSeed("Superhuman Email", "30.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -7, 19),
            new SubscriptionSeed("Grammarly Business", "15.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -14, 26),
            new SubscriptionSeed("Otter.ai Pro Transcription", "10.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.CANCELLED, SubscriptionCategory.PRODUCTIVITY, -8, 29),
            new SubscriptionSeed("Notion AI Addon", "10.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -6, 15),
            new SubscriptionSeed("Mailchimp Essentials", "13.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -12, 7),
            new SubscriptionSeed("Zapier Professional", "29.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.PAUSED, SubscriptionCategory.PRODUCTIVITY, -10, 16),
            new SubscriptionSeed("Make.com Pro", "16.00", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.PRODUCTIVITY, -4, 24),
            new SubscriptionSeed("ChatGPT Plus", "20.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -15, 9),
            new SubscriptionSeed("GitHub Copilot Individual", "10.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -16, 5),
            new SubscriptionSeed("Claude Pro Anthropic", "20.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -9, 14),
            new SubscriptionSeed("Midjourney Standard Plan", "30.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -12, 18),
            new SubscriptionSeed("Cursor Pro IDE", "20.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -6, 11),
            new SubscriptionSeed("Perplexity Pro", "20.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -8, 22),
            new SubscriptionSeed("Jasper AI Creator", "39.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.PAUSED, SubscriptionCategory.AI_TOOLS, -10, 25),
            new SubscriptionSeed("Runway Gen-2 Standard", "12.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -7, 17),
            new SubscriptionSeed("ElevenLabs Starter", "5.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -11, 3),
            new SubscriptionSeed("Poe by Quora Subscription", "19.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -5, 26),
            new SubscriptionSeed("DeepL Pro Starter", "7.49", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -13, 8),
            new SubscriptionSeed("Grammarly Premium AI", "12.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -17, 20),
            new SubscriptionSeed("Tabnine Pro AI Assistant", "12.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -14, 13),
            new SubscriptionSeed("Phind Pro Assistant", "20.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -4, 27),
            new SubscriptionSeed("Replit Core Cloud", "20.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -9, 2),
            new SubscriptionSeed("v0.dev Premium Vercel", "20.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -3, 16),
            new SubscriptionSeed("Lovable Scale Plan", "25.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -2, 23),
            new SubscriptionSeed("Bolt.new Pro WebContainer", "20.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -2, 28),
            new SubscriptionSeed("OpenAI API Tier 1", "50.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -12, 1),
            new SubscriptionSeed("Anthropic API Tier 1", "50.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -8, 1),
            new SubscriptionSeed("Hugging Face Pro", "9.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -11, 19),
            new SubscriptionSeed("Krea AI Pro", "30.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -4, 24),
            new SubscriptionSeed("Leonardo AI Apprentice", "10.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -6, 7),
            new SubscriptionSeed("Mistral Le Chat Pro", "15.00", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -3, 30),
            new SubscriptionSeed("Synthesia Starter Video", "22.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.CANCELLED, SubscriptionCategory.AI_TOOLS, -7, 15),
            new SubscriptionSeed("Descript Creator Plan", "12.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -10, 10),
            new SubscriptionSeed("Beautiful.ai Pro Presentation", "12.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -8, 21),
            new SubscriptionSeed("Writesonic Small Team", "19.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.PAUSED, SubscriptionCategory.AI_TOOLS, -9, 4),
            new SubscriptionSeed("Copy.ai Pro Workspace", "36.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -13, 29),
            new SubscriptionSeed("HeyGen Creator Plan", "29.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -5, 12),
            new SubscriptionSeed("PlayHT Professional", "31.20", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -4, 18),
            new SubscriptionSeed("Suno AI Pro Plan", "8.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -6, 6),
            new SubscriptionSeed("Udio Standard Music", "10.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -3, 22),
            new SubscriptionSeed("Luma Dream Machine Pro", "29.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -2, 25),
            new SubscriptionSeed("Meshy 3D AI Pro", "16.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.AI_TOOLS, -3, 14),
            new SubscriptionSeed("Coursera Plus Annual", "399.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -14, 180),
            new SubscriptionSeed("Udemy Business Personal", "16.58", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -10, 12),
            new SubscriptionSeed("Duolingo Super Family", "119.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -18, 90),
            new SubscriptionSeed("DataCamp Premium Learner", "25.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -12, 19),
            new SubscriptionSeed("LeetCode Premium Annual", "159.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -15, 230),
            new SubscriptionSeed("Brilliant.org Premium", "13.49", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -8, 24),
            new SubscriptionSeed("Skillshare Annual", "168.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -16, 140),
            new SubscriptionSeed("MasterClass Annual Solo", "120.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.PAUSED, SubscriptionCategory.EDUCATION, -11, 270),
            new SubscriptionSeed("Pluralsight Standard Skills", "29.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -9, 8),
            new SubscriptionSeed("Codecademy Pro", "17.49", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -13, 22),
            new SubscriptionSeed("Medium Membership", "5.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -21, 15),
            new SubscriptionSeed("Frontend Masters Monthly", "39.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -7, 6),
            new SubscriptionSeed("Educative.io Unlimited", "16.66", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -6, 28),
            new SubscriptionSeed("Babbel Standard Russian", "12.99", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -10, 14),
            new SubscriptionSeed("Rosetta Stone Unlimited", "11.99", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.PAUSED, SubscriptionCategory.EDUCATION, -14, 30),
            new SubscriptionSeed("LinkedIn Learning", "19.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -17, 2),
            new SubscriptionSeed("Chess.com Diamond", "99.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -20, 115),
            new SubscriptionSeed("CodeCrafters Membership", "40.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -4, 17),
            new SubscriptionSeed("O'Reilly Learning Platform", "49.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -5, 23),
            new SubscriptionSeed("Blinkist Premium Annual", "99.99", CurrencyCode.EUR, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -12, 310),
            new SubscriptionSeed("Headway App Growth", "14.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -3, 11),
            new SubscriptionSeed("Shortform Book Summaries", "16.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.CANCELLED, SubscriptionCategory.EDUCATION, -9, 26),
            new SubscriptionSeed("ELSA Speak Pro English", "65.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -11, 200),
            new SubscriptionSeed("Najot Ta'lim Online Pro", "350000.00", CurrencyCode.UZS, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -7, 5),
            new SubscriptionSeed("Mohirdev Pro Subscription", "150000.00", CurrencyCode.UZS, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.EDUCATION, -6, 18),
            new SubscriptionSeed("Strava Summit Pro", "79.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -15, 80),
            new SubscriptionSeed("MyFitnessPal Premium", "19.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -11, 14),
            new SubscriptionSeed("Headspace Plus Mindfulness", "12.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -14, 21),
            new SubscriptionSeed("Calm Premium Meditation", "69.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -18, 175),
            new SubscriptionSeed("WHOOP 4.0 Membership", "30.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -8, 9),
            new SubscriptionSeed("Apple Fitness+ Individual", "9.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -12, 17),
            new SubscriptionSeed("Fitbod Elite Workout", "12.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -6, 25),
            new SubscriptionSeed("Peloton App One", "12.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.PAUSED, SubscriptionCategory.HEALTH, -10, 4),
            new SubscriptionSeed("Noom Weight Program", "70.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.CANCELLED, SubscriptionCategory.HEALTH, -5, 29),
            new SubscriptionSeed("Flo Premium Period Tracker", "49.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -16, 220),
            new SubscriptionSeed("Sleep Cycle Premium", "39.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -20, 195),
            new SubscriptionSeed("Oura Ring Horizon Sub", "5.99", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -9, 13),
            new SubscriptionSeed("Daily Burn 365", "19.95", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -7, 28),
            new SubscriptionSeed("Alo Moves Yoga", "20.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -4, 8),
            new SubscriptionSeed("Centr by Chris Hemsworth", "29.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.PAUSED, SubscriptionCategory.HEALTH, -8, 19),
            new SubscriptionSeed("Sweat App Fitness", "19.99", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -6, 2),
            new SubscriptionSeed("Nike Training Club Pass", "14.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -11, 23),
            new SubscriptionSeed("Clue Plus Cycle Tracking", "39.99", CurrencyCode.EUR, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -13, 160),
            new SubscriptionSeed("Simple Intermittent Fasting", "17.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -5, 7),
            new SubscriptionSeed("Fabulous Daily Routine", "40.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -15, 290),
            new SubscriptionSeed("Freeletics Training Coach", "24.99", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -7, 16),
            new SubscriptionSeed("Lifesum Premium Nutrition", "49.99", CurrencyCode.EUR, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -12, 135),
            new SubscriptionSeed("Cronometer Gold", "8.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -9, 27),
            new SubscriptionSeed("Zero Fasting Plus", "69.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -14, 185),
            new SubscriptionSeed("Waking Up Sam Harris", "139.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, -17, 70),
            new SubscriptionSeed("TradingView Pro Plus", "29.95", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -16, 11),
            new SubscriptionSeed("YNAB Budgeting App", "98.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -22, 125),
            new SubscriptionSeed("CoinMarketCap Diamond", "15.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -8, 26),
            new SubscriptionSeed("Koyfin Basic Analytics", "25.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -10, 18),
            new SubscriptionSeed("Seeking Alpha Premium", "239.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -13, 215),
            new SubscriptionSeed("PocketGuard Plus", "7.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -9, 3),
            new SubscriptionSeed("Copilot Money Mac", "95.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -7, 190),
            new SubscriptionSeed("Monarch Money Family", "99.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -11, 260),
            new SubscriptionSeed("Lunch Money Developer", "10.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -6, 22),
            new SubscriptionSeed("Morningstar Investor", "249.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.PAUSED, SubscriptionCategory.FINANCE, -15, 140),
            new SubscriptionSeed("Wall Street Journal Digital", "38.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -18, 9),
            new SubscriptionSeed("Financial Times Digital", "40.00", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -14, 14),
            new SubscriptionSeed("The Economist Digital", "29.90", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -12, 28),
            new SubscriptionSeed("Barron's Digital Access", "19.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -8, 20),
            new SubscriptionSeed("Simply Wall St Unlimited", "119.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -10, 310),
            new SubscriptionSeed("Motley Fool Stock Advisor", "199.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -17, 180),
            new SubscriptionSeed("Finviz Elite Financials", "39.50", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -9, 7),
            new SubscriptionSeed("StockRover Premium", "27.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.PAUSED, SubscriptionCategory.FINANCE, -5, 16),
            new SubscriptionSeed("TrendSpider Technical", "49.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -6, 24),
            new SubscriptionSeed("Benzinga Pro Basic", "79.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.CANCELLED, SubscriptionCategory.FINANCE, -4, 30),
            new SubscriptionSeed("QuickBooks Simple Start", "30.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -19, 13),
            new SubscriptionSeed("Xero Early Ledger", "15.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -11, 2),
            new SubscriptionSeed("Kapitalbank VIP Card Sub", "75000.00", CurrencyCode.UZS, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -8, 5),
            new SubscriptionSeed("TBC Bank Pro Package", "50000.00", CurrencyCode.UZS, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -10, 15),
            new SubscriptionSeed("Payme Plus Service", "25000.00", CurrencyCode.UZS, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.FINANCE, -12, 25),
            new SubscriptionSeed("Amazon Prime Delivery", "14.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -20, 12),
            new SubscriptionSeed("Uber One Membership", "9.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -11, 28),
            new SubscriptionSeed("NordVPN 2-Year Standard", "83.76", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -15, 200),
            new SubscriptionSeed("ExpressVPN 12 Months", "99.95", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -18, 170),
            new SubscriptionSeed("Surfshark VPN One", "47.88", CurrencyCode.EUR, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -9, 290),
            new SubscriptionSeed("CleanMyMac X License", "39.95", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -16, 140),
            new SubscriptionSeed("JetBrains All Products Pack", "289.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -24, 250),
            new SubscriptionSeed("DigitalOcean Basic Droplet", "12.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -14, 1),
            new SubscriptionSeed("Vercel Pro Developer", "20.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -10, 19),
            new SubscriptionSeed("Cloudflare Pro Plan", "25.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -8, 15),
            new SubscriptionSeed("GitHub Pro Individual", "4.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -13, 22),
            new SubscriptionSeed("Proton Unlimited Privacy", "11.99", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -7, 6),
            new SubscriptionSeed("Mullvad VPN Secure", "5.00", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -12, 27),
            new SubscriptionSeed("Backblaze Computer Backup", "99.00", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -19, 130),
            new SubscriptionSeed("Setapp Mac Apps Suite", "9.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -15, 10),
            new SubscriptionSeed("Google One 2TB Storage", "99.99", CurrencyCode.USD, BillingFrequency.ANNUAL, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -17, 240),
            new SubscriptionSeed("iCloud+ 200GB Family", "2.99", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -22, 18),
            new SubscriptionSeed("Hetzner Cloud CX22", "4.55", CurrencyCode.EUR, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -6, 29),
            new SubscriptionSeed("Supabase Pro Tier", "25.00", CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -8, 8),
            new SubscriptionSeed("Ucell VIP Internet Tariff", "120000.00", CurrencyCode.UZS, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, -10, 1)
    );

    public DataLoader(UserRepository users,
                      PaymentHistoryRepository historyRepository,
                      SubscriptionRepository subscriptions,
                      ExchangeRateRepository exchangeRates,
                      PasswordEncoder passwordEncoder) {
        this.users = users;
        this.historyRepository = historyRepository;
        this.subscriptions = subscriptions;
        this.exchangeRates = exchangeRates;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedExchangeRates();

        User admin = users.findByEmailIgnoreCase("admin@subscribemaster.local")
                .orElseGet(() -> users.save(new User(
                        "admin@subscribemaster.local",
                        passwordEncoder.encode("AdminPassword123!"),
                        Set.of(Role.ADMIN, Role.USER)
                )));

        User mirkamol = users.findByEmailIgnoreCase("hteg9188@gmail.com")
                .orElseGet(() -> users.save(new User(
                        "hteg9188@gmail.com",
                        passwordEncoder.encode("DemoPassword123!"),
                        Set.of(Role.USER)
                )));

        if (subscriptions.count() >= 200) {
            log.info("Database already contains {} subscriptions. Skipping data seeding.", subscriptions.count());
            return;
        }

        log.info("Seeding 200 subscriptions for user {}...", mirkamol.getEmail());
        LocalDate today = LocalDate.now();

        int count = 0;
        for (SubscriptionSeed seed : SEED_SUBSCRIPTIONS) {
            LocalDate startDate = today.plusMonths(seed.startMonthsOffset());
            LocalDate nextPaymentDate = today.plusDays(seed.nextDaysOffset());

            Subscription sub = subscriptions.save(new Subscription(
                    mirkamol,
                    seed.name(),
                    new BigDecimal(seed.price()),
                    seed.currency(),
                    seed.frequency(),
                    seed.status(),
                    seed.category(),
                    startDate,
                    nextPaymentDate
            ));

            // Seed historical payments for the first 30 subscriptions
            if (count < 30) {
                seedPaymentHistoryFor(sub, today);
            }
            count++;
        }

        log.info("Successfully seeded {} subscriptions and historical payments.", count);
    }

    private void seedExchangeRates() {
        LocalDate today = LocalDate.now();
        if (exchangeRates.findFirstByCurrencyOrderByRateDateDesc(CurrencyCode.USD).isEmpty()) {
            exchangeRates.save(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12850.000000"), today));
        }
        if (exchangeRates.findFirstByCurrencyOrderByRateDateDesc(CurrencyCode.EUR).isEmpty()) {
            exchangeRates.save(new ExchangeRate(CurrencyCode.EUR, new BigDecimal("13967.500000"), today));
        }
    }

    private void seedPaymentHistoryFor(Subscription sub, LocalDate today) {
        BigDecimal rate = switch (sub.getCurrency()) {
            case USD -> new BigDecimal("12850.000000");
            case EUR -> new BigDecimal("13967.500000");
            case UZS -> BigDecimal.ONE;
        };

        historyRepository.save(new PaymentHistory(
                sub,
                today.minusMonths(1),
                sub.getPrice(),
                sub.getCurrency(),
                rate
        ));

        historyRepository.save(new PaymentHistory(
                sub,
                today.minusMonths(2),
                sub.getPrice(),
                sub.getCurrency(),
                rate
        ));
    }
}
