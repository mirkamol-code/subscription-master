package com.mirkamolcode.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class CustomPageableHandlerMethodArgumentResolverTest {

    private final CustomSortHandlerMethodArgumentResolver sortResolver = new CustomSortHandlerMethodArgumentResolver();
    private final CustomPageableHandlerMethodArgumentResolver underTest = new CustomPageableHandlerMethodArgumentResolver(sortResolver);

    void dummyMethod(Pageable pageable) {}

    @Test
    void resolveArgument_whenUnpagedTrue_shouldCapAtMaxUnpagedSize() throws Exception {
        Method method = getClass().getDeclaredMethod("dummyMethod", Pageable.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("unpaged", "true");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        Pageable result = underTest.resolveArgument(parameter, null, webRequest, null);

        assertThat(result.getPageNumber()).isZero();
        assertThat(result.getPageSize()).isEqualTo(1000);
    }

    @Test
    void resolveArgument_whenSizeMinusOne_shouldCapAtMaxUnpagedSize() throws Exception {
        Method method = getClass().getDeclaredMethod("dummyMethod", Pageable.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("size", "-1");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        Pageable result = underTest.resolveArgument(parameter, null, webRequest, null);

        assertThat(result.getPageNumber()).isZero();
        assertThat(result.getPageSize()).isEqualTo(1000);
    }

    @Test
    void resolveArgument_whenUnpagedWithSort_shouldCapAndPreserveSort() throws Exception {
        Method method = getClass().getDeclaredMethod("dummyMethod", Pageable.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("unpaged", "true");
        request.setParameter("sort", "price,desc");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        Pageable result = underTest.resolveArgument(parameter, null, webRequest, null);

        assertThat(result.getPageNumber()).isZero();
        assertThat(result.getPageSize()).isEqualTo(1000);
        assertThat(result.getSort().getOrderFor("price")).isNotNull();
        assertThat(result.getSort().getOrderFor("price").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
