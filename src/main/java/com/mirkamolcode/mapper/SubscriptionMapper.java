package com.mirkamolcode.mapper;

import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.entity.Subscription;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {
    SubscriptionResponse toResponse(Subscription subscription);
}
