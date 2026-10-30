package com.mirkamolcode.service;

import com.mirkamolcode.dto.request.NotificationSettingsRequest;
import com.mirkamolcode.dto.response.NotificationSettingsResponse;
import com.mirkamolcode.entity.NotificationSettings;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.repository.NotificationSettingsRepository;
import com.mirkamolcode.repository.UserRepository;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationSettingsService {
    private final NotificationSettingsRepository settings;
    private final UserRepository users;
    private final CurrentUserService currentUser;

    public NotificationSettingsService(NotificationSettingsRepository settings, UserRepository users, CurrentUserService currentUser) {
        this.settings = settings;
        this.users = users;
        this.currentUser = currentUser;
    }

    public NotificationSettings findOrDefault(Long userId) {
        return settings.findById(userId).orElseGet(() -> new NotificationSettings(users.getReferenceById(userId)));
    }

    public NotificationSettingsResponse findForCurrentUser() {
        User user = currentUser.requiredUser();
        return mapToNotificationSettingsResponse(findOrDefault(user.getId()), user);
    }

    @Transactional
    public NotificationSettingsResponse update(NotificationSettingsRequest request) {
        User user = currentUser.requiredUser();
        ZoneId zone = parseZone(request.timeZone());
        NotificationSettings userSettings = settings.findById(user.getId()).orElseGet(() -> new NotificationSettings(user));
        userSettings.change(request.emailEnabled(), request.inAppEnabled(), request.pushEnabled(), request.daysBefore());
        user.changeTimeZone(zone);
        return mapToNotificationSettingsResponse(settings.save(userSettings), user);
    }

    private static ZoneId parseZone(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Unknown time zone: " + timeZone);
        }
    }

    private static NotificationSettingsResponse mapToNotificationSettingsResponse(NotificationSettings userSettings, User user) {
        return new NotificationSettingsResponse(userSettings.isEmailEnabled(), userSettings.isInAppEnabled(),
                userSettings.isPushEnabled(), userSettings.getDaysBefore(), user.getTimeZone().getId());
    }
}
