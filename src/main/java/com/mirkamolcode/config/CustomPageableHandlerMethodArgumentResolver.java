package com.mirkamolcode.config;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CustomPageableHandlerMethodArgumentResolver extends PageableHandlerMethodArgumentResolver {

    public CustomPageableHandlerMethodArgumentResolver(CustomSortHandlerMethodArgumentResolver sortResolver) {
        super(sortResolver);
    }

    @Override
    public Pageable resolveArgument(MethodParameter methodParameter,
                                    ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest,
                                    WebDataBinderFactory binderFactory) {
        Pageable pageable = super.resolveArgument(methodParameter, mavContainer, webRequest, binderFactory);
        String[] sortParams = webRequest.getParameterValues("sort");
        if (sortParams != null && sortParams.length > 0) {
            Sort sort = CustomSortHandlerMethodArgumentResolver.parseSort(sortParams);
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        }
        return pageable;
    }
}
