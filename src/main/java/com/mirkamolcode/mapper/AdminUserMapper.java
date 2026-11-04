package com.mirkamolcode.mapper;

import com.mirkamolcode.dto.response.AdminPaymentResponse;
import com.mirkamolcode.dto.response.AdminSubscriptionResponse;
import com.mirkamolcode.dto.response.AdminUserResponse;
import com.mirkamolcode.dto.response.LoginEventResponse;
import com.mirkamolcode.entity.LoginEvent;
import com.mirkamolcode.entity.PaymentHistory;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.security.UserAgentParser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = Role.class)
public interface AdminUserMapper {
    @Mapping(target = "role", expression = "java(user.isAdmin() ? Role.ADMIN : Role.USER)")
    @Mapping(target = "timeZone", expression = "java(user.getTimeZone().getId())")
    AdminUserResponse toResponse(User user);

    default LoginEventResponse toResponse(LoginEvent event) {
        if (event == null) {
            return null;
        }
        UserAgentParser.ClientInfo client = UserAgentParser.parse(event.getUserAgent());
        return new LoginEventResponse(
                event.getId(),
                event.getOccurredAt(),
                event.getIp(),
                event.getUserAgent(),
                client.device(),
                client.browser(),
                client.os()
        );
    }

    @Mapping(target = "merchant", source = "merchantSlug")
    AdminSubscriptionResponse toResponse(Subscription subscription);

    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "subscriptionName", source = "subscription.name")
    @Mapping(target = "paidOn", source = "paymentDate")
    @Mapping(target = "amount", source = "originalAmount")
    @Mapping(target = "currency", source = "originalCurrency")
    AdminPaymentResponse toResponse(PaymentHistory payment);
}
