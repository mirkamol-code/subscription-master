package com.mirkamolcode.mapper;

import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.entity.PriceChange;
import com.mirkamolcode.entity.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {
    @Mapping(target = "paymentMethod", expression = "java(toPaymentMethod(subscription))")
    @Mapping(target = "latestPriceChange", expression = "java(toPriceChangeSummary(subscription.getLatestPriceChange()))")
    SubscriptionResponse toResponse(Subscription subscription);

    default SubscriptionResponse.PriceChangeSummary toPriceChangeSummary(PriceChange change) {
        if (change == null)
            return null;
        return new SubscriptionResponse.PriceChangeSummary(change.getOldAmount(), change.getNewAmount(), change.getCurrency(),
                change.getEffectiveOn(), change.getSource());
    }

    default SubscriptionResponse.PaymentMethod toPaymentMethod(Subscription subscription) {
        if (subscription.getPaymentMethodType() == null)
            return null;
        return new SubscriptionResponse.PaymentMethod(subscription.getPaymentMethodType(), subscription.getCardBrand(),
                subscription.getCardLast4());
    }
}
