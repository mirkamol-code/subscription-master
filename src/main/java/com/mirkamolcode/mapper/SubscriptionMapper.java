package com.mirkamolcode.mapper;

import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.entity.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {
    @Mapping(target = "paymentMethod", expression = "java(toPaymentMethod(subscription))")
    SubscriptionResponse toResponse(Subscription subscription);

    default SubscriptionResponse.PaymentMethod toPaymentMethod(Subscription subscription) {
        if (subscription.getPaymentMethodType() == null)
            return null;
        return new SubscriptionResponse.PaymentMethod(subscription.getPaymentMethodType(), subscription.getCardBrand(),
                subscription.getCardLast4());
    }
}
