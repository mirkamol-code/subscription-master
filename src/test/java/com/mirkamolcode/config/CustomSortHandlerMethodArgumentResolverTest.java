package com.mirkamolcode.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class CustomSortHandlerMethodArgumentResolverTest {

    @Test
    void parseSort_shouldParseCommaSeparatedFieldAndDirection() {
        Sort sort = CustomSortHandlerMethodArgumentResolver.parseSort(new String[]{"nextPaymentDate,ASC"});
        assertThat(sort.isSorted()).isTrue();
        assertThat(sort.getOrderFor("nextPaymentDate")).isNotNull();
        assertThat(sort.getOrderFor("nextPaymentDate").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void parseSort_shouldParseArraySerializedFieldAndDirection() {
        Sort sort = CustomSortHandlerMethodArgumentResolver.parseSort(new String[]{"nextPaymentDate", "desc"});
        assertThat(sort.isSorted()).isTrue();
        assertThat(sort.getOrderFor("nextPaymentDate")).isNotNull();
        assertThat(sort.getOrderFor("nextPaymentDate").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void parseSort_shouldMapCaseInsensitiveAndSnakeCaseProperties() {
        Sort sort = CustomSortHandlerMethodArgumentResolver.parseSort(new String[]{"nextpaymentdate,asc", "start_date,desc"});
        assertThat(sort.isSorted()).isTrue();
        assertThat(sort.getOrderFor("nextPaymentDate")).isNotNull();
        assertThat(sort.getOrderFor("nextPaymentDate").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getOrderFor("startDate")).isNotNull();
        assertThat(sort.getOrderFor("startDate").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void parseSort_shouldHandleMultipleSortParameters() {
        Sort sort = CustomSortHandlerMethodArgumentResolver.parseSort(new String[]{"nextPaymentDate,asc", "price,desc"});
        assertThat(sort.isSorted()).isTrue();
        assertThat(sort.getOrderFor("nextPaymentDate").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getOrderFor("price").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void parseSort_shouldReturnUnsortedForEmptyOrNull() {
        assertThat(CustomSortHandlerMethodArgumentResolver.parseSort(null).isUnsorted()).isTrue();
        assertThat(CustomSortHandlerMethodArgumentResolver.parseSort(new String[]{}).isUnsorted()).isTrue();
    }
}
